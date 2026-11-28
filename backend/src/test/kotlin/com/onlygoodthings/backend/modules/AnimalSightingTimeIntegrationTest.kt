package com.onlygoodthings.backend.modules

import com.onlygoodthings.backend.community.AnimalContacts
import com.onlygoodthings.backend.infra.Database
import com.onlygoodthings.backend.qa.*
import com.onlygoodthings.shared.domain.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path
import java.util.UUID

@Tag("business-integration")
class AnimalSightingTimeIntegrationTest {
    private fun database():Database {
        val url=System.getenv("OGT_BUSINESS_TEST_JDBC").orEmpty()
        require(url.matches(Regex("jdbc:postgresql://127\\.0\\.0\\.1:19432/ogt_business_qa_[a-z0-9]+")))
        return Database(url,"ogt","ogt_qa_local_only")
    }
    private fun user(db:Database):String = UUID.randomUUID().toString().also { id ->
        db.withConnection { c ->c.prepareStatement("INSERT INTO users(id,firebase_uid,email,display_name,invite_code) VALUES(?::uuid,?,?,'Persona QA Horarios',?)").use { q ->
            q.setString(1,id);q.setString(2,"qa-time-$id");q.setString(3,"$id@example.test");q.setString(4,id);q.executeUpdate()
        } }
    }
    private fun lost(db:Database,owner:String,tag:String="QA"):AnimalListingDto = AnimalSqlRepository(db).publish(owner,
        animalWriteFrom(mapOf("kind" to "LOST","species" to "CAT","petName" to "Gato $tag","description" to "Aviso ficticio para pruebas",
            "marks" to "Collar QA","lastSeenPlace" to "Caballito QA","latitude" to -34.62,"longitude" to -58.44,
            "attributionUrl" to "https://www.instagram.com/patitas_perdidas9/p/$tag/","media" to listOf(mapOf("url" to "https://example.test/cat.jpg"))),null))

    @Test fun observationTimeSortsLateReportsWithoutChangingCreationChatOrRetrySemantics() {
        database().use { db ->
            val owner=user(db);val a=user(db);val b=user(db);val legacy=user(db);val outsider=user(db)
            val listing=lost(db,owner);val contacts=AnimalContacts(db);val now=System.currentTimeMillis()
            fun request(at:Long?)=AnimalContactRequest(listing.listingId,"SIGHTING",note="Observación QA",latitude=-34.621,longitude=-58.441,observedAtEpochMs=at)
            val bThread=contacts.open(b,request(now-86_400_000));val aThread=contacts.open(a,request(now-3*86_400_000L))
            contacts.open(legacy,request(null))
            val rows=contacts.sightings(owner,listing.listingId)
            assertEquals(listOf(legacy,b,a),rows.map { it.userId })
            val snapshot=rows.single { it.userId==a }
            assertEquals(now-3*86_400_000L,snapshot.observedAtEpochMs)
            assertTrue(snapshot.createdAtEpochMs>snapshot.observedAtEpochMs!!)
            assertEquals(aThread.matchId,snapshot.matchId)
            val retried=contacts.open(a,request(now-2*86_400_000L))
            assertEquals(aThread.matchId,retried.matchId)
            assertEquals(snapshot,contacts.sightings(owner,listing.listingId).single { it.userId==a })
            assertTrue(contacts.sightings(outsider,listing.listingId).all { it.matchId==null })
            assertEquals(bThread.matchId,contacts.sightings(b,listing.listingId).single { it.userId==b }.matchId)
            db.withConnection { c ->c.prepareStatement("SELECT count(*) FROM timebank_messages WHERE match_id=?::uuid").use { q ->
                q.setString(1,aThread.matchId);q.executeQuery().use { r ->r.next();assertEquals(1,r.getInt(1)) }
            } }
            assertThrows(IllegalArgumentException::class.java){contacts.open(outsider,request(now+600_000))}
            assertEquals(3,contacts.sightings(owner,listing.listingId).size)
        }
    }

