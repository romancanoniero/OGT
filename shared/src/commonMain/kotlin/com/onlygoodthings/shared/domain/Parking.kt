package com.onlygoodthings.shared.domain

import kotlinx.serialization.Serializable

@Serializable
enum class ParkingStatus {
    AVAILABLE,
    CLAIMED,
    COMPLETED,
    EXPIRED,
    CANCELLED,
}

@Serializable
data class GeoPoint(
    val latitude: Double,
    val longitude: Double,
)

/** Reglas de cesión: el cliente no cierra el claim ni suma puntos. */
object ParkingRules {
    const val DEFAULT_TTL_MINUTES = 12
    const val PROXIMITY_METERS = 40.0
    const val DEFAULT_RADIUS_METERS = 400
    const val MAX_RADIUS_METERS = 2000
    const val MAX_NEARBY = 50
    const val DEFAULT_REWARD_POINTS = 50
    const val SHARE_BONUS_POINTS = 15
    const val CO2_GRAMS_PER_HANDOFF = 850
    const val PUBLISH_RATE_PER_MINUTE = 8
    /** Si el vecino dijo que se iba, preguntamos al acercarse a esta distancia. */
    const val YIELD_ASK_METERS = 50.0
    /** Tuvo que alejarse al menos esto para tratar el regreso como “voy a dejar el auto”. */
    const val AWAY_BEFORE_YIELD_METERS = 120.0
    /** Caminata urbana para ETA a pie (Valhalla/OSRM o haversine). */
    const val WALK_SPEED_MPS = 1.4
    /** Ventana de interés: todos ven la vacante; nadie se la lleva todavía. */
    const val INTEREST_WINDOW_MS = 20_000L
    /** El cedente espera como máximo esto después de su ETA al auto. */
    const val OWNER_WAIT_SECONDS = 60
}

/** Fase de emparejamiento que ve el cliente; el ganador lo decide el backend. */
enum class ParkingMatchingPhase {
    INTEREST,
    LEFTOVER,
    ASSIGNED,
    CLOSED,
}

/** Decide si preguntamos por ceder al volver al auto memorizado. */
fun shouldAskYieldOnApproach(
    willLeave: Boolean,
    farthestMeters: Double,
    nowMeters: Double,
    alreadyAsked: Boolean,
): Boolean {
    if (alreadyAsked) return false
    val closeEnough = nowMeters <= ParkingRules.YIELD_ASK_METERS
    val cameBack = farthestMeters >= ParkingRules.AWAY_BEFORE_YIELD_METERS
    return closeEnough && (willLeave || cameBack)
}

@Serializable
data class ParkingSpot(
    val id: String,
    val ownerUserId: String,
    val claimedByUserId: String?,
    val location: GeoPoint,
    val status: ParkingStatus,
    val version: Int,
    val expiresAtEpochMs: Long,
    val distanceMeters: Double?,
    val etaSeconds: Int?,
    val rewardPoints: Int,
    val notes: String?,
    val address: String? = null,
    val vehicleLabel: String? = null,
    val ownerEtaSeconds: Int? = null,
    val interestClosesAtEpochMs: Long? = null,
    val ownerWaitDeadlineAtEpochMs: Long? = null,
    val leftoverOpen: Boolean = false,
    val interestCount: Int = 0,
    val viewerInterested: Boolean = false,
)

fun ParkingSpot.matchingPhase(nowEpochMs: Long): ParkingMatchingPhase = when (status) {
    ParkingStatus.CLAIMED -> ParkingMatchingPhase.ASSIGNED
    ParkingStatus.AVAILABLE -> {
        val windowOpen = !leftoverOpen && (interestClosesAtEpochMs ?: 0L) > nowEpochMs
        if (windowOpen) ParkingMatchingPhase.INTEREST else ParkingMatchingPhase.LEFTOVER
    }
    else -> ParkingMatchingPhase.CLOSED
}

fun ParkingSpot.interestSecondsLeft(nowEpochMs: Long): Int {
    val closes = interestClosesAtEpochMs ?: return 0
    return ((closes - nowEpochMs).coerceAtLeast(0L) / 1000L).toInt()
}

fun ParkingSpot.waitSecondsLeft(nowEpochMs: Long): Int {
    val deadline = ownerWaitDeadlineAtEpochMs ?: return 0
    return ((deadline - nowEpochMs).coerceAtLeast(0L) / 1000L).toInt()
}

@Serializable
data class ParkingHandoff(
    val id: String,
    val parkingSpotId: String,
    val ownerUserId: String,
    val claimantUserId: String,
    val proximityMeters: Double,
    val verified: Boolean,
    val pointsAwarded: Int,
    val completedAtEpochMs: Long,
)

@Serializable
data class ParkingCompleteResult(
    val spot: ParkingSpot,
    val handoff: ParkingHandoff,
)
