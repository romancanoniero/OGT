package com.onlygoodthings.backend.social

import com.onlygoodthings.backend.community.UserBlocks
import com.onlygoodthings.backend.infra.Database
import com.onlygoodthings.backend.modules.DiscoverySettingsRepository
import com.onlygoodthings.shared.domain.FeedMode
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import java.util.UUID

@Tag("business-integration")
class PostDiffusionsIntegrationTest {
    @Test fun diffusionsReachFollowersWithoutCopiesAndRetainModerationBlocksAndProximity() {
        val url=System.getenv("OGT_BUSINESS_TEST_JDBC").orEmpty()
        require(url.matches(Regex("jdbc:postgresql://127\\.0\\.0\\.1:19432/ogt_business_qa_[a-z0-9]+")))
        Database(url,"ogt","ogt_qa_local_only").use { db ->
            fun id()=UUID.randomUUID().toString()
            val author=id();val friend=id();val viewer=id();val stranger=id();val original=id()
            fun sql(value:String)=db.withConnection { it.createStatement().use { q ->q.execute(value) } }
            for(user in listOf(author,friend,viewer,stranger))sql("INSERT INTO users(id,firebase_uid,email,display_name,invite_code,home_location) VALUES('$user','diff-$user','$user@example.test','Persona QA','$user',ST_SetSRID(ST_MakePoint(-58.4424893,-34.6200773),4326))")
            sql("INSERT INTO follows(follower_id,followed_id) VALUES('$viewer','$friend')")
            sql("INSERT INTO social_posts(id,author_kind,author_user_id,body,topic,created_at) VALUES('$original','USER','$author','Original sin copia','Comunidad',now()-interval '400 days')")
            sql("INSERT INTO social_posts(author_kind,author_user_id,body,topic,created_at) SELECT 'USER','$stranger','Reciente QA','Comunidad',now()-interval '1 hour' FROM generate_series(1,205)")
            val diffusions=PostDiffusions(db);val posts=SocialSqlRepository(db)
            diffusions.set(friend,original,true)
            val first=posts.byId(original,viewer)!!.diffusedBy.single()
            diffusions.set(friend,original,true)
            assertEquals(first.createdAtEpochMs,posts.byId(original,viewer)!!.diffusedBy.single().createdAtEpochMs)
            val following=posts.feed(viewer,FeedMode.FOLLOWING,200,null)
            assertEquals(original,following.first().id);assertEquals(author,following.first().authorId)
            assertFalse(following.first().viewerHasDiffused)
            assertTrue(posts.byId(original,friend)!!.viewerHasDiffused)
            assertTrue(posts.feed(stranger,FeedMode.FOLLOWING,200,null).none { it.id==original })
            diffusions.set(friend,original,false)
            assertTrue(posts.feed(viewer,FeedMode.FOLLOWING,200,null).none { it.id==original })
            diffusions.set(friend,original,true)
            sql("UPDATE social_posts SET moderation_status='HIDDEN' WHERE id='$original'")
            assertTrue(posts.feed(viewer,FeedMode.FOLLOWING,200,null).none { it.id==original })
            assertThrows(IllegalArgumentException::class.java){diffusions.set(friend,original,true)}
            sql("UPDATE social_posts SET moderation_status='VISIBLE' WHERE id='$original'")
            UserBlocks(db).set(viewer,friend,true)
            assertTrue(posts.byId(original,viewer)!!.diffusedBy.isEmpty())
            assertTrue(posts.feed(viewer,FeedMode.FOLLOWING,200,null).none { it.id==original })
            UserBlocks(db).set(friend,author,true)
            assertThrows(IllegalArgumentException::class.java){diffusions.set(friend,original,true)}
            // Confirmed manual place works with GPS absent; settings are global and versioned.
            val settings=DiscoverySettingsRepository(db)
            sql("UPDATE users SET home_location=NULL WHERE id='$viewer'")
            assertNull(settings.scope(viewer).latitude)
            val scope=settings.place(viewer,mapOf("place" to "Caballito, CABA","latitude" to -34.62,"longitude" to -58.44))
            assertEquals(-34.62,scope.latitude);assertEquals("Caballito, CABA",scope.place)
            val before=settings.get();val updated=settings.save(author,20,before.version)
            assertEquals(before.version+1,updated.version)
            assertThrows(IllegalArgumentException::class.java){settings.save(author,30,before.version)}
            assertThrows(IllegalArgumentException::class.java){settings.save(author,0,updated.version)}
        }
    }
}
