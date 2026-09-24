package com.onlygoodthings.shared.domain

/** Evita saturar POST /me/location: un ping cada 45 s o 80 m. */
object LocationSyncPolicy {
    const val MIN_INTERVAL_MS = 45_000L
    const val MIN_DISTANCE_M = 80.0

    fun shouldUpload(
        previousLat: Double?,
        previousLng: Double?,
        previousEpochMs: Long,
        latitude: Double,
        longitude: Double,
        epochMs: Long,
    ): Boolean {
        if (previousLat == null || previousLng == null || previousEpochMs <= 0L) return true
        val moved = GeoMath.haversineMeters(
            GeoPoint(previousLat, previousLng),
            GeoPoint(latitude, longitude),
        )
        val waited = epochMs - previousEpochMs
        return waited >= MIN_INTERVAL_MS || moved >= MIN_DISTANCE_M
    }
}
