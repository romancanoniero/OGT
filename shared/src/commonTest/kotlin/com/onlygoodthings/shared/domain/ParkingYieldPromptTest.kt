package com.onlygoodthings.shared.domain

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ParkingYieldPromptTest {

    @Test
    fun preguntaSiDijoQueSeIbaYEstaCerca() {
        assertTrue(shouldAskYieldOnApproach(willLeave = true, farthestMeters = 10.0, nowMeters = 20.0, alreadyAsked = false))
    }

    @Test
    fun preguntaSiVolvioDespuesDeAlejarse() {
        assertTrue(shouldAskYieldOnApproach(willLeave = false, farthestMeters = 180.0, nowMeters = 30.0, alreadyAsked = false))
    }

    @Test
    fun noPreguntaSiSigueLejos() {
        assertFalse(shouldAskYieldOnApproach(willLeave = true, farthestMeters = 200.0, nowMeters = 90.0, alreadyAsked = false))
    }

    @Test
    fun noRepiteSiYaPregunto() {
        assertFalse(shouldAskYieldOnApproach(willLeave = true, farthestMeters = 200.0, nowMeters = 10.0, alreadyAsked = true))
    }
}
