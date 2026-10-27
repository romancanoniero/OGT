package com.onlygoodthings.shared.domain

import kotlinx.serialization.Serializable

@Serializable enum class StoryAudience { COMMUNITY, FOLLOWERS }

object StoryRules {
    const val LIFETIME_MS = 24L * 60 * 60 * 1000
    const val MAX_VIDEO_MS = 30_000L
    const val PHOTO_MS = 5_000L
    const val MAX_BYTES = 8 * 1024 * 1024
    const val MAX_CAPTION = 1200
}

@Serializable data class Story(
    val id: String,
    val authorId: String,
    val authorName: String,
    val authorPhotoUrl: String? = null,
    val caption: String,
    val audience: StoryAudience,
    val kind: MediaKind,
    val mediaUrl: String,
    val posterUrl: String? = null,
    val durationMs: Long,
    val createdAtEpochMs: Long,
    val expiresAtEpochMs: Long,
    val seen: Boolean = false,
    val viewCount: Int = 0,
    val status: String = "VISIBLE",
    val frame: StoryFrame? = null,
)

@Serializable data class StoryPage(
    val stories: List<Story>,
    val serverNowEpochMs: Long,
    val nextCursor: String? = null,
)

/** A cached page may never extend server-authorized visibility. */
fun activeStories(stories: List<Story>, serverNowEpochMs: Long): List<Story> =
    stories.filter { it.status == "VISIBLE" && it.expiresAtEpochMs > serverNowEpochMs }

@Serializable data class StoryDraft(
    val id: String,
    val ownerId: String,
    val path: String,
    val kind: MediaKind,
    val posterPath: String? = null,
    val caption: String = "",
    val audience: StoryAudience = StoryAudience.COMMUNITY,
    val prepared: Boolean = false,
    val sourcePath: String? = null,
    val audioMuted: Boolean = false,
    val unmutedPath: String? = null,
    val frame: StoryFrame? = null,
)
