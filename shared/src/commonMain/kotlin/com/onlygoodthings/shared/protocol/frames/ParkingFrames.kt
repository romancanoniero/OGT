package com.onlygoodthings.shared.protocol.frames

import kotlinx.serialization.Serializable

@Serializable
data class ParkingBroadcastFrame(
    val spotId: String,
    val ownerUserId: String,
    val latitude: Double,
    val longitude: Double,
    val expiresAtEpochMs: Long,
    val rewardPoints: Int,
    val notes: String? = null,
    val ownerEtaSeconds: Int? = null,
    val interestClosesAtEpochMs: Long? = null,
    val ownerWaitDeadlineAtEpochMs: Long? = null,
)

@Serializable
data class ParkingClaimFrame(
    val spotId: String,
    val expectedVersion: Int,
    val claimantLatitude: Double,
    val claimantLongitude: Double,
)

@Serializable
data class ParkingClaimResultFrame(
    val spotId: String,
    val accepted: Boolean,
    val newVersion: Int,
    val status: String,
    val conflictReason: String? = null,
)

@Serializable
data class LocationTickFrame(
    val spotId: String,
    val userId: String,
    val role: LocationRole,
    val latitude: Double,
    val longitude: Double,
    val speedMps: Double? = null,
    val headingDegrees: Double? = null,
    val recordedAtEpochMs: Long,
) {
    companion object
}

@Serializable
enum class LocationRole {
    OWNER,
    CLAIMANT,
}

@Serializable
data class LocationPairEtaFrame(
    val spotId: String,
    val distanceMeters: Double,
    val etaSeconds: Int,
    val ownerLatitude: Double,
    val ownerLongitude: Double,
    val claimantLatitude: Double,
    val claimantLongitude: Double,
)
