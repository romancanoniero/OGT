package com.onlygoodthings.shared.feed

import com.onlygoodthings.shared.domain.FeedEventKind
import com.onlygoodthings.shared.domain.FeedMode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FeedRankerTest {
    private val now = 1_000_000_000_000L

    @Test
    fun followingSoloDevuelveGrafoYCronologico() {
        val ranked = FeedRanker.rank(
            candidates = listOf(post("extra", "ana", hoursAgo = 1), post("grafo", "sofia", hoursAgo = 10)),
            viewer = ViewerContext("me", followedIds = setOf("sofia")),
            mode = FeedMode.FOLLOWING,
            nowEpochMs = now,
        )
        assertEquals(listOf("grafo"), ranked.map { it.postId })
        assertTrue(ranked.all { !it.discovery })
    }

    @Test
    fun homeIncluyeDescubrimiento() {
        val ranked = FeedRanker.rank(
            candidates = listOf(post("extra", "ana"), post("grafo", "sofia")),
            viewer = ViewerContext("me", followedIds = setOf("sofia")),
            mode = FeedMode.HOME,
            nowEpochMs = now,
        )
        assertEquals(setOf("extra", "grafo"), ranked.map { it.postId }.toSet())
        assertTrue(ranked.first { it.postId == "extra" }.discovery)
        assertTrue(!ranked.first { it.postId == "grafo" }.discovery)
    }

    @Test
    fun elGrafoPesaMasQueUnDesconocidoIdentico() {
        val stranger = post("x", "ana", hoursAgo = 2, impact = 10)
        val friend = post("y", "sofia", hoursAgo = 2, impact = 10)
        val ranked = FeedRanker.rank(
            candidates = listOf(stranger, friend),
            viewer = ViewerContext("me", followedIds = setOf("sofia")),
            mode = FeedMode.HOME,
            nowEpochMs = now,
        )
        assertEquals("y", ranked.first().postId)
    }

    @Test
    fun hideSacaElPostDelInventario() {
        val ranked = FeedRanker.rank(
            candidates = listOf(post("a", "ana"), post("b", "sofia")),
            viewer = ViewerContext(
                userId = "me",
                followedIds = emptySet(),
                events = listOf(FeedEventSignal("a", "ana", "Huerta", FeedEventKind.HIDE)),
            ),
            mode = FeedMode.HOME,
            nowEpochMs = now,
        )
        assertEquals(listOf("b"), ranked.map { it.postId })
    }

    @Test
    fun homeOrdenaDeNuevoAViejoAunqueElViejoTengaMasImpacto() {
        val sameAuthor = (1..4).map { post("p$it", "ana", hoursAgo = it.toDouble(), impact = 200 - it) }
        val other = post("otro", "lucas", hoursAgo = 20, impact = 1)
        val ranked = FeedRanker.rank(
            candidates = sameAuthor + other,
            viewer = ViewerContext("me", followedIds = emptySet()),
            mode = FeedMode.HOME,
            nowEpochMs = now,
        )
        assertEquals(listOf("p1", "p2", "p3", "p4", "otro"), ranked.map { it.postId })
    }

    @Test
    fun loNuevoVaAntesAunqueElLikeSeaDeOtroTema() {
        val huerta = post("huerta", "ana", topic = "Huerta", hoursAgo = 8, impact = 5)
        val rse = post("rse", "acme", topic = "RSE", hoursAgo = 1, impact = 5)
        val events = listOf(
            FeedEventSignal("old", "x", "Huerta", FeedEventKind.CLAP),
            FeedEventSignal("old2", "x", "Huerta", FeedEventKind.COMMENT),
        )
        val ranked = FeedRanker.rank(
            candidates = listOf(rse, huerta),
            viewer = ViewerContext("me", followedIds = emptySet(), events = events),
            mode = FeedMode.HOME,
            nowEpochMs = now,
        )
        assertEquals(listOf("rse", "huerta"), ranked.map { it.postId })
    }

    @Test
    fun loMasNuevoGanaSiElRestoEsIgual() {
        val old = post("old", "ana", hoursAgo = 48)
        val fresh = post("fresh", "ana", hoursAgo = 1)
        val ranked = FeedRanker.rank(
            candidates = listOf(old, fresh),
            viewer = ViewerContext("me", followedIds = emptySet()),
            mode = FeedMode.HOME,
            nowEpochMs = now,
        )
        assertEquals("fresh", ranked.first().postId)
        assertTrue(ranked.zipWithNext().all { (a, b) -> a.createdAtEpochMs >= b.createdAtEpochMs })
    }

    private fun post(
        id: String,
        author: String,
        topic: String = "Huerta",
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
