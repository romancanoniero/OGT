package com.onlygoodthings.shared.domain

import com.onlygoodthings.shared.data.local.LocalSighting
import com.onlygoodthings.shared.data.local.OgtLocalDatabase
import kotlin.test.*

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
}
