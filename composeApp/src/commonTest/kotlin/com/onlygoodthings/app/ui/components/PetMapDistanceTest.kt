package com.onlygoodthings.app.ui.components

import com.onlygoodthings.shared.domain.GeoMath
import com.onlygoodthings.shared.domain.GeoPoint
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PetMapDistanceTest {
    private val point = GeoPoint(-34.62, -58.44) // Public city reference, never a device position.

    @Test fun geographicDistancesUseMetersAndKilometersWithoutInventingTravelDistances() {
        assertEquals("0 m", petMapDistance(point, point))
        assertEquals("850 m", petMapDistance(point, GeoMath.destination(point, 850.0, 90.0)))
        assertEquals("1,2 km", petMapDistance(point, GeoMath.destination(point, 1200.0, 90.0)))
        assertEquals("20,0 km", petMapDistance(point, GeoMath.destination(point, 20_000.0, 90.0)))
    }

    @Test fun absentOrInvalidLocationDoesNotProduceAPretendDistance() {
        assertNull(petMapDistance(null, point))
        assertNull(petMapDistance(point, null))
        assertNull(petMapDistance(GeoPoint(Double.NaN, -58.44), point))
        assertNull(petMapDistance(point, GeoPoint(-91.0, -58.44)))
    }
}
