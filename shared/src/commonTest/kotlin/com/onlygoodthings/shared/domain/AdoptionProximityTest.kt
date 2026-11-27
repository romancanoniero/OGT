package com.onlygoodthings.shared.domain

import kotlin.test.*

class AdoptionProximityTest {
    private val here=GeoPoint(-34.6200773,-58.4424893)
    @Test fun nearbyRequiresConfirmedCoordinatesAndCanBeExpandedWithoutHidingOwnPosts() {
        assertTrue(AdoptionProximity.allows(false,here,GeoPoint(-34.60,-58.45)))
        assertFalse(AdoptionProximity.allows(false,here,GeoPoint(-34.92,-57.95)))
        assertTrue(AdoptionProximity.allows(false,here,GeoPoint(-34.92,-57.95),100))
        assertFalse(AdoptionProximity.allows(false,here,GeoPoint(-31.42,-64.18),250))
        assertFalse(AdoptionProximity.allows(false,here,null))
        assertFalse(AdoptionProximity.allows(false,null,here))
        assertTrue(AdoptionProximity.allows(false,null,null,0))
        assertTrue(AdoptionProximity.allows(true,null,null))
        assertNull(AdoptionProximity.point(Double.NaN,0.0))
        assertNull(AdoptionProximity.point(91.0,0.0))
        assertNull(AdoptionProximity.point(0.0,null))
    }
}
