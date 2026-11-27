package com.onlygoodthings.shared.domain

import kotlinx.serialization.Serializable

/** Pieza de un post, como en Instagram: foto o video, en orden. */
@Serializable
enum class MediaKind {
    IMAGE,
    VIDEO,
}

/** Una publicación admite hasta 20 piezas, en el orden elegido. */
object PostMediaRules {
    const val MIN_ITEMS = 1
    const val MAX_ITEMS = 20
    const val MAX_VIDEO_MS = 120_000L

    fun isValid(count: Int): Boolean = count in MIN_ITEMS..MAX_ITEMS
}

/**
 * Marco del editor de encuadre. Se conserva para los recortes guardados previamente.
 */
const val POST_FRAME_RATIO = 16f / 9f

/** Marco de lectura: la primera pieza fija la altura de todo el carrusel. */
const val POST_READING_DEFAULT_RATIO = 4f / 5f
fun postReadingRatio(width: Int?, height: Int?): Float =
    if (width == null || height == null || width <= 0 || height <= 0) POST_READING_DEFAULT_RATIO
    else postReadingRatio(width.toFloat() / height)
fun postReadingRatio(ratio: Float): Float =
    if (!ratio.isFinite() || ratio <= 0f) POST_READING_DEFAULT_RATIO else ratio.coerceIn(3f / 4f, 1.91f)

/** Dónde queda la foto dentro del marco, en píxeles del marco. */
data class PostFramePlacement(
    val displayWidth: Float,
    val displayHeight: Float,
    val left: Float,
    val top: Float,
    val centerX: Float,
    val centerY: Float,
    val zoom: Float,
)

/**
 * Pone el punto (frameX, frameY) de la foto en el centro del marco y la acerca.
 * 1 cubre el marco sin bandas. El recorte no deja huecos.
 */
fun placePostFrame(
    frameWidth: Float,
    frameHeight: Float,
    imageWidth: Float,
    imageHeight: Float,
    frameX: Float,
    frameY: Float,
    frameZoom: Float,
): PostFramePlacement? {
    if (frameWidth <= 0f || frameHeight <= 0f || imageWidth <= 0f || imageHeight <= 0f) return null
    if (!frameX.isFinite() || !frameY.isFinite() || !frameZoom.isFinite()) return null
    val zoom = frameZoom.coerceIn(1f, 6f)
    val cover = maxOf(frameWidth / imageWidth, frameHeight / imageHeight) * zoom
    val displayWidth = imageWidth * cover
    val displayHeight = imageHeight * cover
    val minX = (frameWidth / (2f * displayWidth)).coerceAtMost(0.5f)
    val maxX = 1f - minX
    val minY = (frameHeight / (2f * displayHeight)).coerceAtMost(0.5f)
    val maxY = 1f - minY
    val x = frameX.coerceIn(minX, maxX)
    val y = frameY.coerceIn(minY, maxY)
    return PostFramePlacement(
        displayWidth = displayWidth,
        displayHeight = displayHeight,
        left = frameWidth / 2f - x * displayWidth,
        top = frameHeight / 2f - y * displayHeight,
        centerX = x,
        centerY = y,
        zoom = zoom,
    )
}

/** Rectángulo del archivo original que entra en el marco 16:9, en píxeles de esa foto. */
data class PostFrameSourceRect(
    val x: Float,
    val y: Float,
    val width: Float,
    val height: Float,
)

/**
 * Recorte del archivo original. El marco de salida es 16:9; el tamaño en píxeles no cambia el rectángulo.
 * Queda dentro de la foto para no inventar bandas.
 */
fun postFrameSourceRect(
    imageWidth: Float,
    imageHeight: Float,
    frameX: Float,
    frameY: Float,
    frameZoom: Float,
): PostFrameSourceRect? {
    val placed = placePostFrame(16f, 9f, imageWidth, imageHeight, frameX, frameY, frameZoom) ?: return null
    val cover = placed.displayWidth / imageWidth
    if (cover <= 0f) return null
    var x = -placed.left / cover
    var y = -placed.top / cover
    var width = 16f / cover
    var height = 9f / cover
    if (x < 0f) {
        width += x
        x = 0f
    }
    if (y < 0f) {
        height += y
        y = 0f
    }
    if (x + width > imageWidth) width = imageWidth - x
    if (y + height > imageHeight) height = imageHeight - y
    if (width <= 0f || height <= 0f) return null
    return PostFrameSourceRect(x = x, y = y, width = width, height = height)
}

@Serializable
data class PostMediaItem(
    val id: String,
    val kind: MediaKind,
    val url: String,
    val posterUrl: String? = null,
    val sortOrder: Int = 0,
    val durationMs: Int? = null,
    val altText: String? = null,
    val assetKey: String? = null,
    /** Punto de la foto que queda al centro del marco 16:9. Null es el centro. */
    val frameX: Double? = null,
    val frameY: Double? = null,
    /** Acercamiento sobre el recorte que ya cubre el marco. 1 no acerca. */
    val frameZoom: Double? = null,
    val pixelWidth: Int? = null,
    val pixelHeight: Int? = null,
)

/**
 * Lo que pinta una pantalla de lectura. Si el original quedó guardado aparte,
 * `url` ya es el JPEG 16:9 y no hay que volver a encuadrar encima.
 */
fun PostMediaItem.forReading(originalUrl: String?): PostMediaItem =
    if (originalUrl.isNullOrBlank()) this
    else copy(frameX = null, frameY = null, frameZoom = null)

/** Respuesta de `POST /api/v1/media/upload`. El archivo ya está en disco de la API. */
@Serializable
data class UploadedMedia(
    val id: String,
    val kind: MediaKind,
    val url: String,
    val filename: String,
)
