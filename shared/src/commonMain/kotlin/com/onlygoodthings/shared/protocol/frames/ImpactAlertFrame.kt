package com.onlygoodthings.shared.protocol.frames

import kotlinx.serialization.Serializable

@Serializable
data class ImpactAlertFrame(
    val alertId: String,
    val kind: ImpactKind,
    val title: String,
    val body: String,
    val latitude: Double,
    val longitude: Double,
    val radiusMeters: Int,
    val urgency: String,
    val deepLink: String? = null,
)

@Serializable
enum class ImpactKind {
    LOST_PET,
    FOUND_PET,
    VOLUNTEER_CALL,
    COMMUNITY_CAUSE,
    PARKING_NEARBY,
}
