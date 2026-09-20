package com.onlygoodthings.shared.data.local

import com.onlygoodthings.shared.domain.GeoPoint
import com.onlygoodthings.shared.domain.ParkingRules
import com.onlygoodthings.shared.domain.ParkingStatus
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking

class LocalParkingMatchingTest {

    @Test
    fun ventanaAnotaInteresYNoAdjudica() = runBlocking {
        val db = OgtLocalDatabase.seeded()
        var now = 1_000_000L
        val owner = LocalParkingRepository(db, OgtIds.Mariana, Random(1), { now })
        val seeker = LocalParkingRepository(db, OgtIds.Lucas, Random(1), { now })
        val car = GeoPoint(-34.5886, -58.4294)
        val walking = GeoPoint(-34.5890, -58.4300)
        val published = owner.publishVacancy(car, 12, "Salgo", "Gol gris", walking)
        assertEquals(ParkingStatus.AVAILABLE, published.status)
        assertTrue(published.interestClosesAtEpochMs!! > now)
        val interested = seeker.expressInterest(published.id, GeoPoint(-34.5887, -58.4295))
        assertEquals(ParkingStatus.AVAILABLE, interested.status)
        assertTrue(interested.viewerInterested)
        assertEquals(1, interested.interestCount)
        assertEquals(null, interested.claimedByUserId)
    }

    @Test
    fun alCerrarLaVentanaGanaElElegibleConMasPeso() = runBlocking {
        val db = OgtLocalDatabase.seeded()
        var now = 2_000_000L
        val owner = LocalParkingRepository(db, OgtIds.Mariana, Random(0), { now })
        val low = LocalParkingRepository(db, OgtIds.Lucas, Random(0), { now })
        val high = LocalParkingRepository(db, OgtIds.Sofia, Random(0), { now })
        val car = GeoPoint(-34.5886, -58.4294)
        val published = owner.publishVacancy(car, 12, "Salgo", null, car)
        low.expressInterest(published.id, car)
        high.expressInterest(published.id, car)
        now += ParkingRules.INTEREST_WINDOW_MS + 1
        val after = owner.get(published.id)!!
        assertEquals(ParkingStatus.CLAIMED, after.status)
        assertTrue(after.claimedByUserId == OgtIds.Lucas || after.claimedByUserId == OgtIds.Sofia)
    }

    @Test
    fun siNadieLlegaATiempoQuedaFCFS() = runBlocking {
        val db = OgtLocalDatabase.seeded()
        var now = 3_000_000L
        val owner = LocalParkingRepository(db, OgtIds.Mariana, Random(1), { now })
        val far = LocalParkingRepository(db, OgtIds.Lucas, Random(1), { now })
        val car = GeoPoint(-34.5886, -58.4294)
        val published = owner.publishVacancy(car, 12, "Salgo", null, car)
        far.expressInterest(published.id, GeoPoint(-34.70, -58.50))
        now += ParkingRules.INTEREST_WINDOW_MS + 1
        val leftover = owner.get(published.id)!!
        assertEquals(ParkingStatus.AVAILABLE, leftover.status)
        assertTrue(leftover.leftoverOpen)
        val claimed = far.claim(leftover.id, leftover.version, GeoPoint(-34.70, -58.50))
        assertEquals(ParkingStatus.CLAIMED, claimed.status)
        assertEquals(OgtIds.Lucas, claimed.claimedByUserId)
    }

    @Test
    fun elDuenioNoEsperaMasDelTope() = runBlocking {
        val db = OgtLocalDatabase.seeded()
        var now = 4_000_000L
        val owner = LocalParkingRepository(db, OgtIds.Mariana, Random(1), { now })
        val car = GeoPoint(-34.5886, -58.4294)
        val published = owner.publishVacancy(car, 12, "Salgo", null, car)
        now = published.ownerWaitDeadlineAtEpochMs!! + 1
        val expired = owner.get(published.id)
        assertEquals(ParkingStatus.EXPIRED, expired?.status)
    }
}
