package com.onlygoodthings.shared.domain

import kotlin.random.Random

/** Candidato que anotó interés. El cliente no elige ganador. */
data class ParkingCandidate(
    val userId: String,
    val etaSeconds: Int,
    val communityPoints: Int,
    val expressedAtEpochMs: Long,
)

/**
 * Emparejamiento de cesión: ventana corta, elegibles según ETA del dueño + 60 s,
 * y sorteo ponderado por puntos. Si nadie llega a tiempo, queda FCFS leftover.
 */
object ParkingMatch {

    fun interestClosesAt(publishedAtEpochMs: Long): Long =
        publishedAtEpochMs + ParkingRules.INTEREST_WINDOW_MS

    fun ownerWaitDeadlineAt(publishedAtEpochMs: Long, ownerEtaSeconds: Int): Long =
        publishedAtEpochMs + (ownerEtaSeconds + ParkingRules.OWNER_WAIT_SECONDS) * 1000L

    fun isEligible(seekerEtaSeconds: Int, ownerEtaSeconds: Int): Boolean =
        seekerEtaSeconds <= ownerEtaSeconds + ParkingRules.OWNER_WAIT_SECONDS

    fun weight(communityPoints: Int): Int = communityPoints.coerceAtLeast(1)

    fun eligibleOf(candidates: List<ParkingCandidate>, ownerEtaSeconds: Int): List<ParkingCandidate> =
        candidates.filter { isEligible(it.etaSeconds, ownerEtaSeconds) }

    fun totalWeight(eligible: List<ParkingCandidate>): Int = eligible.sumOf { weight(it.communityPoints) }

    /** [ticket] en `[0, totalWeight)`. */
    fun pickWinner(eligible: List<ParkingCandidate>, ticket: Int): ParkingCandidate? {
        if (eligible.isEmpty()) return null
        var remain = ticket.coerceAtLeast(0)
        for (candidate in eligible) {
            remain -= weight(candidate.communityPoints)
            if (remain < 0) return candidate
        }
        return eligible.last()
    }

    fun drawWinner(eligible: List<ParkingCandidate>, random: Random): ParkingCandidate? {
        val total = totalWeight(eligible)
        if (total <= 0) return null
        return pickWinner(eligible, random.nextInt(total))
    }
}
