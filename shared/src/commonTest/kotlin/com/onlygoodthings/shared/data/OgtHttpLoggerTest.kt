package com.onlygoodthings.shared.data
import kotlin.test.*
class OgtHttpLoggerTest {
    @Test fun signedMediaAccessIsNotExposedInDiagnostics() {
        val log="REQUEST: https://onlygoodthings.lat/media/stories/123/media?viewer=person&until=123&signature=secret\nMETHOD: GET"
        val clean=redactHttpLog(log)
        assertTrue(clean.contains("/media/stories/123/media?[redacted]"))
        assertFalse(clean.contains("secret"));assertFalse(clean.contains("person"))
        assertTrue(clean.contains("METHOD: GET"))
    }
    @Test fun ordinaryRoutesAndStatusRemainUseful() {
        val log="RESPONSE: 401 Unauthorized\nFROM: https://onlygoodthings.lat/api/v1/animals/open"
        assertEquals(log,redactHttpLog(log))
    }
    @Test fun multipleUrlsAreRedactedWithoutLosingLines() {
        assertEquals("GET https://a.test/x?[redacted]\nGET https://b.test/y?[redacted]",redactHttpLog("GET https://a.test/x?token=a\nGET https://b.test/y?address=b"))
    }
}
