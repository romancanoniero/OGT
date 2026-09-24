package com.onlygoodthings.shared.domain

import kotlinx.serialization.Serializable

/**
 * Pieza visual del feed. Un aviso operativo no se dibuja como una historia.
 * listingKind manda: las noticias de adopción siguen siendo PET_STORY.
 */
enum class FeedCardKind {
    LOST_PET,
    ADOPTION,
    PET_STORY,
    TERNURA,
    HOMENAJE,
    NEWS,
    COMMUNITY,
}

/** El autor edita su post; una noticia editorial o de empresa no se reescribe acá. */
fun canEditSocialPost(
    isAuthor: Boolean,
    authorKind: AuthorKind,
    tag: String,
    listingKind: String? = null,
    sourceUrl: String? = null,
): Boolean {
    if (!isAuthor || authorKind == AuthorKind.COMPANY) return false
    val kind = feedCardKind(tag, listingKind, sourceUrl)
    if (kind == FeedCardKind.NEWS && !sourceUrl.isNullOrBlank() && !sourceUrl.startsWith("ogt://")) {
        return false
    }
    return true
}

fun feedCardKind(
    tag: String,
    listingKind: String? = null,
    sourceUrl: String? = null,
): FeedCardKind {
    val kind = listingKind?.uppercase()
    if (kind == "LOST") return FeedCardKind.LOST_PET
    if (kind == "ADOPTION") return FeedCardKind.ADOPTION
    val t = tag.trim().lowercase()
    if (t.contains("perdid")) return FeedCardKind.LOST_PET
    if (t in TERNURA_TAGS) return FeedCardKind.TERNURA
    if (t in HOMENAJE_TAGS) return FeedCardKind.HOMENAJE
    if (t in PET_STORY_TAGS) return FeedCardKind.PET_STORY
    if (!sourceUrl.isNullOrBlank()) return FeedCardKind.NEWS
    return FeedCardKind.COMMUNITY
}

fun isTernuraPost(tag: String): Boolean = tag.trim().lowercase() in TERNURA_TAGS

fun isHomenajePost(tag: String): Boolean = tag.trim().lowercase() in HOMENAJE_TAGS

/**
 * Un momento puntual del homenaje, no la ficha completa.
 * El link sigue abriendo el homenaje: sin eso se pierde a quién se honra.
 */
fun isAnecdoteShare(
    tag: String,
    sourceUrl: String? = null,
    postId: String = "",
    parentPostId: String? = null,
): Boolean {
    if (!parentPostId.isNullOrBlank()) return true
    if (postId.startsWith("post-anecdote-")) return true
    return isHomenajePost(tag) && sourceUrl?.startsWith("ogt://p/") == true
}

fun anecdoteParentPostId(parentPostId: String?, sourceUrl: String?): String? =
    parentPostId?.takeIf { it.isNotBlank() } ?: sourceUrl?.let(::parsePostDeepLink)

/** Convocatoria a la que alguien de la comunidad puede decir “asistiré” y avisar a sus contactos. */
fun isGatheringPost(tag: String): Boolean = tag.trim().lowercase() in GATHERING_TAGS

/** Asistiré solo si hay horario de inicio y el evento todavía no empezó. */
fun canRsvpToGathering(
    tag: String,
    eventStartsAtEpochMs: Long?,
    nowEpochMs: Long,
): Boolean {
    if (!isGatheringPost(tag)) return false
    val start = eventStartsAtEpochMs ?: return false
    return nowEpochMs < start
}

private val GATHERING_TAGS = setOf(
    "huerta comunitaria",
    "huerta barrial",
    "huerta",
    "reforestación",
    "reforestacion",
    "merienda comunitaria",
    "merienda",
    "comedor",
    "limpieza de playa",
    "compost comunitario",
    "compost vecinal",
    "trueque",
)

private val PET_STORY_TAGS = setOf(
    "adopción",
    "rescate animal",
    "tránsito animal",
    "mascotas",
    "fauna",
)

private val TERNURA_TAGS = setOf(
    "cría",
    "cria",
    "llegó a casa",
    "llego a casa",
    "historia tierna",
    "ternura",
)

private val HOMENAJE_TAGS = setOf(
    "en vida",
    "post mortem",
    "postmortem",
    "póstumo",
    "postumo",
    "enseñanza",
    "ensenanza",
    "anécdota",
    "anecdota",
    "gracias",
    "homenaje",
)

/** Familias de contenido. El like agrupa acá, no en el string exacto del tag. */
@Serializable
enum class FeedTopicFamily {
    PETS,
    COMMUNITY,
    FOOD,
    OCEAN,
    NEWS,
    OTHER,
}

/** Por qué este post quedó en la página. */
@Serializable
enum class FeedShowReason {
    GRAPH,
    AFFINITY,
    EXPLORE,
    FILTER,
    PROMOTED,
    SKILL_MATCH,
}

/** Home rankeado vs tubo cronológico del grafo. */
@Serializable
enum class FeedMode {
    HOME,
    FOLLOWING,
}

@Serializable
enum class PostPersonRole {
    AUTHOR,
    PROTAGONIST,
    PARTICIPANT,
}

/** Acciones que alimentan el ranking. Un comentario no es una mención en el post. */
@Serializable
enum class FeedEventKind {
    IMPRESSION,
    DWELL,
    CLAP,
    HEART,
    COMMENT,
    PROFILE_TAP,
    SHARE,
    HIDE,
    SKIP,
}
