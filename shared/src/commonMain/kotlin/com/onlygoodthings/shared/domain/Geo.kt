package com.onlygoodthings.shared.domain

import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

object GeoMath {
    private const val EARTH_RADIUS_M = 6_371_000.0

    fun haversineMeters(a: GeoPoint, b: GeoPoint): Double {
        val dLat = toRad(b.latitude - a.latitude)
        val dLon = toRad(b.longitude - a.longitude)
        val lat1 = toRad(a.latitude)
        val lat2 = toRad(b.latitude)
        val h = sin(dLat / 2) * sin(dLat / 2) +
            cos(lat1) * cos(lat2) * sin(dLon / 2) * sin(dLon / 2)
        return 2 * EARTH_RADIUS_M * atan2(sqrt(h), sqrt(1 - h))
    }

    /**
     * ETA simple: usa velocidad reportada o 4.5 m/s (caminata + maniobra urbana).
     */
    fun etaSeconds(distanceMeters: Double, speedMps: Double?): Int {
        val speed = (speedMps ?: 4.5).coerceAtLeast(0.8)
        return (distanceMeters / speed).roundToInt().coerceAtLeast(1)
    }

    /** ETA a pie: 1.4 m/s salvo que el GPS reporte otra velocidad. */
    fun walkEtaSeconds(from: GeoPoint, to: GeoPoint, speedMps: Double? = null): Int =
        etaSeconds(haversineMeters(from, to), speedMps ?: ParkingRules.WALK_SPEED_MPS)

    /** Punto a [meters] del origen, rumbo en grados (0 = norte, 90 = este). */
    fun destination(from: GeoPoint, meters: Double, bearingDegrees: Double): GeoPoint {
        val ang = meters / EARTH_RADIUS_M
        val br = toRad(bearingDegrees)
        val lat1 = toRad(from.latitude)
        val lon1 = toRad(from.longitude)
        val lat2 = asin(sin(lat1) * cos(ang) + cos(lat1) * sin(ang) * cos(br))
        val lon2 = lon1 + atan2(
            sin(br) * sin(ang) * cos(lat1),
            cos(ang) - sin(lat1) * sin(lat2),
        )
        return GeoPoint(toDeg(lat2), toDeg(lon2))
    }

    private fun toRad(deg: Double): Double = deg * (kotlin.math.PI / 180.0)
    private fun toDeg(rad: Double): Double = rad * (180.0 / kotlin.math.PI)
}
