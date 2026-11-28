package com.onlygoodthings.backend.modules

import com.onlygoodthings.backend.infra.Database
import com.onlygoodthings.backend.social.SocialSqlRepository
import com.onlygoodthings.shared.domain.FeedMode
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import java.util.UUID

@Tag("business-integration")
class PetAlertsIntegrationTest {
    @Test fun foundIsAnOpenAlertWithItsOwnCoordinatesAndNeverAnAdoptionOrResolvedLostPet() {
        val url=System.getenv("OGT_BUSINESS_TEST_JDBC").orEmpty()
        require(url.matches(Regex("jdbc:postgresql://127\\.0\\.0\\.1:19432/ogt_business_qa_[a-z0-9]+")))
        Database(url,"ogt","ogt_qa_local_only").use { db ->
            val owner=UUID.randomUUID().toString();val viewer=UUID.randomUUID().toString()
            db.withConnection { c -> for(id in listOf(owner,viewer)) c.prepareStatement("INSERT INTO users(id,firebase_uid,email,display_name,invite_code) VALUES(?::uuid,?,?,'Persona QA Mascotas',?)").use { q ->
                q.setString(1,id);q.setString(2,"pets-$id");q.setString(3,"$id@example.test");q.setString(4,id);q.executeUpdate()
            } }
            val animals=AnimalSqlRepository(db);val posts=SocialSqlRepository(db)
            val draft=mapOf<String,Any?>("kind" to "FOUND","species" to "DOG","description" to "Busca a su familia",
                "marks" to "Canela, collar azul","lastSeenPlace" to "Castelar, punto aproximado","latitude" to -34.66,"longitude" to -58.66,
                "media" to listOf(mapOf("url" to "https://example.test/dog.jpg")))
            val found=animals.publish(owner,animalWriteFrom(draft,-31.42 to -64.18))
            assertEquals("FOUND",found.kind);assertFalse(found.resolved);assertNull(found.adoptionDetails)
            assertEquals("",found.size);assertEquals("",found.petName)
            assertEquals(-34.66,found.latitude);assertEquals(-58.66,found.longitude)
            val social=posts.byId(found.postId,viewer)!!
            assertEquals("Mascota encontrada",social.topic);assertEquals("FOUND",social.animalListing!!.kind)
            assertTrue(posts.feed(viewer,FeedMode.HOME,200,null,device=com.onlygoodthings.shared.domain.GeoPoint(-34.66,-58.66)).any { it.id==found.postId })
            assertTrue(posts.feed(viewer,FeedMode.HOME,200,null,family=com.onlygoodthings.shared.domain.FeedTopicFamily.PETS,device=com.onlygoodthings.shared.domain.GeoPoint(-34.66,-58.66)).any { it.id==found.postId })
            assertTrue(animals.open().any { it.listingId==found.listingId && !it.resolved })
            posts.clap(found.postId,viewer);posts.addComment(found.postId,viewer,"Aviso de prueba",null)
            val edited=animals.update(owner,found.listingId,animalWriteFrom(draft+mapOf("marks" to "Canela, collar y chapa"),null,previous=found))
            assertEquals(found.postId,edited.postId);assertEquals(found.createdAtEpochMs,edited.createdAtEpochMs)
            assertEquals(1,posts.byId(found.postId,viewer)!!.commentCount)
            assertTrue(posts.byId(found.postId,viewer)!!.viewerHasImpacted)
            assertThrows(IllegalArgumentException::class.java) { animalWriteFrom(draft+mapOf("kind" to "ADOPTION"),null,previous=edited) }
            assertThrows(IllegalArgumentException::class.java) { animalWriteFrom(draft-mapOf("latitude" to -34.66,"longitude" to -58.66).keys,-31.42 to -64.18) }
            val lost=animals.publish(owner,animalWriteFrom(draft+mapOf("kind" to "LOST","petName" to "Luna","size" to "SMALL"),null))
            assertEquals("LOST",lost.kind);assertEquals("HIGH",lost.urgency)
            assertTrue(animals.markResolved(found.listingId));assertFalse(animals.markResolved(found.listingId))
            assertFalse(animals.open().any { it.listingId==found.listingId })
            assertTrue(animals.open().any { it.listingId==lost.listingId })
            assertNotNull(posts.byId(found.postId,viewer))
        }
    }
}
