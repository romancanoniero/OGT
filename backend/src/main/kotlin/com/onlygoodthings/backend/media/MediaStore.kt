package com.onlygoodthings.backend.media

import com.onlygoodthings.shared.domain.MediaKind
import com.onlygoodthings.shared.domain.UploadedMedia
import java.nio.file.Files
import java.nio.file.Path
import java.util.Locale
import java.util.UUID

/**
 * Disco de multimedia, el mismo camino que usa una subida de usuaria.
 * Los archivos viven en `{root}/u/{uuid}.{ext}` y se sirven en `/media/u/…`.
 */
class MediaStore(
    private val root: Path,
    private val publicBase: String,
) {
    init {
        Files.createDirectories(uploadsDir())
    }

    fun uploadsDir(): Path = root.resolve("u")

    fun publicUrl(storedName: String): String =
        publicBase.trimEnd('/') + "/media/u/" + storedName

    fun save(bytes: ByteArray, filename: String, contentType: String?, id: String = UUID.randomUUID().toString()): UploadedMedia {
        require(bytes.isNotEmpty()) { "El archivo está vacío" }
        require(bytes.size <= MAX_BYTES) { "El archivo supera ${MAX_BYTES / (1024 * 1024)} MB" }
        val ext = extensionOf(filename, contentType)
        val kind = kindOf(ext)
        val stored = "$id.$ext"
        require(isSafeName(stored)) { "Nombre de archivo inválido" }
        val dest = uploadsDir().resolve(stored)
        Files.write(dest, bytes)
        return UploadedMedia(
            id = id,
            kind = kind,
            url = publicUrl(stored),
            filename = stored,
        )
    }

    fun resolvePublic(name: String): Path? {
        if (!isSafeName(name)) return null
        val file = uploadsDir().resolve(name).normalize()
        if (!file.startsWith(uploadsDir())) return null
        return file.takeIf { Files.isRegularFile(it) }
    }

    fun contentTypeOf(name: String): String = when (name.substringAfterLast('.', "").lowercase(Locale.ROOT)) {
        "jpg", "jpeg" -> "image/jpeg"
        "png" -> "image/png"
        "webp" -> "image/webp"
        "mp4" -> "video/mp4"
        "webm" -> "video/webm"
        else -> "application/octet-stream"
    }

    companion object {
        const val MAX_BYTES = 8 * 1024 * 1024
        private val SAFE_NAME = Regex("^[0-9a-fA-F-]{36}\\.(jpg|jpeg|png|webp|mp4|webm)$")

        fun isSafeName(name: String): Boolean = SAFE_NAME.matches(name)

        fun extensionOf(filename: String, contentType: String?): String {
            val fromType = when (contentType?.lowercase(Locale.ROOT)?.substringBefore(';')?.trim()) {
                "image/jpeg" -> "jpg"
                "image/png" -> "png"
                "image/webp" -> "webp"
                "video/mp4" -> "mp4"
                "video/webm" -> "webm"
                else -> null
            }
            val fromName = filename.substringAfterLast('.', "").lowercase(Locale.ROOT).let { ext ->
                when (ext) {
                    "jpeg" -> "jpg"
                    "jpg", "png", "webp", "mp4", "webm" -> ext
                    else -> null
                }
            }
            return fromType ?: fromName ?: error("Formato no soportado. Usá jpg, png, webp, mp4 o webm")
        }

        fun kindOf(ext: String): MediaKind = when (ext) {
            "mp4", "webm" -> MediaKind.VIDEO
            else -> MediaKind.IMAGE
        }
    }
}
