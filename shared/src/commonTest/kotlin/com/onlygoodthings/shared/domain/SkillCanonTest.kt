package com.onlygoodthings.shared.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SkillCanonTest {

    @Test
    fun oficioDePersonaACategoria() {
        assertEquals("Albañilería", SkillCanon.canonicalLabel("albañil"))
        assertEquals("Albañilería", SkillCanon.canonicalLabel("Albañileria"))
        assertEquals("Carpintería", SkillCanon.canonicalLabel("carpintero"))
        assertEquals("Carpintería", SkillCanon.canonicalLabel("Carpintera"))
        assertEquals("Plomería", SkillCanon.canonicalLabel("plomero"))
        assertEquals("Electricidad", SkillCanon.canonicalLabel("electricista"))
        assertEquals("Jardinería", SkillCanon.canonicalLabel("jardinero"))
        assertEquals("Costura", SkillCanon.canonicalLabel("costurera"))
    }

    @Test
    fun mismaCategoriaAunqueDigaLaPersona() {
        assertTrue(SkillCanon.sameTrade("albañil", "Albañilería"))
        assertTrue(SkillCanon.sameTrade("carpintero", "carpinteria"))
    }

    @Test
    fun buscaPorAliasOPrefijo() {
        assertTrue(SkillCanon.matchesQuery("alba", "Albañilería"))
        assertTrue(SkillCanon.matchesQuery("jardinero", "Jardinería"))
        assertTrue(SkillCanon.matchesQuery("costura", "Costura"))
    }

    @Test
    fun textoLibreQuedaEnTitulo() {
        assertEquals("Clases de Guitarra", SkillCanon.canonicalLabel("clases de guitarra"))
    }
}
