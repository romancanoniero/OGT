package com.onlygoodthings.backend.stories

import com.onlygoodthings.backend.media.ImageOptimizer
import com.onlygoodthings.shared.domain.MediaKind
import com.onlygoodthings.shared.domain.StoryRules
import java.nio.ByteBuffer
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import java.util.UUID
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/** Private namespace; never reachable through the ordinary /media/u handler. */
class StoryMediaStore(private val root: Path, private val base: String) {
    private val directory = root.resolve("stories-private")
    private val secret: ByteArray
    init {
        Files.createDirectories(directory)
        val key = directory.resolve(".signing-key")
        if (!Files.exists(key)) {
            val bytes = ByteArray(32).also { SecureRandom().nextBytes(it) }
            try { Files.write(key, bytes, java.nio.file.StandardOpenOption.CREATE_NEW) }
            catch (_: java.nio.file.FileAlreadyExistsException) { /* Another worker won. */ }
        }
        runCatching { Files.setPosixFilePermissions(key, java.nio.file.attribute.PosixFilePermissions.fromString("rw-------")) }
        secret = Files.readAllBytes(key)
        require(secret.size == 32) { "Invalid story signing key" }
    }

    data class Validated(val bytes: ByteArray, val type: String, val kind: MediaKind, val durationMs: Long)

    fun validate(bytes: ByteArray, video: Boolean): Validated {
        require(bytes.isNotEmpty() && bytes.size <= StoryRules.MAX_BYTES) { "La historia debe pesar hasta 8 MB" }
        if (video) {
            val duration = mp4DurationMs(bytes)
            require(duration in 1..StoryRules.MAX_VIDEO_MS) { "El video debe durar hasta 30 segundos" }
            return Validated(bytes, "video/mp4", MediaKind.VIDEO, duration)
        }
        val extension = when {
            bytes.size >= 8 && bytes.take(8).toByteArray().contentEquals(byteArrayOf(-119,80,78,71,13,10,26,10)) -> "png"
            bytes.size >= 3 && bytes[0] == (-1).toByte() && bytes[1] == (-40).toByte() -> "jpg"
            bytes.size >= 12 && String(bytes, 0, 4, Charsets.US_ASCII) == "RIFF" && String(bytes,8,4,Charsets.US_ASCII) == "WEBP" -> "webp"
            else -> throw IllegalArgumentException("Elegí una foto JPEG, PNG o WebP válida")
        }
        val image = ImageOptimizer.optimize(bytes, extension)
        return Validated(image.bytes, if (image.extension == "jpg") "image/jpeg" else "image/${image.extension}", MediaKind.IMAGE, StoryRules.PHOTO_MS)
    }

    fun path(id: String, poster: Boolean = false): Path {
        UUID.fromString(id)
        return directory.resolve("$id${if (poster) "-poster" else ""}.bin")
    }
    fun write(id: String, bytes: ByteArray, poster: Boolean = false) {
        val temp = Files.createTempFile(directory, ".pending-", ".tmp")
        try { Files.write(temp, bytes); Files.move(temp, path(id, poster), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE) }
        finally { Files.deleteIfExists(temp) }
    }
    fun delete(id: String) { Files.deleteIfExists(path(id)); Files.deleteIfExists(path(id, true)) }
    fun hash(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    private fun signature(value: String): String = Base64.getUrlEncoder().withoutPadding().encodeToString(
        Mac.getInstance("HmacSHA256").run { init(SecretKeySpec(secret,"HmacSHA256")); doFinal(value.toByteArray()) },
    )
    fun url(id: String, viewer: String, expires: Long, poster: Boolean = false, admin: Boolean = false): String {
        val variant = (if(admin) "admin-" else "") + if (poster) "poster" else "media"
        val value = "$id|$viewer|$expires|$variant"
        return "${base.trimEnd('/')}/media/stories/$id/$variant?viewer=$viewer&until=$expires&signature=${signature(value)}"
    }
    fun signed(id: String, viewer: String, until: Long, variant: String, signature: String, now: Long): Boolean =
        until > now && until <= now + 180_000 && variant in setOf("media", "poster", "admin-media", "admin-poster") &&
            MessageDigest.isEqual(signature.toByteArray(), signature("$id|$viewer|$until|$variant").toByteArray())
}

/** Bounded ISO-BMFF parsing, not client-declared duration. Rejects truncated/non-video files. */
internal fun mp4DurationMs(bytes: ByteArray): Long {
    var duration: Long? = null
    var video = false
    var payload = false
    fun uint(at: Int): Long = ByteBuffer.wrap(bytes, at, 4).int.toLong() and 0xffffffffL
    fun walk(start: Int, end: Int, depth: Int) {
        require(depth <= 5) { "Video inválido" }
        var at = start
        while (at < end) {
            require(end-at >= 8) { "Video incompleto" }
            val shortSize = uint(at)
            val header = if (shortSize == 1L) 16 else 8
            require(end-at >= header) { "Video incompleto" }
            val size = when(shortSize) { 0L -> (end-at).toLong(); 1L -> ByteBuffer.wrap(bytes,at+8,8).long; else -> shortSize }
            require(size >= header && size <= end-at) { "Video inválido" }
            val type = String(bytes, at+4,4,Charsets.US_ASCII)
            val data = at+header
            val next = at+size.toInt()
            if (type == "mdat" && next > data) payload = true
            if (type in setOf("moov","trak","mdia")) walk(data,next,depth+1)
            if (type == "mvhd") {
                require(next-data >= 20) { "Video inválido" }
                val version = bytes[data].toInt()
                require(version == 0 || version == 1) { "Video inválido" }
                val timeOffset = if(version == 1) 20 else 12
                require(next-data >= timeOffset+if(version == 1) 12 else 8) { "Video inválido" }
                val scale = uint(data+timeOffset)
                val ticks = if(version == 1) ByteBuffer.wrap(bytes,data+timeOffset+4,8).long else uint(data+timeOffset+4)
                require(scale > 0 && ticks > 0 && ticks <= Long.MAX_VALUE/1000) { "Duración de video inválida" }
                duration = ticks*1000/scale
            }
            if(type == "hdlr" && next-data >= 12 && String(bytes,data+8,4,Charsets.US_ASCII) == "vide") video = true
            at = next
        }
    }
    require(bytes.size >= 16 && String(bytes,4,4,Charsets.US_ASCII) == "ftyp") { "Usá un video MP4 válido" }
    walk(0,bytes.size,0)
    return duration?.takeIf { video && payload } ?: throw IllegalArgumentException("El archivo no contiene video")
}
