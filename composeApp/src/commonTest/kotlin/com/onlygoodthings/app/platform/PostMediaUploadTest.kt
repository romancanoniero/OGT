package com.onlygoodthings.app.platform

import com.onlygoodthings.shared.domain.*
import kotlin.coroutines.*
import kotlin.test.*

class PostMediaUploadTest {
    @Test fun aMutedVideoUploadsTheSilentFileAndNeverItsAudibleBackup() = immediateSuspendTest {
        val reads=mutableListOf<String>()
        val media=uploadPostMedia(listOf(OgtPickedMedia("v",MediaKind.VIDEO,"/silent.mp4",true,
            preparedForUpload=true,sourcePath="/original.mov",audioMuted=true,unmutedPath="/audible.mp4")),"",
            read={reads+=it;byteArrayOf(1)},upload={_,_->UploadedMedia("v",MediaKind.VIDEO,"https://media.test/silent.mp4","silent.mp4")})
        assertEquals(listOf("/silent.mp4"),reads)
        assertEquals("https://media.test/silent.mp4",media.single().url)
    }
    @Test fun uploadsPreparedCopyAndPosterInOrder() = immediateSuspendTest {
        val reads = mutableListOf<String>()
        val writes = mutableListOf<String>()
        val files = mapOf("/cache/trim.mp4" to byteArrayOf(1, 2), "/cache/poster.jpg" to byteArrayOf(3), "/cache/photo.jpg" to byteArrayOf(4))
        val result = uploadPostMedia(listOf(
            OgtPickedMedia("v", MediaKind.VIDEO, "/cache/trim.mp4", true, "/cache/poster.jpg", true, "/original/long.mov"),
            OgtPickedMedia("p", MediaKind.IMAGE, "/cache/photo.jpg", true),
        ), "Historia", read = { reads += it; files[it] }, upload = { name, bytes ->
            writes += name
            assertContentEquals(files["/cache/$name"], bytes)
            UploadedMedia(name, if(name.endsWith("mp4")) MediaKind.VIDEO else MediaKind.IMAGE, "https://media.test/$name", name)
        })
        assertEquals(listOf("/cache/trim.mp4", "/cache/poster.jpg", "/cache/photo.jpg"), reads)
        assertEquals(listOf("trim.mp4", "poster.jpg", "photo.jpg"), writes)
        assertEquals("https://media.test/trim.mp4", result[0].url)
        assertEquals("https://media.test/poster.jpg", result[0].posterUrl)
        assertEquals(listOf(0, 1), result.map { it.sortOrder })
        assertTrue(result.all { !it.url.startsWith("file:") })
    }

    @Test fun rejectsUnpreparedVideoBeforeReadingOriginal() = immediateSuspendTest {
        assertFailsWith<IllegalArgumentException> {
            uploadPostMedia(listOf(OgtPickedMedia("v",MediaKind.VIDEO,"/original.mov",true)), "", { error("Must not read original") }, { _,_->error("Must not upload") })
        }
    }

    @Test fun uploadFailureCannotBecomeSuccessfulPostMedia() = immediateSuspendTest {
        assertFailsWith<IllegalStateException> {
            uploadPostMedia(listOf(OgtPickedMedia("p",MediaKind.IMAGE,"/p.jpg",true)), "", { byteArrayOf(1) }, { _,_->error("Upload failed") })
        }
    }

    @Test fun rejectsMissingEmptyOversizedAndDeviceOnlyServerUrls() = immediateSuspendTest {
        for(bytes in listOf(null, byteArrayOf(), ByteArray(VideoUploadRules.MAX_BYTES.toInt()+1))) {
            assertFails { uploadPostMedia(listOf(OgtPickedMedia("p",MediaKind.IMAGE,"/p.jpg",true)), "", { bytes }, { _,_->error("Must not upload") }) }
        }
        assertFails { uploadPostMedia(listOf(OgtPickedMedia("p",MediaKind.IMAGE,"/p.jpg",true)), "", { byteArrayOf(1) }, { _,_->UploadedMedia("p",MediaKind.IMAGE,"file:///p.jpg","p.jpg") }) }
    }
}

// Test doubles complete synchronously; a suspended continuation must fail the test.
private fun immediateSuspendTest(block: suspend () -> Unit) {
    var completed = false
    var failure: Throwable? = null
    block.startCoroutine(object: Continuation<Unit> {
        override val context = EmptyCoroutineContext
        override fun resumeWith(result: Result<Unit>) { completed = true; failure = result.exceptionOrNull() }
    })
    assertTrue(completed)
    failure?.let { throw it }
}
