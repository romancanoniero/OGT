package com.onlygoodthings.app.ui.components

import com.onlygoodthings.shared.domain.AdoptionProximity
import com.onlygoodthings.shared.domain.GeoMath
import com.onlygoodthings.shared.domain.GeoPoint
import kotlin.math.roundToInt

/** Distancia geográfica, nunca una estimación de recorrido por calles. */
internal fun petMapDistance(from: GeoPoint?, to: GeoPoint?): String? {
    if (from == null || to == null) return null
    if (AdoptionProximity.point(from.latitude, from.longitude) == null ||
        AdoptionProximity.point(to.latitude, to.longitude) == null) return null
    val meters = GeoMath.haversineMeters(from, to)
    if (!meters.isFinite()) return null
    return if (meters < 1000) "${meters.roundToInt()} m"
        else "${((meters / 100).roundToInt() / 10.0).toString().replace('.', ',')} km"
}
