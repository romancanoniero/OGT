package com.onlygoodthings.shared.domain

import com.onlygoodthings.shared.data.local.LocalAnimalListing
import com.onlygoodthings.shared.data.local.LocalSighting
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone

class LostSightingTimelineTest {
    private val tz = TimeZone.of("America/Argentina/Buenos_Aires")
    private val now = Instant.parse("2026-09-18T18:00:00Z").toEpochMilliseconds()

    @Test
    fun paraEnElDiaMarcadoYElRumboSaleDelSiguienteAvistaje() {
        val listing = listing(now - 3 * DAY)
        val rows = listOf(
            sight("a", now - 2 * DAY),
            sight("b", now - DAY),
            sight("c", now - 3_600_000L),
        )
        val stops = lostTimelineStops(listing, rows, now, tz)
        assertEquals(4, stops.size)
        val untilAyer = sightingsUntil(rows, stops[stops.size - 2].dayEndEpochMs)
        assertEquals(listOf("a", "b"), untilAyer.map { it.id })
        val heading = headingAfter(rows, untilAyer)
        assertEquals(-34.5904, heading?.latitude)
    }

    private fun listing(seenAt: Long) = LocalAnimalListing(
        id = "l",
        reporterUserId = "u",
        kind = "LOST",
        species = "DOG",
        size = "LARGE",
        urgency = "HIGH",
        title = "Oliver",
        description = "",
        place = "Palermo",
        alertRadiusM = 2000,
        neighborsAlerted = 1,
        resolved = false,
        latitude = -34.5889,
        longitude = -58.4328,
        lastSeenAtEpochMs = seenAt,
    )

    private fun sight(id: String, at: Long) = LocalSighting(
        id = id,
        listingId = "l",
        postId = "p",
        userId = "v",
        note = "",
        latitude = if (id == "c") -34.5904 else -34.5890,
        longitude = -58.4315,
        status = "SEEN",
        matchId = "m-$id",
        timeLabel = "",
        createdAtEpochMs = at,
    )

    companion object {
        private const val DAY = 86_400_000L
    }
}
