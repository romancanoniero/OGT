package com.onlygoodthings.shared.feed

import com.onlygoodthings.shared.domain.FeedEventKind
import com.onlygoodthings.shared.domain.FeedMode
import com.onlygoodthings.shared.domain.FeedShowReason
import com.onlygoodthings.shared.domain.FeedTopicFamily
import kotlinx.serialization.Serializable

/** Consulta de decisión: tubo + filtro opcional de familia. */
@Serializable
data class FeedDecisionQuery(
    val mode: FeedMode = FeedMode.HOME,
    val family: FeedTopicFamily? = null,
)

/**
 * Perfil de intereses a partir de aplausos y otras señales positivas.
 * `chosen` son las familias que el usuario ya eligió con la conducta.
 */
data class FeedInterestProfile(
    val weights: Map<FeedTopicFamily, Double>,
    val chosen: Set<FeedTopicFamily>,
) {
    fun weight(family: FeedTopicFamily): Double = weights[family] ?: 0.0

    fun isChosen(family: FeedTopicFamily): Boolean = family in chosen

    /** Familias que el filtro debe ofrecer para ver si le importan. */
    fun unexplored(): Set<FeedTopicFamily> =
        FeedTopicFamily.entries.filter { it != FeedTopicFamily.OTHER && it !in chosen }.toSet()

    companion object {
        const val CHOSEN_THRESHOLD = 0.22

        private val Positive = setOf(
            FeedEventKind.CLAP,
            FeedEventKind.HEART,
            FeedEventKind.COMMENT,
            FeedEventKind.PROFILE_TAP,
            FeedEventKind.SHARE,
            FeedEventKind.DWELL,
        )

        fun from(events: List<FeedEventSignal>): FeedInterestProfile {
            val relevant = events.filter { it.kind in Positive }
            if (relevant.isEmpty()) {
                return FeedInterestProfile(emptyMap(), emptySet())
            }
            val counts = relevant.groupingBy { feedTopicFamily(it.topic) }.eachCount()
            val total = relevant.size.toDouble()
            val weights = FeedTopicFamily.entries.associateWith { family ->
                (counts[family] ?: 0) / total
            }
            val chosen = weights.filter { it.value >= CHOSEN_THRESHOLD }.keys
            return FeedInterestProfile(weights, chosen)
        }
    }
}

fun feedTopicFamily(tag: String, sourceUrl: String? = null): FeedTopicFamily {
    val t = tag.trim().lowercase()
    return when {
        t.contains("perdid") || t in PET_TAGS -> FeedTopicFamily.PETS
        t in FOOD_TAGS -> FeedTopicFamily.FOOD
        t in OCEAN_TAGS -> FeedTopicFamily.OCEAN
        t in COMMUNITY_TAGS -> FeedTopicFamily.COMMUNITY
        !sourceUrl.isNullOrBlank() && !sourceUrl.startsWith("ogt://") -> FeedTopicFamily.NEWS
        else -> FeedTopicFamily.OTHER
    }
}

fun FeedShowReason.labelEs(): String = when (this) {
    FeedShowReason.GRAPH -> "De tu gente"
    FeedShowReason.AFFINITY -> "Por lo que te gusta"
    FeedShowReason.EXPLORE -> "Para que explores"
    FeedShowReason.FILTER -> "Filtro"
    FeedShowReason.PROMOTED -> "Promocionado"
}

private val PET_TAGS = setOf(
    "adopción", "rescate animal", "tránsito animal", "mascotas", "fauna", "mascota perdida",
    "cría", "cria", "llegó a casa", "llego a casa", "historia tierna", "ternura",
)

private val FOOD_TAGS = setOf(
    "comedor", "merienda comunitaria", "merienda",
)

private val OCEAN_TAGS = setOf(
    "océano", "oceano", "limpieza de playa",
)

private val COMMUNITY_TAGS = setOf(
    "huerta comunitaria", "huerta barrial", "huerta",
    "compost comunitario", "compost vecinal", "compost",
    "reforestación", "cuidado comunitario", "cuidado barrial",
    "trueque", "energía solar", "energia solar",
    "en vida", "post mortem", "postmortem", "póstumo", "postumo",
    "enseñanza", "ensenanza", "anécdota", "anecdota", "gracias", "homenaje",
    "intercambio de ayuda", "parking colaborativo", "ayuda vecinal",
)
