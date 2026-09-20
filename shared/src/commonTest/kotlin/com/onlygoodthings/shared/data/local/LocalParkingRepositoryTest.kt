package com.onlygoodthings.shared.data.local

import com.onlygoodthings.shared.domain.GeoPoint
import com.onlygoodthings.shared.domain.ParkingStatus
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class LocalParkingRepositoryTest {

    @Test
    fun radarExcluyePropiasYRespetaRadio() = runBlocking {
        val db = OgtLocalDatabase.seeded()
        val mariana = LocalParkingRepository(db, OgtIds.Mariana)
        val here = GeoPoint(-34.5888, -58.4302)
        val near = mariana.nearby(here, 400)
        assertTrue(near.any { it.id == "spot-thames" })
        assertTrue(near.none { it.ownerUserId == OgtIds.Mariana })
        assertTrue(near.none { it.id == OgtIds.SpotCorrientes }, "Corrientes queda fuera de 400 m")
        val fromMicrocentro = mariana.nearby(GeoPoint(-34.6039, -58.3815), 400)
        assertTrue(fromMicrocentro.any { it.id == OgtIds.SpotCorrientes })
    }

    @Test
    fun reclamarYConfirmarCercaSumaPuntosAlCedente() = runBlocking {
        val db = OgtLocalDatabase.seeded()
        val mariana = LocalParkingRepository(db, OgtIds.Mariana)
        val spot = db.parkingSpots.first { it.id == "spot-thames" }
        val ownerPts = db.user(spot.ownerUserId).communityPoints
        val claimed = mariana.claim(spot.id, spot.version, GeoPoint(-34.5888, -58.4302))
        assertEquals(ParkingStatus.CLAIMED, claimed.status)
        assertEquals(OgtIds.Mariana, claimed.claimedByUserId)
        val done = mariana.complete(spot.id, GeoPoint(spot.latitude, spot.longitude))
        assertEquals(ParkingStatus.COMPLETED, done.spot.status)
        assertTrue(done.handoff.verified)
        assertEquals(ownerPts + spot.rewardPoints, db.user(spot.ownerUserId).communityPoints)
        assertTrue(mariana.history().any { it.parkingSpotId == spot.id })
    }

    @Test
    fun noConfirmaSiEstaLejos() = runBlocking {
        val db = OgtLocalDatabase.seeded()
        val mariana = LocalParkingRepository(db, OgtIds.Mariana)
        val spot = db.parkingSpots.first { it.id == "spot-thames" }
        mariana.claim(spot.id, spot.version, GeoPoint(-34.5888, -58.4302))
        assertFailsWith<IllegalArgumentException> {
            mariana.complete(spot.id, GeoPoint(-34.5888, -58.4302))
        }
        return@runBlocking
    }

    @Test
    fun noSeReclamaDosVecesNiLaPropia() = runBlocking {
        val db = OgtLocalDatabase.seeded()
        val mariana = LocalParkingRepository(db, OgtIds.Mariana)
        val lucas = LocalParkingRepository(db, OgtIds.Lucas)
        val spot = db.parkingSpots.first { it.id == OgtIds.SpotCorrientes }
        assertFailsWith<IllegalArgumentException> {
            lucas.claim(spot.id, spot.version, GeoPoint(spot.latitude, spot.longitude))
        }
        mariana.claim(spot.id, spot.version, GeoPoint(-34.6039, -58.3815))
        assertFailsWith<IllegalArgumentException> {
            mariana.claim(spot.id, spot.version, GeoPoint(-34.6039, -58.3815))
        }
        return@runBlocking
    }

    @Test
    fun encontrarOtroLugarDevuelveLaPlazaFCFSYAvisa() = runBlocking {
        val db = OgtLocalDatabase.seeded()
        val mariana = LocalParkingRepository(db, OgtIds.Mariana)
        val spot = db.parkingSpots.first { it.id == "spot-thames" }
        mariana.claim(spot.id, spot.version, GeoPoint(-34.5888, -58.4302))
        val released = mariana.foundOtherPlace(spot.id)
        assertEquals(ParkingStatus.AVAILABLE, released.status)
        assertEquals(null, released.claimedByUserId)
        assertTrue(released.leftoverOpen)
        assertTrue(db.notifications.any { it.id.startsWith("n-found-other-") })
        val again = mariana.nearby(GeoPoint(-34.5888, -58.4302), 400)
        assertTrue(again.any { it.id == spot.id })
    }

    @Test
    fun cancelarReservaLaDevuelveAlRadar() = runBlocking {
        val db = OgtLocalDatabase.seeded()
        val mariana = LocalParkingRepository(db, OgtIds.Mariana)
        val spot = db.parkingSpots.first { it.id == "spot-thames" }
        mariana.claim(spot.id, spot.version, GeoPoint(-34.5888, -58.4302))
        val released = mariana.cancel(spot.id)
        assertEquals(ParkingStatus.AVAILABLE, released.status)
        assertEquals(null, released.claimedByUserId)
    }

    @Test
    fun radarDemoCruzaLosCuatroRadios() = runBlocking {
        val db = OgtLocalDatabase.seeded()
        val origin = GeoPoint(-34.5990, -58.4410)
        db.placeRadarDemoSpots(origin)
        val mariana = LocalParkingRepository(db, OgtIds.Mariana)
        val at400 = mariana.nearby(origin, 400).map { it.id }
        val at500 = mariana.nearby(origin, 500).map { it.id }
        val at1000 = mariana.nearby(origin, 1000).map { it.id }
        val at2000 = mariana.nearby(origin, 2000).map { it.id }
        assertTrue(at400.contains("spot-radar-220"))
        assertTrue(at400.none { it.startsWith("spot-radar-") && it != "spot-radar-220" })
        assertTrue(at500.containsAll(listOf("spot-radar-220", "spot-radar-450")))
        assertTrue(at1000.containsAll(listOf("spot-radar-220", "spot-radar-450", "spot-radar-850")))
        assertTrue(at2000.containsAll(listOf("spot-radar-220", "spot-radar-450", "spot-radar-850", "spot-radar-1600")))
    }
}
