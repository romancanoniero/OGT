package com.onlygoodthings.backend.social

import com.onlygoodthings.backend.infra.Database
import com.onlygoodthings.shared.domain.MediaKind
import com.onlygoodthings.shared.domain.PostMediaItem
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import java.util.UUID

@Tag("business-integration")
class PublicationLocationIntegrationTest {
    @Test fun chosenPlaceCoordinatesSurviveTextEditsAndDoNotSurviveAnUnconfirmedPlaceChange() {
        val url=System.getenv("OGT_BUSINESS_TEST_JDBC").orEmpty()
        require(url.matches(Regex("jdbc:postgresql://127\\.0\\.0\\.1:19432/ogt_business_qa_[a-z0-9]+")))
        Database(url,"ogt","ogt_qa_local_only").use { db ->
            val owner=UUID.randomUUID().toString()
            db.withConnection { c -> c.prepareStatement("INSERT INTO users(id,firebase_uid,email,display_name,invite_code) VALUES(?::uuid,?,?,'Persona QA Lugar',?)").use { q ->
                q.setString(1,owner);q.setString(2,"place-$owner");q.setString(3,"$owner@example.test");q.setString(4,owner);q.executeUpdate()
            } }
            val repo=SocialSqlRepository(db)
            val post=repo.publish(owner,"Historia con lugar","Ternura",listOf(PostMediaItem(id=UUID.randomUUID().toString(),kind=MediaKind.IMAGE,url="https://example.test/qa.jpg")),-31.42,-64.18,null,emptyList(),publicationKind="POST",place="Córdoba",hashtags=listOf("Ternura"))
            fun point()=db.withConnection { c -> c.prepareStatement("SELECT ST_AsText(location) FROM social_posts WHERE id=?::uuid").use { q ->q.setString(1,post.id);q.executeQuery().use { r ->r.next();r.getString(1) } } }
            assertEquals("POINT(-64.18 -31.42)",point())
            repo.editPost(post.id,owner,"Historia editada",null)
            assertEquals("POINT(-64.18 -31.42)",point())
            repo.editPost(post.id,owner,"Otro lugar",null,place="Caballito",latitude=-34.62,longitude=-58.44,pointChanged=true)
            assertEquals("POINT(-58.44 -34.62)",point())
            repo.editPost(post.id,owner,"Lugar escrito",null,place="Lugar por confirmar")
            assertNull(point());assertEquals(post.createdAtEpochMs,repo.byId(post.id,owner)!!.createdAtEpochMs)
        }
    }
}
