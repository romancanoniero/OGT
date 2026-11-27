package com.onlygoodthings.shared.domain

/** Radius zero explicitly includes every area and unconfirmed locations. */
object AdoptionProximity {
    const val DEFAULT_RADIUS_KM = 50
    val radiusOptions = listOf(50, 100, 250, 0)
    fun point(latitude: Double?, longitude: Double?): GeoPoint? =
        if (latitude != null && longitude != null && latitude.isFinite() && longitude.isFinite() &&
            latitude in -90.0..90.0 && longitude in -180.0..180.0) GeoPoint(latitude, longitude) else null

    fun allows(own: Boolean, viewer: GeoPoint?, animal: GeoPoint?, radiusKm: Int = DEFAULT_RADIUS_KM): Boolean =
        own || radiusKm == 0 || (viewer != null && animal != null && GeoMath.haversineMeters(viewer, animal) <= radiusKm * 1_000.0)
}
