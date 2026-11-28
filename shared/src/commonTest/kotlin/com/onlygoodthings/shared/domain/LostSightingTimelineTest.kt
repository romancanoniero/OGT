package com.onlygoodthings.shared.domain

import com.onlygoodthings.shared.data.local.LocalAnimalListing
import com.onlygoodthings.shared.data.local.LocalSighting
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.assertNull
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

    @Test fun laPerdidaEsElPrimerHitoInclusoConAvistamientosDelMismoDia() {
        val listing = listing(now - 2 * 3_600_000L)
        val rows = listOf(sight("b", now - 30 * 60_000L), sight("a", now - 3_600_000L))
        val stops = lostTimelineStops(listing, rows, now, tz)
        assertEquals(2, stops.size)
        assertTrue(stops.first().isOrigin)
        assertEquals("Pérdida", stops.first().label)
        assertTrue(sightingsAtTimelineStop(rows, stops.first()).isEmpty())
        assertEquals(listOf(GeoPoint(-34.5889, -58.4328)), movementRoute(listing, emptyList()))
        assertEquals(listOf("a", "b"), sightingsAtTimelineStop(rows, stops.last()).map { it.id })
        // Volver al inicio no conserva un avistamiento ya recorrido.
        assertTrue(sightingsAtTimelineStop(rows, stops.first()).isEmpty())
    }

    @Test fun elOrigenSiguePrimeroConUnAvisoPosteriorAUnAvistamientoHistorico() {
        val rows = listOf(sight("c", now - DAY), sight("a", now - 3 * DAY))
        val stops = lostTimelineStops(listing(now), rows, now, tz)
        assertTrue(stops.first().isOrigin)
        assertTrue(sightingsAtTimelineStop(rows, stops.first()).isEmpty())
        assertEquals(listOf("a"), sightingsAtTimelineStop(rows, stops[1]).map { it.id })
        assertEquals(listOf("a", "c"), sightingsAtTimelineStop(rows, stops[2]).map { it.id })
    }

    @Test fun sinAvistamientosSoloSeMuestraElPuntoDePartida() {
        val stops = lostTimelineStops(listing(0), emptyList(), now, tz)
        assertEquals(1, stops.size)
        assertEquals("Pérdida", stops.single().label)
        assertTrue(stops.single().isOrigin)
    }

    @Test fun elPrimerHitoDeUnEncontradoNoSeEtiquetaComoPerdida() {
        val stops = lostTimelineStops(listing(now).copy(kind = "FOUND"), emptyList(), now, tz)
        assertTrue(stops.single().isOrigin)
        assertEquals("Encuentro", stops.single().label)
    }

    @Test fun elRecorridoSoloUneElOrigenConLosReportesVisiblesEnOrden() {
        val origin = listing(now - 3 * DAY)
        val rows = listOf(sight("c", now), sight("a", now - 2 * DAY), sight("b", now - DAY))
        val stops = lostTimelineStops(origin, rows, now, tz)
        val visible = sightingsAtTimelineStop(rows, stops[stops.lastIndex - 1])
        val route = movementRoute(origin, visible)
        assertEquals(listOf("a", "b"), visible.map { it.id })
        assertEquals(visible.size + 1, route.size)
        assertEquals(GeoPoint(origin.latitude!!, origin.longitude!!), route.first())
        assertEquals(GeoPoint(visible.last().latitude, visible.last().longitude), route.last())
    }

    @Test fun lasFechasDeAvistamientosRespetanLaZonaHorariaYLosDatosAusentes() {
        assertEquals("18 sep · 15:00", sightingMomentLabel(sight("a", now), now, tz))
        assertEquals("18 sep 2025 · 15:00", sightingMomentLabel(sight("a", Instant.parse("2025-09-18T18:00:00Z").toEpochMilliseconds()), now, tz))
        assertNull(sightingMomentLabel(sight("a", 0), now, tz))
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

    @Test fun unReporteCargadoTardeSeOrdenaPorCuandoOcurrioSinAlterarLaCarga() {
        val oldObservation=sight("a",now).copy(observedAtEpochMs=now-3*DAY)
        val recentObservation=sight("c",now-3_600_000).copy(observedAtEpochMs=now-DAY)
        val rows=listOf(recentObservation,oldObservation)
        val stops=lostTimelineStops(listing(now-4*DAY),rows,now,tz)
        assertEquals(3,stops.size)
        assertEquals(listOf("a"),sightingsAtTimelineStop(rows,stops[1]).map { it.id })
        val all=sightingsAtTimelineStop(rows,stops.last())
        assertEquals(listOf("a","c"),all.map { it.id })
        assertEquals(now,all.first().createdAtEpochMs)
        assertEquals(now-3*DAY,all.first().observedAtEpochMs)
        assertEquals(GeoPoint(recentObservation.latitude,recentObservation.longitude),movementRoute(listing(now),rows).last())
        assertEquals("15 sep · 15:00",sightingMomentLabel(oldObservation,now,tz))
        assertEquals("18 sep · 15:00",sightingReportMomentLabel(oldObservation,now,tz))
        assertEquals(GeoPoint(recentObservation.latitude,recentObservation.longitude),headingAfter(rows,listOf(oldObservation)))
    }

    @Test fun losHorariosAnterioresUsanLaCargaComoReferenciaSinInventarUnaObservacion() {
        val legacy=sight("old",now-DAY)
        val actual=sight("new",now).copy(observedAtEpochMs=now-2*DAY)
        val ordered=sightingsUntil(listOf(legacy,actual),now)
        assertEquals(listOf("new","old"),ordered.map { it.id })
        assertNull(legacy.observedAtEpochMs)
        assertEquals(now-DAY,legacy.createdAtEpochMs)
    }
}
