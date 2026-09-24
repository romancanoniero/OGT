package com.onlygoodthings.shared.feed

import com.onlygoodthings.shared.domain.FeedEventKind
import com.onlygoodthings.shared.domain.FeedMode
import com.onlygoodthings.shared.domain.FeedShowReason
import com.onlygoodthings.shared.domain.PostPlacement
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
    fun loMasNuevoVaPrimeroAunqueNoSeaDelGrafo() {
        val stranger = post("x", "ana", hoursAgo = 1, impact = 10)
        val friend = post("y", "sofia", hoursAgo = 10, impact = 10)
        val ranked = FeedRanker.rank(
            candidates = listOf(stranger, friend),
            viewer = ViewerContext("me", followedIds = setOf("sofia")),
            mode = FeedMode.HOME,
            nowEpochMs = now,
        )
        assertEquals(listOf("x", "y"), ranked.map { it.postId })
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
    fun homeEsEstrictamenteCronologico() {
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
    }

    @Test
    fun laFechaGanaALosClapsYAloLogrado() {
        val claps = post("claps", "ana", hoursAgo = 1, impact = 250, achieved = 0)
        val done = post("done", "sofia", hoursAgo = 8, impact = 0, achieved = 3)
        val ranked = FeedRanker.rank(
            candidates = listOf(claps, done),
            viewer = ViewerContext("me", followedIds = emptySet()),
            mode = FeedMode.HOME,
            nowEpochMs = now,
        )
        assertEquals(listOf("claps", "done"), ranked.map { it.postId })
    }

    @Test
    fun promocionadoQuedaEnSuFecha() {
        val promo = post("promo", "ana", hoursAgo = 1, impact = 200, achieved = 8, placement = PostPlacement.PROMOTED)
        val organic = post("og", "sofia", hoursAgo = 3, impact = 2, achieved = 1)
        val ranked = FeedRanker.rank(
            candidates = listOf(promo, organic),
            viewer = ViewerContext("me", followedIds = emptySet()),
            mode = FeedMode.HOME,
            nowEpochMs = now,
        )
        assertEquals(listOf("promo", "og"), ranked.map { it.postId })
        assertEquals(FeedShowReason.PROMOTED, ranked.first { it.postId == "promo" }.reason)
    }

    @Test
    fun pedidoDeUnSeguidoQuePuedoCubrirSaleAntes() {
        val fresco = post("foto", "ana", hoursAgo = 1)
        val pedido = post("trueque", "sofia", hoursAgo = 20, topic = "Trueque", needTagId = "albanil")
        val ranked = FeedRanker.rank(
            candidates = listOf(fresco, pedido),
            viewer = ViewerContext("me", followedIds = setOf("sofia"), offeredTagIds = setOf("albanil")),
            mode = FeedMode.HOME,
            nowEpochMs = now,
        )
        assertEquals(listOf("trueque", "foto"), ranked.map { it.postId })
        assertEquals(FeedShowReason.SKILL_MATCH, ranked.first().reason)
    }

    @Test
    fun adsNoEntranAlHome() {
        val ad = post("ad", "ana", placement = PostPlacement.AD)
        val organic = post("og", "sofia")
        val ranked = FeedRanker.rank(
            candidates = listOf(ad, organic),
            viewer = ViewerContext("me", followedIds = emptySet()),
            mode = FeedMode.HOME,
            nowEpochMs = now,
        )
        assertEquals(listOf("og"), ranked.map { it.postId })
    }

    private fun post(
        id: String,
        author: String,
        topic: String = "Huerta",
        hoursAgo: Number = 2,
        impact: Int = 10,
        achieved: Int = 0,
        placement: PostPlacement = PostPlacement.ORGANIC,
        needTagId: String? = null,
    ) = FeedCandidate(
        postId = id,
        authorKey = author,
        authorUserId = author,
        protagonistUserId = author,
        topic = topic,
        createdAtEpochMs = now - (hoursAgo.toDouble() * 3_600_000).toLong(),
        impactCount = impact,
        commentCount = 2,
        achievedCount = achieved,
        placement = placement,
        needTagId = needTagId,
    )
}
