package com.onlygoodthings.shared.domain

import kotlinx.serialization.Serializable

@Serializable
data class PostHashtag(val key: String, val label: String, val categoryId: String? = null) {
    val interestKey: String get() = categoryId?.let { "category:$it" } ?: "tag:$key"
    val display: String get() = "#" + label.filter { it.isLetterOrDigit() || it=='_' }
}

/** Canonical keys are shared by Android, iOS, web and the backend. */
object Hashtags {
    const val MAX_TAGS = 8
    const val MAX_LENGTH = 40

    fun key(raw: String): String {
        val text = raw.trim().removePrefix("#")
        require(text.isNotEmpty() && text.length <= 60 && text.all { it.isLetterOrDigit() || it == '_' || it.isWhitespace() }) {
            "Usá letras, números y guion bajo en los hashtags."
        }
        return foldMention(text).filter { it.isLetterOrDigit() || it == '_' }.also {
            require(it.length in 2..MAX_LENGTH && it.any(Char::isLetter)) { "El hashtag debe tener entre 2 y 40 caracteres e incluir letras." }
        }
    }

    fun free(raw: String): PostHashtag = PostHashtag(key(raw), raw.trim().removePrefix("#").filterNot(Char::isWhitespace))

    /** Historical category names may contain punctuation or exceed hashtag length. */
    fun catalogKey(label:String,id:String):String {
        val compact=foldMention(label).filter { it.isLetterOrDigit() || it=='_' }
        val suffix=id.filter(Char::isLetterOrDigit).takeLast(9)
        val candidate=when {
            compact.length<2 || compact.none(Char::isLetter) -> "tema_$suffix"
            compact.length>MAX_LENGTH -> compact.take(30)+"_"+suffix
            else -> compact
        }
        return key(candidate)
    }

    fun legacy(topic: String, categoryId: String? = null): List<PostHashtag> =
        if(topic.isBlank())emptyList() else listOf(PostHashtag(catalogKey(topic,categoryId ?: "legacy"),topic,categoryId))
}

@Serializable
data class PublicationDraft(
    val body: String,
    val hashtags: List<String>,
    val media: List<PostMediaItem>,
    val requestId: String,
    val honoreeFullName: String? = null,
    val protagonistUserId: String? = null,
    val participantUserIds: List<String> = emptyList(),
    val invitations: List<PostInvitationDraft> = emptyList(),
    val attributionUrl: String? = null,
    val publicationKind: String = "POST",
    val place: String? = null,
)
