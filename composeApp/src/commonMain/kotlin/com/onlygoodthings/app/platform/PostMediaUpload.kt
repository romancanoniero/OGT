package com.onlygoodthings.app.platform

import com.onlygoodthings.shared.domain.MediaKind
import com.onlygoodthings.shared.domain.PostMediaItem
import com.onlygoodthings.shared.domain.PostMediaRules
import com.onlygoodthings.shared.domain.UploadedMedia

/** Upload the selected copy, never the untrimmed source or a device-only URL. */
internal suspend fun uploadPostMedia(
    items: List<OgtPickedMedia>,
    altText: String,
    read: suspend (String) -> ByteArray?,
    upload: suspend (String, ByteArray) -> UploadedMedia,
): List<PostMediaItem> {
    require(items.size <= PostMediaRules.MAX_ITEMS) { "Hay demasiados archivos." }
    return items.mapIndexed { order, item ->
        if (!item.fromDevice) {
            require(item.kind != MediaKind.VIDEO) { "Elegí el video nuevamente desde la galería." }
            return@mapIndexed PostMediaItem(item.id, item.kind, "asset://${item.path}", sortOrder = order, altText = altText, assetKey = item.path)
        }
        require(item.kind != MediaKind.VIDEO || item.preparedForUpload) { "Recortá y prepará el video antes de publicarlo." }
        suspend fun send(path: String): UploadedMedia {
            val bytes = read(path) ?: error("No se pudo leer el archivo. Elegilo nuevamente.")
            require(bytes.isNotEmpty() && bytes.size.toLong() <= VideoUploadRules.MAX_BYTES) { "El archivo debe ocupar hasta 8 MB." }
            return upload(path.substringAfterLast('/'), bytes).also {
                require(it.url.startsWith("https://") || it.url.startsWith("http://")) { "El servidor no devolvió una dirección válida." }
            }
        }
        val media = send(item.path)
        require(media.kind == item.kind) { "El servidor no reconoció el formato del archivo." }
        val poster = if (item.kind == MediaKind.VIDEO) item.posterPath?.let { send(it).url } else null
        PostMediaItem(media.id, item.kind, media.url, posterUrl = poster, sortOrder = order, altText = altText)
    }
}
