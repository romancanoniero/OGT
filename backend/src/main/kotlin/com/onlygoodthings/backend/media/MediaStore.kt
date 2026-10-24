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
        val declaredExt = extensionOf(filename, contentType)
        val kind = kindOf(declaredExt)
        val optimized = if (kind == MediaKind.IMAGE) ImageOptimizer.optimize(bytes, declaredExt)
            else ImageOptimizer.Result(bytes, declaredExt)
        val ext = optimized.extension
        val stored = "$id.$ext"
        require(isSafeName(stored)) { "Nombre de archivo inválido" }
        val dest = uploadsDir().resolve(stored)
        val temporary = Files.createTempFile(uploadsDir(), ".upload-", ".tmp")
        try {
            Files.write(temporary, optimized.bytes)
            Files.move(temporary, dest)
        } finally { Files.deleteIfExists(temporary) }
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

    /** Nombre local de `/media/u/{uuid}.ext`. No descarga una URL remota. */
    fun storedName(url: String): String? {
        val raw = url.trim()
        if (raw.isEmpty()) return null
        val path = if (raw.startsWith("/")) raw.substringBefore('?').substringBefore('#')
        else runCatching { java.net.URI(raw).path }.getOrNull() ?: return null
        val marker = "/media/u/"
        val at = path.lastIndexOf(marker)
        if (at < 0) return null
        val name = path.substring(at + marker.length)
        if (name.isEmpty() || name.contains('/')) return null
        return name.takeIf { isSafeName(it) }
    }

    fun readLocal(url: String): ByteArray? {
        val file = storedName(url)?.let { resolvePublic(it) } ?: return null
        return Files.readAllBytes(file)
    }

    /** Borra un archivo de este disco. No sigue una URL que no sea `/media/u/`. */
    fun deleteLocal(url: String): Boolean {
        val file = storedName(url)?.let { resolvePublic(it) } ?: return false
        return Files.deleteIfExists(file)
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
            return fromType ?: fromName ?: throw IllegalArgumentException("Formato no soportado. Usá jpg, png, webp, mp4 o webm")
        }

        fun kindOf(ext: String): MediaKind = when (ext) {
            "mp4", "webm" -> MediaKind.VIDEO
            else -> MediaKind.IMAGE
        }
    }
}
