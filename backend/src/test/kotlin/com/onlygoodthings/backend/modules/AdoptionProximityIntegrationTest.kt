package com.onlygoodthings.backend.modules

import com.onlygoodthings.backend.infra.Database
import com.onlygoodthings.backend.social.SocialSqlRepository
import com.onlygoodthings.shared.domain.FeedMode
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import java.util.UUID

@Tag("business-integration")
class AdoptionProximityIntegrationTest {
    @Test fun radiusIsAppliedBeforeInventoryLimitAndDoesNotRestrictFollowingOrDetails() {
        val url=System.getenv("OGT_BUSINESS_TEST_JDBC").orEmpty()
        require(url.matches(Regex("jdbc:postgresql://127\\.0\\.0\\.1:19432/ogt_business_qa_[a-z0-9]+")))
        Database(url,"ogt","ogt_qa_local_only").use { db ->
            val owner=UUID.randomUUID().toString();val viewer=UUID.randomUUID().toString()
            db.withConnection { c ->
                for(id in listOf(owner,viewer)) c.prepareStatement("INSERT INTO users(id,firebase_uid,email,display_name,invite_code,home_location) VALUES(?::uuid,?,?,'Persona QA Cercanía',?,ST_SetSRID(ST_MakePoint(-58.4424893,-34.6200773),4326))").use { q ->
                    q.setString(1,id);q.setString(2,"geo-$id");q.setString(3,"$id@example.test");q.setString(4,id);q.executeUpdate()
                }
            }
            val animals=AnimalSqlRepository(db);val posts=SocialSqlRepository(db)
            val draft=mapOf<String,Any?>("kind" to "ADOPTION","species" to "CAT","petName" to "Gato QA","description" to "Busca hogar","place" to "Zona QA")
            fun publish(lat:Double?,lng:Double?,author:String=owner)=animals.publish(author,animalWriteFrom(draft+mapOf("latitude" to lat,"longitude" to lng),null))
            val near=publish(-34.60,-58.45);val far=publish(-31.42,-64.18);val expanded=publish(-34.92,-57.95)
            val unknown=publish(null,null);val own=publish(null,null,viewer)
            assertNull(unknown.latitude);assertNull(unknown.longitude)
            db.withConnection { c -> c.createStatement().use { q ->
                q.execute("""WITH added AS (
                    INSERT INTO animal_listings(reporter_user_id,kind,species,size,urgency,title,description,location,alert_radius_m)
                    SELECT '$owner'::uuid,'ADOPTION','CAT','SMALL','LOW','Lejana QA','Busca hogar',ST_SetSRID(ST_MakePoint(-64.18,-31.42),4326),0 FROM generate_series(1,205) RETURNING id,location
                ) INSERT INTO social_posts(author_kind,author_user_id,body,topic,listing_id,location,created_at)
                  SELECT 'USER','$owner'::uuid,'Lejana QA','Adopción',id,location,now()+interval '1 day' FROM added""")
                q.execute("INSERT INTO follows(follower_id,followed_id) VALUES('$viewer'::uuid,'$owner'::uuid)")
            } }
            val local=posts.feed(viewer,FeedMode.HOME,200,null)
            assertTrue(local.any { it.id==near.postId });assertTrue(local.any { it.id==own.postId })
            assertFalse(local.any { it.id in setOf(far.postId,expanded.postId,unknown.postId) })
            assertFalse(local.any { it.animalListing?.latitude == -31.42 })
            assertTrue(posts.feed(viewer,FeedMode.HOME,200,null,adoptionRadiusKm=100).any { it.id==expanded.postId })
            assertTrue(posts.feed(viewer,FeedMode.HOME,200,null,adoptionRadiusKm=0).any { it.animalListing?.latitude == -31.42 })
            assertTrue(posts.feed(viewer,FeedMode.FOLLOWING,200,null).any { it.animalListing?.latitude == -31.42 })
            assertNotNull(posts.byId(far.postId,viewer));assertNotNull(posts.byId(unknown.postId,viewer))
            db.withConnection { c -> c.createStatement().use { it.execute("UPDATE users SET home_location=NULL WHERE id='$viewer'::uuid") } }
            assertTrue(posts.feed(viewer,FeedMode.HOME,200,null).filter { it.animalListing!=null }.all { it.authorId==viewer })
        }
    }
}
