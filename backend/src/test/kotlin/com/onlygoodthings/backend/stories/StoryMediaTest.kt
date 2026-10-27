package com.onlygoodthings.backend.stories

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.ByteBuffer
import java.nio.file.Path
import kotlin.test.*

class StoryMediaTest {
    @TempDir lateinit var root: Path
    @Test fun leasesBindViewerVariantAndTimeAndSurviveServerRestart() {
        val media=StoryMediaStore(root,"https://example.test")
        val id="10000000-0000-4000-8000-000000000001"
        val url=media.url(id,"viewer",120000)
        val signature=url.substringAfter("signature=")
        assertTrue(media.signed(id,"viewer",120000,"media",signature,100000))
        assertTrue(StoryMediaStore(root,"https://example.test").signed(id,"viewer",120000,"media",signature,100000))
        assertFalse(media.signed(id,"other",120000,"media",signature,100000))
        assertFalse(media.signed(id,"viewer",120000,"admin-media",signature,100000))
        assertFalse(media.signed(id,"viewer",120000,"media",signature,120000))
        assertFailsWith<IllegalArgumentException> { media.path("../escape") }
    }
    private fun box(name:String,bytes:ByteArray) = ByteBuffer.allocate(bytes.size+8).putInt(bytes.size+8).put(name.toByteArray()).put(bytes).array()
    private fun video(seconds:Int):ByteArray {
        val header=ByteArray(24)
        ByteBuffer.wrap(header).putInt(12,1000).putInt(16,seconds*1000)
        val handler=ByteArray(12).also { "vide".toByteArray().copyInto(it,8) }
        return box("ftyp","isom0000".toByteArray())+box("moov",box("mvhd",header)+box("trak",box("mdia",box("hdlr",handler))))+box("mdat",byteArrayOf(1,2,3,4))
    }
    @Test fun durationComesFromTheFileAndMalformedFilesAreRejected() {
        assertEquals(30000L,mp4DurationMs(video(30)))
        val media=StoryMediaStore(root,"https://example.test")
        assertFailsWith<IllegalArgumentException> { media.validate(video(31),true) }
        assertFailsWith<IllegalArgumentException> { mp4DurationMs(video(2).dropLast(1).toByteArray()) }
        assertFailsWith<IllegalArgumentException> { media.validate("fake image".toByteArray(),false) }
    }
}
