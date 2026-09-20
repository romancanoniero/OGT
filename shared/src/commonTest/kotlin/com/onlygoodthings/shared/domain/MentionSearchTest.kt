package com.onlygoodthings.shared.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MentionSearchTest {
    private val pool = listOf(
        MentionCandidate("c", "Camila T.", "camila.t"),
        MentionCandidate("s", "Sofía M.", "sofia.m"),
        MentionCandidate("r", "Carlos R.", "carlos.r"),
        MentionCandidate("g", "Carlos Gutiérrez", "carlos.gutierrez"),
        MentionCandidate("m", "Mariana Cordero", "mariana.cordero"),
    )

    @Test
    fun handleDesdeNombre() {
        assertEquals("camila.t", mentionHandle("Camila T."))
        assertEquals("mariana.cordero", mentionHandle("Mariana Cordero"))
        assertEquals("sofia.m", mentionHandle("Sofía M."))
    }

    @Test
    fun arrobaYAcentoNoImportan() {
        val hits = searchMentions("@Cám", pool)
        assertEquals("camila.t", hits.first().handle)
    }

    @Test
    fun nickQueEmpiezaGanaAlNombre() {
        val hits = searchMentions("carlos", pool)
        assertEquals(listOf("carlos.gutierrez", "carlos.r"), hits.map { it.handle })
    }

    @Test
    fun vacioDevuelveSugeridos() {
        assertEquals(3, searchMentions("", pool, limit = 3).size)
        assertTrue(searchMentions("zzz", pool).isEmpty())
    }

    @Test
    fun nombrarSinInvitarNoQuedaPendiente() {
        val hit = namedMention("Don Héctor")
        assertEquals("Don Héctor", hit.displayName)
        assertTrue(hit.namedOnly)
        assertTrue(!hit.pending)
    }
}
