package com.onlygoodthings.backend.social

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class AccountPreferencesTest {
    @Test fun booleansAndZonesCannotBeCoercedIntoDifferentPrivacyChoices() {
        assertThrows(IllegalArgumentException::class.java) { validateSettings(mapOf("publicProfileVisible" to "false")) }
        assertThrows(IllegalArgumentException::class.java) { validateSettings(mapOf("animalAlertPush" to null)) }
        assertThrows(IllegalArgumentException::class.java) { validateSettings(mapOf("timeZone" to "not/a-zone")) }
        assertEquals(mapOf("publicProfileVisible" to false),validateSettings(mapOf("publicProfileVisible" to false)))
        assertEquals(mapOf("timeZone" to "America/Argentina/Buenos_Aires"),validateSettings(mapOf("timeZone" to "America/Argentina/Buenos_Aires")))
    }
}
