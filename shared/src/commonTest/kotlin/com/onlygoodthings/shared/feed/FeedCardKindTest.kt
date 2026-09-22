package com.onlygoodthings.shared.feed

import com.onlygoodthings.shared.domain.AuthorKind
import com.onlygoodthings.shared.domain.FeedCardKind
import com.onlygoodthings.shared.domain.canEditSocialPost
import com.onlygoodthings.shared.domain.feedCardKind
import com.onlygoodthings.shared.domain.isAnecdoteShare
import com.onlygoodthings.shared.domain.canRsvpToGathering
import com.onlygoodthings.shared.domain.formatGatheringWhen
import com.onlygoodthings.shared.domain.gatheringWhenWhere
import com.onlygoodthings.shared.domain.isGatheringPost
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FeedCardKindTest {
    @Test
    fun avisoOperativoMandaSobreElTagDeHistoria() {
        assertEquals(FeedCardKind.LOST_PET, feedCardKind("Adopción", listingKind = "LOST"))
        assertEquals(FeedCardKind.ADOPTION, feedCardKind("Mascotas", listingKind = "ADOPTION"))
    }

    @Test
    fun noticiaDeAdopcionSigueSiendoHistoria() {
        assertEquals(
            FeedCardKind.PET_STORY,
            feedCardKind("Adopción", sourceUrl = "https://ejemplo.test/nota"),
        )
        assertEquals(FeedCardKind.PET_STORY, feedCardKind("Rescate animal"))
        assertEquals(FeedCardKind.PET_STORY, feedCardKind("Tránsito animal"))
    }

    @Test
    fun ternuraYHomenajeTienenFichaPropia() {
        assertEquals(FeedCardKind.TERNURA, feedCardKind("Cría"))
        assertEquals(FeedCardKind.TERNURA, feedCardKind("Llegó a casa"))
        assertEquals(FeedCardKind.TERNURA, feedCardKind("Historia tierna"))
        assertEquals(FeedCardKind.TERNURA, feedCardKind("Ternura"))
        assertEquals(FeedCardKind.HOMENAJE, feedCardKind("En vida"))
        assertEquals(FeedCardKind.HOMENAJE, feedCardKind("Post mortem"))
        assertEquals(FeedCardKind.HOMENAJE, feedCardKind("Enseñanza"))
        assertEquals(FeedCardKind.HOMENAJE, feedCardKind("Anécdota"))
        assertEquals(FeedCardKind.HOMENAJE, feedCardKind("Gracias"))
        assertTrue(isAnecdoteShare("Anécdota", sourceUrl = "ogt://p/post-homenaje-roberto"))
        assertTrue(isAnecdoteShare("Homenaje", postId = "post-anecdote-1"))
        assertFalse(isAnecdoteShare("Post mortem"))
    }

    @Test
    fun perdidoPorTagOListado() {
        assertEquals(FeedCardKind.LOST_PET, feedCardKind("Mascota perdida"))
        assertEquals(FeedCardKind.LOST_PET, feedCardKind("Perdidos", listingKind = "LOST"))
    }

    @Test
    fun elAutorEditaSalvoNoticiaEditorial() {
        assertTrue(canEditSocialPost(true, AuthorKind.USER, "Comedor"))
        assertTrue(canEditSocialPost(true, AuthorKind.USER, "Ternura"))
        assertTrue(canEditSocialPost(true, AuthorKind.USER, "Mascota perdida", listingKind = "LOST"))
        assertFalse(canEditSocialPost(false, AuthorKind.USER, "Comedor"))
        assertFalse(canEditSocialPost(true, AuthorKind.COMPANY, "Comedor"))
        assertFalse(canEditSocialPost(true, AuthorKind.USER, "Comedor", sourceUrl = "https://diario.test"))
        assertTrue(canEditSocialPost(true, AuthorKind.USER, "Anécdota", sourceUrl = "ogt://p/homenaje"))
    }

    @Test
    fun noticiaYComunidad() {
        assertEquals(FeedCardKind.NEWS, feedCardKind("Comedor", sourceUrl = "https://diario.test"))
        assertEquals(FeedCardKind.COMMUNITY, feedCardKind("Huerta comunitaria"))
        assertEquals(FeedCardKind.COMMUNITY, feedCardKind("Huerta barrial"))
    }

    @Test
    fun convocatoriaPermiteAsistirYAvisarContactos() {
        assertTrue(isGatheringPost("Huerta comunitaria"))
        assertTrue(isGatheringPost("Huerta barrial"))
        assertTrue(isGatheringPost("Reforestación"))
        assertTrue(isGatheringPost("Limpieza de playa"))
        assertFalse(isGatheringPost("Energía solar"))
        assertFalse(isGatheringPost("Adopción"))
    }

    @Test
    fun asistireSoloSiElEventoNoEmpezo() {
        val now = 1_000_000L
        assertTrue(canRsvpToGathering("Huerta comunitaria", now + 3_600_000L, now))
        assertFalse(canRsvpToGathering("Huerta comunitaria", now, now))
        assertFalse(canRsvpToGathering("Huerta comunitaria", now - 1L, now))
        assertFalse(canRsvpToGathering("Huerta comunitaria", null, now))
        assertFalse(canRsvpToGathering("Energía solar", now + 3_600_000L, now))
    }

    @Test
    fun convocatoriaMuestraFechaYLugar() {
        val tz = TimeZone.of("America/Argentina/Buenos_Aires")
        val now = Instant.parse("2026-09-18T21:00:00Z").toEpochMilliseconds()
        val manana11 = Instant.parse("2026-09-19T14:00:00Z").toEpochMilliseconds()
        assertEquals("Mañana 11:00", formatGatheringWhen(manana11, now, tz))
        assertEquals(
            "Mañana 11:00 · Plaza Serrano",
            gatheringWhenWhere("Plaza Serrano", manana11, now, tz),
        )
    }
}
