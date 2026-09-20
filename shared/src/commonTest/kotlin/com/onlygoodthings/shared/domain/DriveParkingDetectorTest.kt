package com.onlygoodthings.shared.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class DriveParkingDetectorTest {
    @Test
    fun noDetectaSiNuncaHuboVelocidadDeAuto() {
        val detector = DriveParkingDetector()
        var t = 1_000L
        repeat(8) {
            assertNull(
                detector.onSample(MotionSample(-34.58, -58.43, 1.0f, 90f, t)),
            )
            t += 2_000
        }
    }

    @Test
    fun detectaLateralTrasMarchaYGiro() {
        val detector = DriveParkingDetector()
        var t = 10_000L
        var heading = 10f
        repeat(12) {
            detector.onSample(MotionSample(-34.5800, -58.4300 + it * 0.00008, 9.0f, heading, t))
            t += 2_000
        }
        repeat(5) {
            heading += 18f
            detector.onSample(MotionSample(-34.5800 + it * 0.00001, -58.4310, 1.8f, heading, t))
            t += 2_000
        }
        val maneuver = detector.onSample(MotionSample(-34.5801, -58.4310, 0.4f, heading, t))
        assertNotNull(maneuver)
        assertEquals(ParkingManeuver.LATERAL, maneuver)
    }
}
