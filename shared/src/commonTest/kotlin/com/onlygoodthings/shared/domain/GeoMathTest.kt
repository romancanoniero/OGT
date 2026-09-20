package com.onlygoodthings.shared.domain

import kotlin.test.Test
import kotlin.test.assertTrue

class GeoMathTest {
    @Test
    fun plazaCercanaEnBuenosAires() {
        val a = GeoPoint(-34.6037, -58.3816)
        val b = GeoPoint(-34.6045, -58.3824)
        val meters = GeoMath.haversineMeters(a, b)
        assertTrue(meters in 50.0..200.0, "distancia=$meters")
        assertTrue(GeoMath.etaSeconds(meters, 1.4) > 0)
    }
}
