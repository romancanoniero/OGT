package com.onlygoodthings.backend.social

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class OgtProfilePhotoUrlTest {
    @Test fun acceptsOnlyOgtHostedImages() {
        val valid = "550e8400-e29b-41d4-a716-446655440000"
        assertTrue(ogtUploadedProfilePhotoUrl("https://onlygoodthings.lat/media/u/$valid.jpg"))
        assertTrue(ogtUploadedProfilePhotoUrl("https://onlygoodthings.lat/media/u/$valid.webp"))
        assertFalse(ogtUploadedProfilePhotoUrl("file:///private/tmp/avatar.jpg"))
        assertFalse(ogtUploadedProfilePhotoUrl("asset://avatar.png"))
        assertFalse(ogtUploadedProfilePhotoUrl("https://another.example/media/u/$valid.jpg"))
        assertFalse(ogtUploadedProfilePhotoUrl("https://onlygoodthings.lat/media/u/../avatar.jpg"))
        assertFalse(ogtUploadedProfilePhotoUrl("https://onlygoodthings.lat/media/u/$valid.mp4"))
    }
}
