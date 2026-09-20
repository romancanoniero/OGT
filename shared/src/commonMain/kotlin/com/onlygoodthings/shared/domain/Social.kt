package com.onlygoodthings.shared.domain

import kotlinx.serialization.Serializable

@Serializable
enum class AuthorKind {
    USER,
    COMPANY,
}

@Serializable
data class SocialPost(
    val id: String,
    val authorKind: AuthorKind,
    val authorId: String,
    val authorName: String,
    val authorPhotoUrl: String?,
    val body: String,
    val mediaUrls: List<String>,
    val impactCount: Int,
    val commentCount: Int,
    val isStory: Boolean,
    val createdAtEpochMs: Long,
    val viewerHasImpacted: Boolean = false,
    val topic: String = "",
    val discovery: Boolean = false,
    val protagonistUserId: String? = null,
    val media: List<PostMediaItem> = emptyList(),
    val sourceUrl: String? = null,
)

@Serializable
data class SocialComment(
    val id: String,
    val postId: String,
    val authorUserId: String,
    val authorName: String,
    val parentCommentId: String?,
    val body: String,
    val createdAtEpochMs: Long,
    val replies: List<SocialComment> = emptyList(),
)

/**
 * Nodo `ogt/social/posts/{id}`: contadores siempre, cuerpo si el post es nuevo.
 * Así el socket puede insertar un posteo, no solo actualizar números.
 */
@Serializable
data class SocialLiveCounters(
    val id: String,
    val commentCount: Int,
    val impactCount: Int,
    val heartCount: Int = 0,
    val authorUserId: String? = null,
    val body: String? = null,
    val tag: String? = null,
    val place: String? = null,
    val createdAtEpochMs: Long = 0L,
    val listingId: String? = null,
    val honoreeName: String? = null,
) {
    val canIngest: Boolean
        get() = !authorUserId.isNullOrBlank() && !body.isNullOrBlank()
}
