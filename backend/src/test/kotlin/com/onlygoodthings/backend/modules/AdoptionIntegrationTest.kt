package com.onlygoodthings.backend.modules

import com.onlygoodthings.backend.infra.Database
import com.onlygoodthings.backend.social.SocialSqlRepository
import com.onlygoodthings.shared.domain.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import java.util.UUID

@Tag("business-integration")
class AdoptionIntegrationTest {
    @Test fun historicalDataMediaReactionsAndCreditsSurviveUpgradeAndLegacyClients() {
        val url=System.getenv("OGT_BUSINESS_TEST_JDBC").orEmpty()
        require(url.matches(Regex("jdbc:postgresql://127\\.0\\.0\\.1:19432/ogt_business_qa_[a-z0-9]+")))
        Database(url,"ogt","ogt_qa_local_only").use { db ->
            val owner=UUID.randomUUID().toString(); val other=UUID.randomUUID().toString()
            db.withConnection { c -> for(id in listOf(owner,other)) c.prepareStatement("INSERT INTO users(id,firebase_uid,email,display_name,invite_code) VALUES(?::uuid,?,?,'Persona QA Adopción',?)").use { q ->
                q.setString(1,id);q.setString(2,"adoption-$id");q.setString(3,"$id@example.test");q.setString(4,id);q.executeUpdate()
            } }
            val animals=AnimalSqlRepository(db);val posts=SocialSqlRepository(db);val tags=AdoptionTags(db)
            val oldPayload=mapOf<String,Any?>("kind" to "ADOPTION","species" to "CAT","size" to "SMALL","petName" to "Luna QA","ageLabel" to "2 años",
                "temperament" to "Tranquila","vaccinated" to true,"description" to "Historia original","homeNeeds" to "Sin perros","place" to "Barrio original",
                "attributionUrl" to "https://example.test/foto-original","media" to listOf(mapOf("url" to "https://example.test/animal.jpg")))
            val old=animals.publish(owner,animalWriteFrom(oldPayload,null))
            posts.clap(old.postId,other);val comment=posts.addComment(old.postId,other,"Comentario original",null)
            db.withConnection { c -> c.prepareStatement("UPDATE post_media SET original_url='https://example.test/original.jpg',source_url='https://example.test/origen',frame_x=0.2,frame_y=0.6,frame_zoom=1.7 WHERE post_id=?::uuid").use { q -> q.setString(1,old.postId);q.executeUpdate() } }
            fun mediaSnapshot()=db.withConnection { c -> c.prepareStatement("SELECT jsonb_agg(to_jsonb(m) ORDER BY id)::text FROM post_media m WHERE post_id=?::uuid").use { q -> q.setString(1,old.postId);q.executeQuery().use { rs -> rs.next();rs.getString(1) } } }
            val mediaBefore=mediaSnapshot();val snapshot=animals.get(old.listingId)!!
            val custom=tags.save(owner,mapOf("label" to "HogarQA${owner.take(8)}","group" to "OTHER"))
            val details=mapOf("tags" to listOf("species-cat","sex-female","sex-male","vaccination-some",custom.id).map { mapOf("id" to it) },"availableCount" to 3,"adoptionMode" to "SEPARATE")
            val upgradedPayload=oldPayload+mapOf("title" to "Tres HERMANITOS buscan hogar","description" to "Historia original\n\nCarácter: Tranquila","adoptionDetails" to details,
                "media" to snapshot.media.map { mapOf("id" to it.id,"kind" to it.kind.name,"url" to it.url,"posterUrl" to it.posterUrl,"altText" to it.altText) })
            val upgraded=animals.update(owner,old.listingId,animalWriteFrom(upgradedPayload,null,tags.list(true),snapshot))
            assertEquals(old.createdAtEpochMs,upgraded.createdAtEpochMs);assertEquals(old.postId,upgraded.postId)
            assertEquals(old.petName,upgraded.petName);assertEquals(old.temperament,upgraded.temperament);assertEquals(old.homeNeeds,upgraded.homeNeeds)
            assertEquals("Tres HERMANITOS buscan hogar",upgraded.title);assertEquals(2,upgraded.adoptionDetails!!.tags.count { it.group=="SEX" })
            assertFalse(upgraded.vaccinated);assertEquals(mediaBefore,mediaSnapshot())
            val social=posts.byId(old.postId,other)!!
            assertEquals(1,social.impactCount);assertEquals(1,social.commentCount);assertTrue(social.viewerHasImpacted)
            assertEquals(comment.id,posts.comments(old.postId).single().id)
            assertEquals(upgraded.adoptionDetails,social.animalListing!!.adoptionDetails)
            assertEquals(old.attributionUrl,social.attributionUrl);assertTrue(social.hashtags.any { it.key=="algunosvacunados" })
            assertEquals("gatos",social.hashtags.first { it.key=="gatos" }.categoryId)
            val inactive=tags.save(owner,mapOf("id" to custom.id,"version" to custom.version,"label" to custom.label,"active" to false))
            assertFalse(inactive.active)
            assertThrows(IllegalArgumentException::class.java) { animalWriteFrom(upgradedPayload,null,tags.list(true)) }
            val kept=animals.update(owner,old.listingId,animalWriteFrom(upgradedPayload+mapOf("attributionUrl" to "","adoptionDetails" to (details+("availableCount" to 2))),null,tags.list(true),upgraded))
            assertNull(kept.attributionUrl);assertEquals(2,kept.adoptionDetails!!.availableCount);assertEquals(mediaBefore,mediaSnapshot())
            assertThrows(IllegalStateException::class.java) { animals.update(other,old.listingId,animalWriteFrom(upgradedPayload,null,tags.list(true),kept)) }
            val legacy=animals.update(owner,old.listingId,animalWriteFrom(oldPayload.filterKeys { it!="attributionUrl" },null,tags.list(true),kept))
            assertEquals(kept.title,legacy.title);assertEquals(kept.adoptionDetails,legacy.adoptionDetails);assertNull(legacy.attributionUrl)
            assertEquals(old.createdAtEpochMs,legacy.createdAtEpochMs);assertEquals(mediaBefore,mediaSnapshot())
            // Extending a carousel must not silently drop files beyond the historical ten-item limit.
            val twenty = (0 until 20).map { index -> mapOf("url" to "https://example.test/$index.jpg", "kind" to if(index%2==0)"IMAGE" else "VIDEO", "posterUrl" to "https://example.test/poster-$index.jpg") }
            val twentyWrite = animalWriteFrom(oldPayload + ("media" to twenty), null)
            val complete = animals.publish(owner, twentyWrite)
            assertEquals(twenty.map { it["url"] }, complete.media.map { it.url })
            assertEquals(20, animals.get(complete.listingId)!!.media.size)
            assertEquals(complete.media.map { it.kind }, posts.byId(complete.postId,other)!!.media.map { it.kind })
            assertThrows(IllegalArgumentException::class.java) { animals.update(owner,complete.listingId,twentyWrite.copy(media=twentyWrite.media+twentyWrite.media.first())) }
            assertEquals(complete.media, animals.get(complete.listingId)!!.media)
            val regular = posts.publish(owner,"Veinte archivos QA","Comunidad",twentyWrite.media,null,null,null,emptyList(),publicationKind="POST",hashtags=listOf("Gatos"))
            assertEquals(twentyWrite.media.map { it.url }, regular.media.map { it.url })
            assertThrows(IllegalArgumentException::class.java) { posts.publish(owner,"Demasiados archivos QA","Comunidad",twentyWrite.media+twentyWrite.media.first(),null,null,null,emptyList(),publicationKind="POST",hashtags=listOf("Gatos")) }

            assertEquals(1,posts.byId(old.postId,other)!!.impactCount);assertEquals(comment.id,posts.comments(old.postId).single().id)
        }
    }
}
