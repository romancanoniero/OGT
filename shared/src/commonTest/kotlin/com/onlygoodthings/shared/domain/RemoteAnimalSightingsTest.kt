package com.onlygoodthings.shared.domain

import com.onlygoodthings.shared.data.local.LocalSighting
import com.onlygoodthings.shared.data.local.OgtLocalDatabase
import kotlin.test.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.*

class RemoteAnimalSightingsTest {
    private fun local(id: String, listing: String) = LocalSighting(id, listing, "post", "neighbor", "Historia local", -34.6, -58.4, "SEEN", "local-chat", "Antes", 1000)
    private fun remote(id: String, listing: String, match: String? = null) = AnimalSightingDto(id, listing, "post", "neighbor", "Vecino", null, "Avistaje compartido", -34.601, -58.401, match, 2000)

    @Test fun serverRefreshPreservesHistoricalDeviceDataAndOtherListings() {
        val db = OgtLocalDatabase()
        val history = local("local-sighting", "listing-a")
        val unrelated = local("remote:other", "listing-b")
        db.sightings += listOf(history, unrelated, local("remote:removed", "listing-a"))
        db.applyRemoteSightings("listing-a", listOf(remote("public-id", "listing-a", "private-chat")))
        assertTrue(history in db.sightings)
        assertTrue(unrelated in db.sightings)
        assertFalse(db.sightings.any { it.id == "remote:removed" })
        assertEquals("private-chat", db.sightings.single { it.id == "remote:public-id" }.matchId)
        assertEquals(-34.601, db.sightings.single { it.id == "remote:public-id" }.latitude)
        db.applyRemoteSightings("listing-a", emptyList())
        assertEquals(setOf(history, unrelated), db.sightings.toSet())
    }

    @Test fun repeatedRefreshDeduplicatesPinsAndDoesNotInventAccessToAConversation() {
        val db = OgtLocalDatabase()
        val row = remote("public-id", "listing-a")
        repeat(3) { db.applyRemoteSightings("listing-a", listOf(row)) }
        assertEquals(1, db.sightings.size)
        assertEquals("remote:public-id", db.sightings.single().id)
        assertEquals("", db.sightings.single().matchId)
    }
    @Test fun observationTimeSurvivesRefreshAndCacheWhileCreationTimeStaysUnchanged() {
        val db=OgtLocalDatabase();val json=Json { encodeDefaults=true }
        val earlier=remote("a","listing-a").copy(createdAtEpochMs=4000,observedAtEpochMs=1000)
        val later=remote("b","listing-a").copy(createdAtEpochMs=3000,observedAtEpochMs=2000)
        repeat(2){db.applyRemoteSightings("listing-a",listOf(earlier,later))}
        assertEquals(listOf("remote:b","remote:a"),db.sightingsOf("listing-a").map { it.id })
        val saved=db.sightingsOf("listing-a").last()
        assertEquals(4000L,saved.createdAtEpochMs);assertEquals(1000L,saved.observedAtEpochMs)
        assertEquals(saved,json.decodeFromString<LocalSighting>(json.encodeToString(saved)))
        val legacy=JsonObject(json.parseToJsonElement(json.encodeToString(saved)).jsonObject-"observedAtEpochMs")
        val restored=json.decodeFromString<LocalSighting>(legacy.toString())
        assertNull(restored.observedAtEpochMs);assertEquals(4000L,restored.createdAtEpochMs)
    }
}
