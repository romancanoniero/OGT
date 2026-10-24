package com.onlygoodthings.shared.domain

import kotlinx.serialization.Serializable

/** Pieza de un post, como en Instagram: foto o video, en orden. */
@Serializable
enum class MediaKind {
    IMAGE,
    VIDEO,
}

/** Instagram: 1 a 10 piezas; al menos una foto o un video. */
object PostMediaRules {
    const val MIN_ITEMS = 1
    const val MAX_ITEMS = 10

    fun isValid(count: Int): Boolean = count in MIN_ITEMS..MAX_ITEMS
}

/**
 * Marco de la foto que ve el vecino en la tarjeta.
 * App: `aspectRatio(POST_FRAME_RATIO)`. Web: `.post.open .media-slot` en 16 / 9.
 */
const val POST_FRAME_RATIO = 16f / 9f

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
)

/** Respuesta de `POST /api/v1/media/upload`. El archivo ya está en disco de la API. */
@Serializable
data class UploadedMedia(
    val id: String,
    val kind: MediaKind,
    val url: String,
    val filename: String,
)
