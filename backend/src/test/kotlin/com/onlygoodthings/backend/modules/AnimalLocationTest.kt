package com.onlygoodthings.backend.modules

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class AnimalLocationTest {
    private val draft=mapOf<String,Any?>("kind" to "ADOPTION","species" to "CAT","petName" to "Luna","description" to "Busca hogar","place" to "Córdoba")
    @Test fun adoptionDoesNotBorrowCoordinatesFromTheAuthorOrBuenosAires() {
        val unknown=animalWriteFrom(draft,-34.6 to -58.4)
        assertNull(unknown.latitude);assertNull(unknown.longitude)
        val known=animalWriteFrom(draft+mapOf("latitude" to -31.42,"longitude" to -64.18),-34.6 to -58.4)
        assertEquals(-31.42,known.latitude);assertEquals(-64.18,known.longitude)
    }
    @Test fun partialInvalidAndMissingLostLocationsAreRejected() {
        for(point in listOf(mapOf("latitude" to -31.0),mapOf("latitude" to Double.NaN,"longitude" to 0.0),mapOf("latitude" to 91.0,"longitude" to 0.0)))
            assertThrows(IllegalArgumentException::class.java) { animalWriteFrom(draft+point,null) }
        assertThrows(IllegalArgumentException::class.java) { animalWriteFrom(draft+mapOf("kind" to "LOST","marks" to "Blanca","lastSeenPlace" to "Plaza"),-34.6 to -58.4) }
    }
}
