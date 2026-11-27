package com.onlygoodthings.backend.social

import com.onlygoodthings.backend.infra.Database
import com.onlygoodthings.shared.domain.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import java.util.UUID

@Tag("business-integration")
class PostLifetimeIntegrationTest {
    private fun id()=UUID.randomUUID().toString()
    @Test fun archiveRenewalAndOneNoticePerPeriodPreserveThePostAndOtherOwners() {
        val url=System.getenv("OGT_BUSINESS_TEST_JDBC").orEmpty()
        require(url.matches(Regex("jdbc:postgresql://127\\.0\\.0\\.1:19432/ogt_business_qa_[a-z0-9]+")))
        Database(url,"ogt","ogt_qa_local_only").use { db ->
            val owner=id();val other=id()
            db.withConnection { c ->
                for(user in listOf(owner,other))c.prepareStatement("INSERT INTO users(id,firebase_uid,email,display_name,invite_code) VALUES(?::uuid,?,?,'Autor QA',?)").use { q ->
                    q.setString(1,user);q.setString(2,"lifetime-$user");q.setString(3,"$user@example.test");q.setString(4,user);q.executeUpdate()
                }
            }
            val repo=SocialSqlRepository(db)
            val post=repo.publish(owner,"Contenido que se conserva","Ternura",listOf(PostMediaItem(id(),MediaKind.IMAGE,"https://example.test/photo.jpg")),null,null,null,emptyList())
            assertNotNull(post.feedExpiresAtEpochMs)
            assertEquals(90.0,(post.feedExpiresAtEpochMs!!-System.currentTimeMillis())/86400000.0,.01)
            assertNull(repo.postLifetime(other,post.id,true))
            fun expire(interval:String) = db.withConnection { c ->c.prepareStatement("UPDATE social_posts SET feed_expires_at=now()+?::interval WHERE id=?::uuid").use { q ->q.setString(1,interval);q.setString(2,post.id);q.executeUpdate() } }
            expire("-1 day")
            assertTrue(repo.postLifetime(owner,post.id)!!.archived)
            assertEquals(post.body,repo.byId(post.id,owner)!!.body)
            assertTrue(repo.postsByAuthor(other,owner,20,null).any { it.id==post.id })
            assertTrue(repo.feed(owner,FeedMode.HOME,200,null).none { it.id==post.id })
            assertTrue(repo.feed(owner,FeedMode.FOLLOWING,200,null).none { it.id==post.id })
            assertTrue(repo.visiblePostIds(listOf(post.id),owner).contains(post.id),"Archive must not look like deletion to caches")
            val renewed=repo.postLifetime(owner,post.id,true)!!
            assertFalse(renewed.archived)
            assertEquals(post.body,repo.byId(post.id,owner)!!.body)
            assertEquals(post.createdAtEpochMs,repo.byId(post.id,owner)!!.createdAtEpochMs)
            expire("20 hours")
            val attempts=mutableListOf<String>()
            val worker=PostLifetimeReminders(db) { user,item -> if(user==owner)attempts.add(item);0 }
            worker.process();worker.process();PostLifetimeReminders(db) { user,item -> if(user==owner)attempts.add(item);0 }.process()
            assertEquals(listOf(post.id),attempts)
            repo.postLifetime(owner,post.id,true);worker.process();assertEquals(1,attempts.size)
            expire("21 hours");worker.process();assertEquals(2,attempts.size)
            db.withConnection { c -> c.prepareStatement("SELECT count(*) FROM post_lifetime_notices WHERE post_id=?::uuid AND recipient_user_id=?::uuid").use { q ->q.setString(1,post.id);q.setString(2,owner);q.executeQuery().use { r->r.next();assertEquals(2,r.getInt(1)) } } }
            repo.deletePost(post.id,owner);expire("22 hours");worker.process();assertEquals(2,attempts.size)
        }
    }
}
