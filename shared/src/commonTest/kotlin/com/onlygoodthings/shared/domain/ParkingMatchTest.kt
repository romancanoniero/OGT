package com.onlygoodthings.shared.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ParkingMatchTest {

    private val cerca = ParkingCandidate("cerca", etaSeconds = 40, communityPoints = 10, expressedAtEpochMs = 1)
    private val puntos = ParkingCandidate("puntos", etaSeconds = 50, communityPoints = 90, expressedAtEpochMs = 2)
    private val lejos = ParkingCandidate("lejos", etaSeconds = 400, communityPoints = 500, expressedAtEpochMs = 3)

    @Test
    fun elegibleSoloSiLlegaEnEtaDelDuenioMasUnMinuto() {
        val ownerEta = 30
        assertTrue(ParkingMatch.isEligible(cerca.etaSeconds, ownerEta))
        assertTrue(ParkingMatch.isEligible(puntos.etaSeconds, ownerEta))
        assertTrue(!ParkingMatch.isEligible(lejos.etaSeconds, ownerEta))
        assertEquals(
            listOf(cerca, puntos),
            ParkingMatch.eligibleOf(listOf(cerca, puntos, lejos), ownerEta),
        )
    }

    @Test
    fun sorteoPonderadoRespetaElTicket() {
        val elegibles = listOf(cerca, puntos)
        assertEquals(100, ParkingMatch.totalWeight(elegibles))
        assertEquals(cerca, ParkingMatch.pickWinner(elegibles, 0))
        assertEquals(cerca, ParkingMatch.pickWinner(elegibles, 9))
        assertEquals(puntos, ParkingMatch.pickWinner(elegibles, 10))
        assertEquals(puntos, ParkingMatch.pickWinner(elegibles, 99))
        assertNull(ParkingMatch.pickWinner(emptyList(), 0))
    }

    @Test
    fun siNadieEsElegibleNoHayGanadorYQuedaLeftover() {
        assertNull(ParkingMatch.drawWinner(ParkingMatch.eligibleOf(listOf(lejos), ownerEtaSeconds = 10), kotlin.random.Random(1)))
    }
}
