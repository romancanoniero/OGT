package com.onlygoodthings.backend.media

import com.onlygoodthings.backend.http.JsonBody
import com.onlygoodthings.shared.domain.PostMediaRules
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.TimeUnit
import kotlin.math.abs

/** One bounded encoder at a time on the existing small server. Never trims a clip. */
object VideoOptimizer {
    const val MAX_TRIM_SOURCE_BYTES = 12 * 1024 * 1024
    private data class Info(val duration: Double, val width: Int, val height: Int, val audio: Int,
                            val simple: Boolean, val codec: String, val pixelFormat: String)

    @Synchronized
    fun trim(bytes:ByteArray,startMs:Long,endMs:Long):ByteArray {
        require(bytes.size in 1..MAX_TRIM_SOURCE_BYTES) { "Para recortar en la web, el original debe pesar hasta 12 MB. Para archivos mayores usá la app de OGT." }
        require(startMs>=0 && endMs>startMs && endMs-startMs<=PostMediaRules.MAX_VIDEO_MS) { "Elegí un tramo de hasta 2 minutos" }
        val work=Files.createTempDirectory("ogt-trim-")
        try {
            val source=work.resolve("source.bin");Files.write(source,bytes)
            val info=probe(source) ?: throw IllegalArgumentException("No pudimos leer el video")
            require(endMs<=info.duration*1000+50 && info.width.toLong()*info.height<=40_000_000) { "Revisá el tramo seleccionado" }
            require(info.simple && info.pixelFormat in setOf("yuv420p","yuvj420p","nv12")) { "Este formato necesita el editor de video de la app de OGT" }
            val target=work.resolve("trim.mp4")
            val duration=(endMs-startMs)/1000.0
            val bitrate=((MediaStore.MAX_BYTES*8*.80/duration).toInt()-96_000).coerceIn(100_000,1_600_000)
            val result=run(listOf("ffmpeg","-hide_banner","-loglevel","error","-nostdin","-y","-protocol_whitelist","file,pipe","-threads","1",
                "-ss",(startMs/1000.0).toString(),"-i",source.toString(),"-t",duration.toString(),"-map","0:v:0","-map","0:a?","-map_metadata","-1",
                "-vf","scale=w='min(iw,1280)':h='min(ih,1280)':force_original_aspect_ratio=decrease:force_divisible_by=2",
                "-c:v","libx264","-preset","veryfast","-b:v",bitrate.toString(),"-maxrate",bitrate.toString(),"-bufsize",(bitrate*2).toString(),
                "-pix_fmt","yuv420p","-threads","1","-c:a","aac","-b:a","96k","-movflags","+faststart",target.toString()),work,45)
            require(result!=null && Files.isRegularFile(target) && Files.size(target) in 1..MediaStore.MAX_BYTES.toLong()) { "No pudimos preparar ese tramo. Probá uno más corto o usá la app de OGT." }
            val after=probe(target) ?: throw IllegalArgumentException("No pudimos verificar el recorte")
            require(after.audio==info.audio && abs(after.duration-duration)<=.15 &&
                abs((after.width.toDouble()/after.height)/(info.width.toDouble()/info.height)-1)<.015) { "El recorte no se pudo verificar. El original se conserva." }
            return Files.readAllBytes(target)
        } finally {Files.list(work).use { it.forEach(Files::deleteIfExists) };Files.deleteIfExists(work)}
    }

