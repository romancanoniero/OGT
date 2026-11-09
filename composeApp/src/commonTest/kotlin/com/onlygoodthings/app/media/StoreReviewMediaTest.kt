package com.onlygoodthings.app.media

import kotlin.test.*
import kotlinx.coroutines.runBlocking

class StoreReviewMediaTest {
    @AfterTest fun clear() { StoreReviewMedia.clear() }

    @Test fun credentialsNeverReachOtherOriginsOrNonMediaPaths()=runBlocking {
        var calls=0
        StoreReviewMedia.configure(true) { calls++;"fixture-token" }
        for(url in listOf("https://example.test/media/a.jpg","https://onlygoodthings.lat.evil.test/media/a.jpg",
            "http://onlygoodthings.lat/media/a.jpg","https://onlygoodthings.lat:8443/media/a.jpg",
            "https://onlygoodthings.lat/terms","https://user@onlygoodthings.lat/media/a.jpg")) {
            assertEquals(url to null,StoreReviewMedia.request(url))
        }
        assertEquals(0,calls)
        assertEquals("https://onlygoodthings.lat/store-review/media/a.jpg?signature=qa" to "fixture-token",StoreReviewMedia.request("https://onlygoodthings.lat/media/a.jpg?signature=qa"))
    }

    @Test fun logoutOrIdentityChangeDuringRenewalPreventsCredentialUse()=runBlocking {
        StoreReviewMedia.configure(true) { StoreReviewMedia.clear();"fixture-token" }
        assertFailsWith<IllegalStateException> { StoreReviewMedia.request("https://onlygoodthings.lat/media/a.jpg") }
        assertEquals("https://onlygoodthings.lat/media/a.jpg" to null,StoreReviewMedia.request("https://onlygoodthings.lat/media/a.jpg"))
    }
}
