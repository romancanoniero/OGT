package com.onlygoodthings.shared.domain

import kotlin.math.abs
import kotlin.math.min

/** Muestra de movimiento para detectar marcha y maniobra de estacionamiento. */
data class MotionSample(
    val latitude: Double,
    val longitude: Double,
    val speedMps: Float?,
    val bearingDegrees: Float?,
    val epochMs: Long,
)

/**
 * Detecta si el vecino venía a velocidad de auto y frenó con patrón
 * lateral, de frente o marcha atrás. No usa ubicación de seed.
 */
class DriveParkingDetector {
    private val samples = ArrayDeque<MotionSample>()
    private var lastPromptAt = 0L

    fun reset() {
        samples.clear()
        lastPromptAt = 0L
    }

    fun onSample(sample: MotionSample): ParkingManeuver? {
        samples.addLast(sample)
        trim(sample.epochMs)
        if (lastPromptAt > 0L && sample.epochMs - lastPromptAt < ParkingMotionRules.COOLDOWN_MS) return null
        val speed = sample.speedMps ?: return null
        if (speed > ParkingMotionRules.STOP_SPEED_MPS) return null
        if (!wasDriving(sample.epochMs)) return null
        val maneuver = classify(sample.epochMs) ?: return null
        lastPromptAt = sample.epochMs
        return maneuver
    }

    private fun trim(now: Long) {
        val cut = now - ParkingMotionRules.WINDOW_MS
        while (samples.isNotEmpty() && samples.first().epochMs < cut) {
            samples.removeFirst()
        }
    }

    private fun wasDriving(now: Long): Boolean {
        var driveMs = 0L
        var prev: MotionSample? = null
        for (sample in samples) {
            val speed = sample.speedMps ?: continue
            if (speed >= ParkingMotionRules.VEHICLE_SPEED_MPS && prev != null) {
                driveMs += (sample.epochMs - prev.epochMs).coerceAtLeast(0L)
            }
            prev = sample
        }
        if (driveMs >= ParkingMotionRules.MIN_DRIVE_MS) return true
        val fast = samples.count { (it.speedMps ?: 0f) >= ParkingMotionRules.VEHICLE_SPEED_MPS }
        return fast >= 4
    }

    private fun classify(now: Long): ParkingManeuver? {
        val recent = samples.filter { now - it.epochMs <= 35_000L }
        if (recent.size < 4) return null
        val headings = recent.mapNotNull { it.bearingDegrees }
        val headingSpan = headingSpan(headings)
        val crawl = recent.count { speed ->
            val v = speed.speedMps ?: return@count false
            v in ParkingMotionRules.STOP_SPEED_MPS..ParkingMotionRules.CRAWL_SPEED_MPS
        }
        val path = pathLength(recent)
        val net = GeoMath.haversineMeters(recent.first().point(), recent.last().point())
        val wiggly = net > 1 && path / net >= 1.7
        return when {
            headingSpan >= ParkingMotionRules.REVERSE_HEADING && crawl >= 2 -> ParkingManeuver.REVERSE
            headingSpan in ParkingMotionRules.PARALLEL_HEADING_MIN..ParkingMotionRules.PARALLEL_HEADING_MAX || wiggly ->
                ParkingManeuver.LATERAL
            headingSpan < ParkingMotionRules.PARALLEL_HEADING_MIN -> ParkingManeuver.HEAD_IN
            else -> ParkingManeuver.LATERAL
        }
    }

    private fun MotionSample.point() = GeoPoint(latitude, longitude)

    private fun pathLength(items: List<MotionSample>): Double {
        var sum = 0.0
        for (i in 1 until items.size) {
            sum += GeoMath.haversineMeters(items[i - 1].point(), items[i].point())
        }
        return sum
    }

    private fun headingSpan(values: List<Float>): Double {
        if (values.size < 2) return 0.0
        var maxDelta = 0.0
        val first = values.first()
        for (value in values) {
            maxDelta = maxDelta.coerceAtLeast(headingDelta(first, value))
        }
        return maxDelta
    }

    private fun headingDelta(a: Float, b: Float): Double {
        val raw = abs(a - b) % 360.0
        return min(raw, 360.0 - raw)
    }
}
