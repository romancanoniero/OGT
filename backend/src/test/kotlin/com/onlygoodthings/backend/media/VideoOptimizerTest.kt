package com.onlygoodthings.backend.media

import com.onlygoodthings.backend.http.JsonBody
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

class VideoOptimizerTest {
    @TempDir lateinit var root: Path
    private fun command(vararg args:String):ByteArray {
        val p=ProcessBuilder(args.toList()).redirectError(ProcessBuilder.Redirect.INHERIT).start()
        val bytes=p.inputStream.readBytes();assertEquals(0,p.waitFor());return bytes
    }
    private fun requireEncoder() {
        val exists=runCatching { ProcessBuilder("ffmpeg","-version").redirectOutput(ProcessBuilder.Redirect.DISCARD).start().waitFor()==0 }.getOrDefault(false)
        if(System.getenv("OGT_REQUIRE_MEDIA_ENCODER")=="true")assertTrue(exists,"CI must install the media encoder")
        assumeTrue(exists,"Native encoder tests run in the media-enabled CI and local QA")
    }
    @Test fun compressionPreservesVideoAudioAspectDurationAndProducesAReadableAppleRangeSource() {
        requireEncoder();val source=root.resolve("source.mp4")
        command("ffmpeg","-v","error","-y","-f","lavfi","-i","testsrc2=size=1280x720:rate=24:duration=2",
            "-f","lavfi","-i","sine=frequency=440:duration=2","-c:v","libx264","-preset","ultrafast","-crf","10","-threads","1","-c:a","aac",source.toString())
        val bytes=Files.readAllBytes(source);val saved=MediaStore(root,"https://example.test").saveMeasured(bytes,"x.mp4","video/mp4")
        assertTrue(saved.storedBytes<saved.receivedBytes)
        val target=root.resolve("u").resolve(saved.media.filename)
        val info=JsonBody.objectMapper.readTree(command("ffprobe","-v","error","-show_streams","-show_format","-of","json",target.toString()))
        assertEquals(1,info["streams"].count { it["codec_type"].asText()=="audio" })
        assertEquals(2.0,info["format"]["duration"].asDouble(),.15)
        val video=info["streams"].first { it["codec_type"].asText()=="video" }
        assertEquals("h264",video["codec_name"].asText());assertEquals(16.0/9,video["width"].asDouble()/video["height"].asDouble(),.01)
        val sound=command("ffmpeg","-v","error","-i",target.toString(),"-vn","-f","s16le","-")
        assertTrue(sound.any { it.toInt()!=0 },"Audio must contain a signal")
        val output=Files.readAllBytes(target);val text=String(output,Charsets.ISO_8859_1)
        assertTrue(text.indexOf("moov")<text.indexOf("mdat"),"MP4 header must be available before the payload")
    }
    @Test fun overlongAndMalformedNewVideosAreRejectedWithoutCreatingPublishedFiles() {
        requireEncoder();val source=root.resolve("long.mp4")
        command("ffmpeg","-v","error","-y","-f","lavfi","-i","color=size=32x32:rate=1:duration=121","-c:v","libx264","-threads","1",source.toString())
        val store=MediaStore(root,"https://example.test")
        assertThrows(IllegalArgumentException::class.java) { store.save(Files.readAllBytes(source),"long.mp4",null) }
        assertThrows(IllegalArgumentException::class.java) { store.save(byteArrayOf(1,2,3),"broken.mp4",null) }
        Files.list(store.uploadsDir()).use { assertEquals(0,it.filter { p->!p.fileName.toString().startsWith(".") }.count()) }
    }
    @Test fun webSelectedRangePreservesSoundAndRejectsOverlongOrOutOfBoundsSelections() {
        requireEncoder();val source=root.resolve("original.mp4")
        command("ffmpeg","-v","error","-y","-f","lavfi","-i","testsrc2=size=320x240:rate=24:duration=4",
            "-f","lavfi","-i","sine=frequency=440:duration=4","-c:v","libx264","-threads","1","-c:a","aac",source.toString())
        val bytes=Files.readAllBytes(source);val selected=VideoOptimizer.trim(bytes,1000,2000)
        val target=root.resolve("selected.mp4");Files.write(target,selected)
        val info=JsonBody.objectMapper.readTree(command("ffprobe","-v","error","-show_streams","-show_format","-of","json",target.toString()))
        assertEquals(1.0,info["format"]["duration"].asDouble(),.15)
        assertEquals(1,info["streams"].count { it["codec_type"].asText()=="audio" })
        assertTrue(command("ffmpeg","-v","error","-i",target.toString(),"-vn","-f","s16le","-").any { it.toInt()!=0 })
        assertThrows(IllegalArgumentException::class.java) { VideoOptimizer.trim(bytes,0,121000) }
        assertThrows(IllegalArgumentException::class.java) { VideoOptimizer.trim(bytes,3000,5000) }
        assertEquals(bytes.size.toLong(),Files.size(source),"The original is never rewritten")
    }
}
