package com.onlygoodthings.shared.feed

import com.onlygoodthings.shared.domain.FeedEventKind
import com.onlygoodthings.shared.domain.FeedMode
import com.onlygoodthings.shared.domain.FeedShowReason
import com.onlygoodthings.shared.domain.FeedTopicFamily
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FeedDecisionTest {
    private val now = 1_000_000_000_000L

    @Test
    fun elLikeAgrupaPorFamiliaNoPorStringExacto() {
        val events = listOf(
            FeedEventSignal("a", "x", "Adopción", FeedEventKind.CLAP),
            FeedEventSignal("b", "x", "Rescate animal", FeedEventKind.CLAP),
            FeedEventSignal("c", "x", "Huerta barrial", FeedEventKind.COMMENT),
        )
        val profile = FeedInterestProfile.from(events)
        assertTrue(profile.weight(FeedTopicFamily.PETS) > profile.weight(FeedTopicFamily.COMMUNITY))
        assertTrue(FeedTopicFamily.PETS in profile.chosen)
    }

    @Test
    fun elFiltroDejaSoloEsaFamiliaAunqueNoLaHayaElegido() {
        val pets = post("perro", "ana", topic = "Adopción")
        val food = post("olla", "bruno", topic = "Comedor")
        val ranked = FeedRanker.rank(
            candidates = listOf(pets, food),
            viewer = ViewerContext("me", followedIds = emptySet()),
            mode = FeedMode.HOME,
            nowEpochMs = now,
            family = FeedTopicFamily.FOOD,
        )
        assertEquals(listOf("olla"), ranked.map { it.postId })
        assertTrue(ranked.all { it.reason == FeedShowReason.FILTER })
    }

    @Test
    fun homeSinFiltroSigueCronologicoYMarcaExploracion() {
        val events = (1..8).map { FeedEventSignal("p$it", "x", "Adopción", FeedEventKind.CLAP) }
        val pets = (1..6).map { post("pet$it", "ana", topic = "Adopción", hoursAgo = it) }
        val food = post("olla", "bruno", topic = "Comedor", hoursAgo = 2, impact = 1)
        val ranked = FeedRanker.rank(
            candidates = pets + food,
            viewer = ViewerContext("me", followedIds = emptySet(), events = events),
            mode = FeedMode.HOME,
            nowEpochMs = now,
            limit = 10,
        )
        assertEquals("pet1", ranked.first().postId)
        assertTrue(ranked.zipWithNext().all { (a, b) -> a.createdAtEpochMs >= b.createdAtEpochMs })
        assertEquals(FeedShowReason.EXPLORE, ranked.first { it.postId == "olla" }.reason)
    }

    @Test
    fun followingRespetaGrafoYPuedeFiltrar() {
        val friend = post("amigo", "sofia", topic = "Comedor")
        val stranger = post("otro", "ana", topic = "Comedor")
        val tree = post("arbol", "sofia", topic = "Huerta barrial")
        val following = FeedRanker.rank(
            candidates = listOf(friend, stranger, tree),
            viewer = ViewerContext("me", followedIds = setOf("sofia")),
            mode = FeedMode.FOLLOWING,
            nowEpochMs = now,
            family = FeedTopicFamily.FOOD,
        )
        assertEquals(listOf("amigo"), following.map { it.postId })
    }

    private fun post(
        id: String,
        author: String,
        topic: String,
        hoursAgo: Number = 2,
        impact: Int = 10,
    ) = FeedCandidate(
        postId = id,
        authorKey = author,
        authorUserId = author,
        protagonistUserId = author,
        topic = topic,
        createdAtEpochMs = now - (hoursAgo.toDouble() * 3_600_000).toLong(),
        impactCount = impact,
        commentCount = 2,
    )
}