    @Test fun additiveMigrationPreservesLegacyPayloadAndCreationAndCanBeReapplied() {
        database().use { db ->db.withConnection { c ->
            c.autoCommit=false
            try {
                val schema="qa_observation_"+UUID.randomUUID().toString().replace("-","")
                c.createStatement().use { q ->
                    q.execute("CREATE SCHEMA $schema");q.execute("SET LOCAL search_path TO $schema,public")
                    q.execute("CREATE TABLE animal_contacts(listing_id uuid,kind text,request_payload jsonb,created_at timestamptz NOT NULL)")
                    q.execute("INSERT INTO animal_contacts VALUES(gen_random_uuid(),'SIGHTING','{\"note\":\"Historial QA\"}','2026-10-01T12:34:56Z')")
                    val sql=listOf(Path.of("database/migrations/50_sighting_observed_at.sql"),Path.of("../database/migrations/50_sighting_observed_at.sql")).first { Files.exists(it) }
                    repeat(2){q.execute(Files.readString(sql))}
                    q.executeQuery("SELECT observed_at,created_at,request_payload->>'note' AS note FROM animal_contacts").use { r ->
                        assertTrue(r.next());assertNull(r.getTimestamp(1));assertEquals("2026-10-01T12:34:56Z",r.getTimestamp(2).toInstant().toString());assertEquals("Historial QA",r.getString(3))
                    }
                }
            }finally{c.rollback();c.autoCommit=true}
        } }
    }

    @Test fun disclosedFixtureBatchUsesTheContactModuleAndIsIdempotent() {
        database().use { db ->
            val owner=user(db);val author=user(db);val targets=listOf(lost(db,author,"QAOne"),lost(db,author,"QATwo"))
            db.withConnection { c ->c.prepareStatement("UPDATE users SET publishing_blocked=true,metadata=jsonb_build_object('testContact',jsonb_build_object('scope','SHARE_TEST','ownerUserId',?)) WHERE id=?::uuid").use { q ->q.setString(1,owner);q.setString(2,author);q.executeUpdate() } }
            val suffix=owner.filter { it.isLetter() }.take(8)
            val profiles=listOf("alfa","beta","gama","delta").map { key ->PetFixtureProfile(key+suffix,"Persona $key QA","prueba_${key}_$suffix","$key-$owner@example.test",
                "https://onlygoodthings.lat/media/u/00000000-0000-0000-0000-000000000001.jpg","https://www.pexels.com/photo/qa-1/","Autor QA","Caballito QA",-34.62,-58.44) }
            val now=System.currentTimeMillis()
            val observations=targets.flatMap { target ->profiles.mapIndexed { index,p ->PetFixtureObservation(p.key,target.listingId,"PRUEBA FICTICIA de avistamiento",-34.6201,-58.4401,now-(index+1)*86_400_000L) } }
            val batch=PetFixtureBatch("pet-sightings-20261128",owner,author,profiles,observations)
            seedPetSightings(db,batch,false);seedPetSightings(db,batch,true)
            profiles.forEach { profile ->
                db.withConnection { c ->c.prepareStatement("SELECT display_name,nickname,email::text,photo_url,invite_code,ST_Y(home_location),ST_X(home_location),metadata->'settings'->>'barrio' FROM users WHERE id=?::uuid").use { q ->
                    q.setString(1,petFixtureId(batch.seedId,profile.key));q.executeQuery().use { r ->
                        assertTrue(r.next());assertEquals(profile.name,r.getString(1));assertEquals(profile.nickname,r.getString(2));assertEquals(profile.email,r.getString(3))
                        assertEquals(profile.photoUrl,r.getString(4));assertFalse(r.getString(5).isBlank());assertEquals(profile.latitude,r.getDouble(6));assertEquals(profile.longitude,r.getDouble(7));assertEquals(profile.place,r.getString(8))
                    }
                } }
            }
            val contacts=AnimalContacts(db);val snapshots=targets.associate { it.listingId to contacts.sightings(author,it.listingId) }
            seedPetSightings(db,batch,true)
            targets.forEach { target ->assertEquals(4,snapshots.getValue(target.listingId).size);assertEquals(snapshots.getValue(target.listingId),contacts.sightings(author,target.listingId)) }
            val changed=batch.copy(observations=observations.map { it.copy(note="PRUEBA FICTICIA distinta") })
            assertThrows(IllegalArgumentException::class.java){seedPetSightings(db,changed,true)}
        }
    }
}
