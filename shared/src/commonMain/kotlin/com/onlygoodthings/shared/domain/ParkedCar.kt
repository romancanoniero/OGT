package com.onlygoodthings.shared.domain

import kotlinx.serialization.Serializable

/** Ficha del auto: el que busca el lugar tiene que reconocerlo en la calle. */
@Serializable
data class VehicleProfile(
    val id: String = "",
    val make: String = "",
    val color: String = "",
    val colorHex: String = "#6B7280",
    val plate: String = "",
) {
    fun isReady(): Boolean = make.isNotBlank() && color.isNotBlank() && plate.isNotBlank()
    fun label(): String = listOf(make, color, plate).filter { it.isNotBlank() }.joinToString(" · ")
    fun ensureId(): VehicleProfile {
        if (id.isNotBlank()) return this
        val key = plate.ifBlank { "$make-$color" }.trim().lowercase()
        return copy(id = "car-${key.hashCode().toUInt()}")
    }
}

/** Auto memorizado por el vecino para encontrarlo después. */
@Serializable
data class ParkedCar(
    val latitude: Double,
    val longitude: Double,
    val parkedAtEpochMs: Long,
    val headingDegrees: Float? = null,
    val maneuver: String? = null,
    val note: String? = null,
    val address: String? = null,
    val vehicle: VehicleProfile? = null,
) {
    fun point(): GeoPoint = GeoPoint(latitude, longitude)
}

enum class LocationScope {
    WHILE_USING,
    ALWAYS,
}

fun LocationScope.prefCode(): String = when (this) {
    LocationScope.ALWAYS -> "always"
    LocationScope.WHILE_USING -> "while"
}

fun locationScopeFromPref(raw: String): LocationScope =
    if (raw == "always") LocationScope.ALWAYS else LocationScope.WHILE_USING

enum class ParkingManeuver {
    LATERAL,
    HEAD_IN,
    REVERSE,
}

object ParkingMotionRules {
    const val VEHICLE_SPEED_MPS = 6.5f
    const val CRAWL_SPEED_MPS = 2.8f
    const val STOP_SPEED_MPS = 1.15f
    const val WINDOW_MS = 90_000L
    const val MIN_DRIVE_MS = 20_000L
    const val COOLDOWN_MS = 8 * 60_000L
    const val PARALLEL_HEADING_MIN = 45.0
    const val PARALLEL_HEADING_MAX = 140.0
    const val REVERSE_HEADING = 110.0
}
