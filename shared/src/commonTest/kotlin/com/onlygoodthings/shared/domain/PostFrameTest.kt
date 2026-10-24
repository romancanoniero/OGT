package com.onlygoodthings.shared.domain

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PostFrameTest {
    @Test
    fun elMarcoDelFeedEs16a9() {
        assertEquals(16.0 / 9.0, PostFrame.ASPECT, 0.0001)
    }

    @Test
    fun zoomUnoCubreElMarco() {
        val scale = PostFrame.coverScale(100.0, 100.0, 0.0, 160.0, 90.0)
        assertEquals(1.6, scale, 0.001)
        val wide = PostFrame.coverScale(320.0, 90.0, 0.0, 160.0, 90.0)
        assertEquals(1.0, wide, 0.001)
    }

    @Test
    fun elPaneoNoDejaElMarcoVacio() {
        val (x, y) = PostFrame.clampPan(4.0, -4.0, 400.0, 300.0, 0.0, 1.0, 160.0, 90.0)
        assertTrue(abs(x) < 4.0)
        assertTrue(abs(y) < 4.0)
    }

    @Test
    fun laSalidaNoSuperaElLadoLargo() {
        val (w, h) = PostFrame.outputSize(4000, 3000, 0.0, 1.0)
        assertTrue(w <= PostFrame.MAX_EDGE)
        assertTrue(h <= PostFrame.MAX_EDGE)
        assertEquals(16.0 / 9.0, w.toDouble() / h.toDouble(), 0.02)
    }
}
