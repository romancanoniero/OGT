package com.onlygoodthings.shared.domain

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class PostFrameTest {
    @Test
    fun elMarcoDelFeedEsDieciseisNovenos() {
        assertEquals(16f / 9f, POST_FRAME_RATIO)
        assertTrue(abs(POST_FRAME_RATIO - (16f / 9f)) < 0.0001f)
    }

    @Test
    fun unRetratoCentradoCubreElMarcoSinBandas() {
        val placed = placePostFrame(160f, 90f, 100f, 200f, 0.5f, 0.5f, 1f)
        assertNotNull(placed)
        assertEquals(16f / 9f, 160f / 90f)
        assertTrue(abs(placed.displayWidth - 160f) < 0.02f)
        assertTrue(abs(placed.displayHeight - 320f) < 0.02f)
        assertTrue(abs(placed.left) < 0.02f)
        assertTrue(abs(placed.top - -115f) < 0.05f)
        assertTrue(placed.top <= 0f)
        assertTrue(placed.top + placed.displayHeight >= 90f)
    }

    @Test
    fun elPuntoElegidoQuedaEnElCentro() {
        val placed = placePostFrame(160f, 90f, 100f, 200f, 0.5f, 0.25f, 1f)
        assertNotNull(placed)
        assertEquals(0.25f, placed.centerY)
        assertTrue(abs(placed.top - -35f) < 0.05f)
    }
}
