package com.onlygoodthings.backend.social

import com.onlygoodthings.backend.infra.Database
import com.onlygoodthings.backend.stories.StoryMediaStore
import com.onlygoodthings.backend.stories.StorySqlRepository
import com.onlygoodthings.shared.domain.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import java.util.UUID

// Reuses the existing isolated database harness; never connects to a production host/database.
@Tag("business-integration")
class ContentAttributionIntegrationTest {
    @TempDir lateinit var mediaRoot:Path
    private fun id()=UUID.randomUUID().toString()

    @Test fun sourcePersistsAcrossReadersEditsRetriesAndModerationStates() {
        val url=System.getenv("OGT_BUSINESS_TEST_JDBC").orEmpty()
        require(url.matches(Regex("jdbc:postgresql://127\\.0\\.0\\.1:19432/ogt_business_qa_[a-z0-9]+")))
        Database(url,"ogt","ogt_qa_local_only").use { db ->
            val owner=id();val other=id();val credit="https://example.org/original?autor=Ana"
            db.withConnection { c ->
                for(user in listOf(owner,other))c.prepareStatement("INSERT INTO users(id,firebase_uid,email,display_name,invite_code) VALUES(?::uuid,?,?,'Autor QA',?)").use { q ->
                    q.setString(1,user);q.setString(2,"attribution-$user");q.setString(3,"$user@example.test");q.setString(4,user);q.executeUpdate()
                }
            }
            val posts=SocialSqlRepository(db)
            val post=posts.publish(owner,"Una ayuda entre vecinos","Ternura",listOf(PostMediaItem(id=id(),kind=MediaKind.IMAGE,url="https://example.org/photo.jpg")),null,null,null,emptyList(),attributionUrl=credit)
            assertEquals(credit,post.attributionUrl);assertNull(post.sourceUrl)
            assertEquals(credit,posts.postsByAuthor(other,owner,20,null).single { it.id==post.id }.attributionUrl)
            assertEquals(credit,posts.editPost(post.id,owner,"Texto corregido",null).attributionUrl)
            assertThrows(IllegalArgumentException::class.java) { posts.editPost(post.id,other,"Texto ajeno",null,"") }
            assertThrows(IllegalArgumentException::class.java) { posts.editPost(post.id,owner,"Texto",null,"javascript:alert(1)") }
            assertNull(posts.editPost(post.id,owner,"Sin fuente externa",null,"").attributionUrl)
            assertEquals(credit,posts.editPost(post.id,owner,"Con fuente externa",null,credit).attributionUrl)

            val stories=StorySqlRepository(db,StoryMediaStore(mediaRoot,"https://example.test"))
            val bytes=javaClass.getResourceAsStream("/images/transparent.webp")!!.readBytes()
            val request=id()
            fun publish(source:String?=credit)=stories.publish(owner,request,"Historia de prueba",StoryAudience.COMMUNITY,bytes,false,null,attributionUrl=source)
            val story=publish();assertEquals(credit,story.attributionUrl);assertEquals(story.id,publish().id)
            assertEquals(credit,stories.list(other).stories.single { it.id==story.id }.attributionUrl)
            assertThrows(IllegalArgumentException::class.java) { publish("https://example.org/otra") }
            assertEquals(credit,stories.list(owner,admin=true,query=request).stories.single().attributionUrl)
            stories.remove(story.id,owner)
            assertTrue(stories.list(other).stories.none { it.id==story.id })
            assertEquals(credit,stories.list(owner,admin=true,query=request).stories.single().attributionUrl)
            assertThrows(IllegalArgumentException::class.java) { publish() }
            val legacy=stories.publish(owner,id(),"Sin fuente",StoryAudience.FOLLOWERS,bytes,false,null)
            assertNull(legacy.attributionUrl)
            assertTrue(stories.list(other).stories.none { it.id==legacy.id })
        }
    }
}
