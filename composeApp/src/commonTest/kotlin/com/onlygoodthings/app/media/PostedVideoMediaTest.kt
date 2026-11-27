package com.onlygoodthings.app.media

import com.onlygoodthings.shared.domain.MediaKind
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PostedVideoMediaTest {
    @Test fun stillPreviewsUseThePosterWithoutReadingTheMp4AndPhotosKeepTheirOriginal() {
        val stream = "https://example.test/posted.mp4"
        for (poster in listOf("https://example.test/poster.jpg", "file:///tmp/poster.jpg", "asset://seed-poster")) {
            assertEquals(poster, postPreviewUrl(MediaKind.VIDEO, stream, poster))
        }
        assertEquals("", postPreviewUrl(MediaKind.VIDEO, stream, null))
        assertEquals("https://example.test/photo.jpg", postPreviewUrl(MediaKind.IMAGE, "https://example.test/photo.jpg", "https://example.test/fallback.jpg"))
    }

    @Test fun cachedPlaybackPreservesTheVideoTypeAndReusesExistingBinDownloads() {
        val bytes = byteArrayOf(0, 0, 0, 32, 102, 116, 121, 112)
        val url = "https://example.test/posted-video-cache-${kotlin.random.Random.nextLong()}.mp4?version=2"
        // Represents the already downloaded file on phones running the previous app.
        val files = mutableMapOf(legacyDiskName(url) to bytes)
        val read = { name: String -> files[name] }
        val write = { name: String, content: ByteArray -> files[name] = content }
        assertContentEquals(bytes, OgtMediaCache.readCachedFile(url, read, write))
        assertTrue(diskName(url).endsWith(".mp4"))
        assertContentEquals(bytes, files[diskName(url)])
        assertContentEquals(bytes, files[legacyDiskName(url)])
        val photo = "https://example.test/photo-cache-regression.jpg"
        assertEquals(legacyDiskName(photo), diskName(photo))
        assertTrue(diskName(photo).endsWith(".bin"))
    }
}
