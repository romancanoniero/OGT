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
    val authorPhotoUrl: String? = null,
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
    val placement: PostPlacement = PostPlacement.ORGANIC,
    val achievedCount: Int = 0,
    val empresaQueSuma: Boolean = false,
    val urgency: String? = null,
    val honoreeName: String? = null,
    val anecdotes: List<SocialAnecdote> = emptyList(),
    val needId: String? = null,
    val needLabel: String? = null,
    val giveLabel: String? = null,
    val giveLabels: List<String> = emptyList(),
    val supportCount: Int = 0,
    val viewerSupported: Boolean = false,
    val viewerInvited: Boolean = false,
    val matchesMyOffer: Boolean = false,
)

@Serializable
data class SocialAnecdote(
    val id: String,
    val postId: String,
    val authorUserId: String,
    val authorName: String,
    /** Foto viva de `users.photo_url` al leer. No hay columna en la anécdota. */
    val authorPhotoUrl: String? = null,
    val body: String,
    val sourceUrl: String? = null,
    val sortOrder: Int = 0,
    val createdAtEpochMs: Long = 0L,
    val impactCount: Int = 0,
    val commentCount: Int = 0,
    val heartCount: Int = 0,
    val viewerHasImpacted: Boolean = false,
    val viewerHasHearted: Boolean = false,
    val comments: List<SocialComment> = emptyList(),
)

@Serializable
data class SocialComment(
    val id: String,
    val postId: String,
    val authorUserId: String,
    val authorName: String,
    /** Foto viva de `users.photo_url` al leer. El comentario no guarda una copia. */
    val authorPhotoUrl: String? = null,
    val parentCommentId: String? = null,
    val body: String,
    val createdAtEpochMs: Long,
    val replies: List<SocialComment> = emptyList(),
    val anecdoteId: String? = null,
    val edited: Boolean = false,
    /** VISIBLE sigue en la ficha. REMOVED lo saca sin borrar la fila. */
    val moderationStatus: String = "VISIBLE",
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
    /** Nombre del autor nuevo. Si viene, las pantallas que ya escuchan el post lo pintan sin recargar. */
    val authorName: String? = null,
    /** Foto viva del autor cuando la autoría cambia. No es una copia en el post. */
    val authorPhotoUrl: String? = null,
    val body: String? = null,
    val tag: String? = null,
    val place: String? = null,
    val createdAtEpochMs: Long = 0L,
    val listingId: String? = null,
    val honoreeName: String? = null,
    /** VISIBLE sigue en el feed. REMOVED lo saca de las pantallas que ya lo muestran. */
    val moderationStatus: String = "VISIBLE",
    /** Anécdota que sale del homenaje. Las pantallas ya escuchan este post, no un nodo nuevo. */
    val removedAnecdoteId: String? = null,
    /** Encuadre de la primera foto. Mismo nodo que ya escuchan feed, ficha y Control. */
    val frameX: Double? = null,
    val frameY: Double? = null,
    val frameZoom: Double? = null,
) {
    val canIngest: Boolean
        get() = !authorUserId.isNullOrBlank() && !body.isNullOrBlank()
}
