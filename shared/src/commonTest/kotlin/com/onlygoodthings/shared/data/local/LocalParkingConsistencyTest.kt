package com.onlygoodthings.shared.data.local

import com.onlygoodthings.shared.domain.GeoPoint
import com.onlygoodthings.shared.domain.ParkingRules
import com.onlygoodthings.shared.domain.ParkingStatus
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking

/** Flujos de cesión: pin olvidado, radar ajeno, FCFS y cierre. */
class LocalParkingConsistencyTest {

    private val curb = GeoPoint(-34.5886, -58.4294)
    private val walking = GeoPoint(-34.5888, -58.4300)

    private fun repos(now: () -> Long = { 5_000_000L }): Triple<OgtLocalDatabase, LocalParkingRepository, LocalParkingRepository> {
        val db = OgtLocalDatabase.seeded()
        val owner = LocalParkingRepository(db, OgtIds.Mariana, Random(1), now)
        val seeker = LocalParkingRepository(db, OgtIds.Lucas, Random(1), now)
        return Triple(db, owner, seeker)
    }

    @Test
    fun cederSinPinPublicaEnElGpsYElDuenioNoLaVe() = runBlocking {
        val (_, owner, seeker) = repos()
        val published = owner.publishVacancy(curb, 12, "Salgo ahora", "Gol gris", walking)
        assertEquals(ParkingStatus.AVAILABLE, published.status)
        assertEquals(OgtIds.Mariana, published.ownerUserId)
        assertTrue(owner.active().any { it.id == published.id })
        assertTrue(owner.nearby(walking, 400).none { it.id == published.id })
        assertTrue(seeker.nearby(walking, 400).any { it.id == published.id })
    }

    @Test
    fun cancelarCesionLaSacaDelRadarYPermiteCederDeNuevo() = runBlocking {
        val (_, owner, seeker) = repos()
        val first = owner.publishVacancy(curb, 12, "Salgo", null, walking)
        owner.cancel(first.id)
        assertTrue(seeker.nearby(walking, 400).none { it.id == first.id })
        assertTrue(owner.active().none { it.id == first.id })
        val second = owner.publishVacancy(curb, 12, "Salgo otra vez", null, walking)
        assertTrue(second.id != first.id)
        assertTrue(seeker.nearby(walking, 400).any { it.id == second.id })
    }

    @Test
    fun elDuenioNoPuedeReclamarSuPlaza() = runBlocking {
        val (_, owner, _) = repos()
        val published = owner.publishVacancy(curb, 12, "Salgo", null, walking, leftoverNow = true)
        assertFailsWith<IllegalArgumentException> {
            owner.claim(published.id, published.version, walking)
        }
        return@runBlocking
    }

    @Test
    fun cancelarElPedidoDelBuscadorDejaLaPlazaFCFS() = runBlocking {
        val (db, owner, firstSeeker) = repos()
        val sofia = LocalParkingRepository(db, OgtIds.Sofia, Random(1))
        val published = owner.publishVacancy(curb, 12, "Salgo", null, walking, leftoverNow = true)
        firstSeeker.claim(published.id, published.version, walking)
        val released = firstSeeker.cancel(published.id)
        assertEquals(ParkingStatus.AVAILABLE, released.status)
        assertNull(released.claimedByUserId)
        assertTrue(released.leftoverOpen)
        val again = sofia.claim(released.id, released.version, walking)
        assertEquals(ParkingStatus.CLAIMED, again.status)
        assertEquals(OgtIds.Sofia, again.claimedByUserId)
    }

    @Test
    fun encontrarOtroLugarDejaFCFSYElDuenioSigueCediendo() = runBlocking {
        val (_, owner, seeker) = repos()
        val published = owner.publishVacancy(curb, 12, "Salgo", null, walking, leftoverNow = true)
        seeker.claim(published.id, published.version, walking)
        seeker.foundOtherPlace(published.id)
        val live = owner.active().first { it.id == published.id }
        assertEquals(ParkingStatus.AVAILABLE, live.status)
        assertTrue(live.leftoverOpen)
        assertTrue(seeker.nearby(walking, 400).any { it.id == published.id })
    }

    @Test
    fun confirmarLejosFallaYEnElCordonCierraConPuntos() = runBlocking {
        val (db, owner, seeker) = repos()
        val published = owner.publishVacancy(curb, 12, "Salgo", null, walking, leftoverNow = true)
        val ownerPts = db.user(OgtIds.Mariana).communityPoints
        seeker.claim(published.id, published.version, walking)
        assertFailsWith<IllegalArgumentException> {
            seeker.complete(published.id, GeoPoint(-34.70, -58.50))
        }
        val done = seeker.complete(published.id, curb)
        assertEquals(ParkingStatus.COMPLETED, done.spot.status)
        assertEquals(ownerPts + ParkingRules.DEFAULT_REWARD_POINTS, db.user(OgtIds.Mariana).communityPoints)
        assertTrue(owner.history().any { it.parkingSpotId == published.id })
        assertTrue(owner.active().none { it.id == published.id })
    }

    @Test
    fun laVentanaSoloAnotaYUnSoloVecinoQuedaAsignado() = runBlocking {
        var now = 6_000_000L
        val db = OgtLocalDatabase.seeded()
        val owner = LocalParkingRepository(db, OgtIds.Mariana, Random(0), { now })
        val lucas = LocalParkingRepository(db, OgtIds.Lucas, Random(0), { now })
        val sofia = LocalParkingRepository(db, OgtIds.Sofia, Random(0), { now })
        val published = owner.publishVacancy(curb, 12, "Salgo", null, curb)
        lucas.expressInterest(published.id, curb)
        sofia.expressInterest(published.id, curb)
        val during = owner.get(published.id)!!
        assertEquals(ParkingStatus.AVAILABLE, during.status)
        assertEquals(2, during.interestCount)
        now += ParkingRules.INTEREST_WINDOW_MS + 1
        val assigned = owner.get(published.id)!!
        assertEquals(ParkingStatus.CLAIMED, assigned.status)
        assertTrue(assigned.claimedByUserId == OgtIds.Lucas || assigned.claimedByUserId == OgtIds.Sofia)
        val winner = assigned.claimedByUserId
        val loser = if (winner == OgtIds.Lucas) sofia else lucas
        assertFailsWith<IllegalArgumentException> {
            loser.claim(assigned.id, assigned.version, curb)
        }
        return@runBlocking
    }
}