    @Synchronized
    fun optimize(bytes: ByteArray, extension: String): ImageOptimizer.Result {
        val work = Files.createTempDirectory("ogt-video-")
        try {
            val source = work.resolve("source.$extension")
            Files.write(source, bytes)
            val info = probe(source) ?: throw IllegalArgumentException("El video está dañado o no se puede leer")
            // AAC/container timestamp rounding may add up to 100 ms to a precisely selected clip.
            require(info.duration > 0 && info.duration * 1000 <= PostMediaRules.MAX_VIDEO_MS+100) { "Cada video debe durar hasta 2 minutos" }
            require(info.width.toLong()*info.height<=40_000_000) { "El video supera la resolución permitida" }
            // Extra tracks, HDR, alpha and unusual color formats need a richer pipeline.
            // Preserve their complete source rather than silently discard information.
            if (!info.simple || info.pixelFormat !in setOf("yuv420p", "yuvj420p", "nv12")) return ImageOptimizer.Result(bytes, extension)
            val target = work.resolve("optimized.mp4")
            val encoded = run(listOf("ffmpeg", "-hide_banner", "-loglevel", "error", "-nostdin", "-y",
                "-protocol_whitelist", "file,pipe", "-threads", "1", "-i", source.toString(),
                "-map", "0:v:0", "-map", "0:a?", "-map_metadata", "-1",
                "-vf", "scale=w='min(iw,1280)':h='min(ih,1280)':force_original_aspect_ratio=decrease:force_divisible_by=2",
                "-c:v", "libx264", "-preset", "veryfast", "-crf", "24", "-pix_fmt", "yuv420p", "-threads", "1",
                "-c:a", "aac", "-b:a", "96k", "-movflags", "+faststart", target.toString()), work, 45)
            if (encoded == null || !Files.isRegularFile(target) || Files.size(target) !in 1..MediaStore.MAX_BYTES.toLong()) return ImageOptimizer.Result(bytes, extension)
            if (extension=="mp4" && info.codec=="h264" && Files.size(target)>=bytes.size) return ImageOptimizer.Result(bytes, extension)
            val after = probe(target) ?: return ImageOptimizer.Result(bytes, extension)
            val expected = info.width.toDouble() / info.height
            val actual = after.width.toDouble() / after.height
            if (after.audio != info.audio || abs(after.duration-info.duration) > .15 || abs(actual/expected-1) > .015)
                return ImageOptimizer.Result(bytes, extension)
            return ImageOptimizer.Result(Files.readAllBytes(target), "mp4")
        } finally {
            Files.list(work).use { it.forEach(Files::deleteIfExists) }
            Files.deleteIfExists(work)
        }
    }

    private fun probe(file: Path): Info? {
        val raw = run(listOf("ffprobe", "-v", "error", "-protocol_whitelist", "file,pipe", "-show_streams", "-show_format", "-of", "json", file.toString()), file.parent, 10) ?: return null
        return runCatching {
            val root = JsonBody.objectMapper.readTree(raw)
            val streams = root.path("streams").toList()
            val video = streams.firstOrNull { it.path("codec_type").asText() == "video" } ?: return null
            var duration = root.path("format").path("duration").asDouble(0.0)
            if(duration<=0) {
                // Browser-recorded WebM often omits its duration header; measure packet timestamps.
                val packets=run(listOf("ffprobe","-v","error","-protocol_whitelist","file,pipe","-select_streams","v:0",
                    "-show_entries","packet=pts_time,duration_time","-of","csv=p=0",file.toString()),file.parent,10) ?: return null
                duration=packets.lineSequence().mapNotNull { line ->
                    val fields=line.split(',');val start=fields.firstOrNull()?.toDoubleOrNull() ?: return@mapNotNull null
                    start+(fields.getOrNull(1)?.toDoubleOrNull() ?: 0.0)
                }.maxOrNull() ?: return null
            }
            Info(duration,video.path("width").asInt(),video.path("height").asInt(),streams.count { it.path("codec_type").asText()=="audio" },
                streams.count { it.path("codec_type").asText()=="video" }==1 && streams.all { it.path("codec_type").asText() in setOf("video","audio") } &&
                    video.path("side_data_list").isMissingNode && video.path("color_transfer").asText() !in setOf("smpte2084","arib-std-b67"),
                video.path("codec_name").asText(),video.path("pix_fmt").asText())
        }.getOrNull()?.takeIf { it.duration.isFinite() && it.width>0 && it.height>0 }
    }

    private fun run(args: List<String>, work: Path, seconds: Long): String? {
        val output = Files.createTempFile(work,"process-",".log")
        return try {
            val process = ProcessBuilder(args).redirectErrorStream(true).redirectOutput(output.toFile()).start()
            try {
                if (!process.waitFor(seconds,TimeUnit.SECONDS)) { process.destroyForcibly();process.waitFor();null }
                else if (process.exitValue()!=0 || Files.size(output)>1024*1024) null else Files.readString(output)
            } catch (interrupted: InterruptedException) { process.destroyForcibly();Thread.currentThread().interrupt();throw interrupted }
        } finally { Files.deleteIfExists(output) }
    }
}
