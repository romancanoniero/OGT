package com.onlygoodthings.backend.auth

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class AuthSettingsTest {
    @Test fun `defaults never accept laboratory tokens`() {
        assertFalse(AuthSettings.from { null }.allowDevTokens)
    }
    @Test fun `production rejects both lab tokens and emulator`() {
        for (values in listOf(mapOf("OGT_ALLOW_DEV_TOKENS" to "true"), mapOf("FIREBASE_AUTH_EMULATOR_HOST" to "localhost:9099"))) {
            assertThrows(IllegalArgumentException::class.java) { AuthSettings.from(values::get) }
        }
    }
    @Test fun `development must explicitly enable lab tokens`() {
        assertTrue(AuthSettings.from(mapOf("OGT_ENV" to "development", "OGT_ALLOW_DEV_TOKENS" to "true")::get).allowDevTokens)
        assertFalse(AuthSettings.from(mapOf("OGT_ENV" to "development")::get).allowDevTokens)
    }
    @Test fun `emulator requires local address and demo project`() {
        val values = mapOf("OGT_ENV" to "test", "FIREBASE_AUTH_EMULATOR_HOST" to "127.0.0.1:19099", "OGT_FIREBASE_PROJECT_ID" to "demo-ogt-qa")
        assertEquals("127.0.0.1:19099", AuthSettings.from(values::get).emulatorHost)
        assertThrows(IllegalArgumentException::class.java) { AuthSettings.from((values + ("OGT_FIREBASE_PROJECT_ID" to "real-project"))::get) }
        assertThrows(IllegalArgumentException::class.java) { AuthSettings.from((values + ("FIREBASE_AUTH_EMULATOR_HOST" to "example.com:9099"))::get) }
    }
}
