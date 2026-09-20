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
)
