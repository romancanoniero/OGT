package com.onlygoodthings.shared.domain

import kotlin.test.Test
import kotlin.test.assertTrue

class CommunityRulesTest {
    @Test
    fun elRioEsDeHechosYNoDeConversion() {
        val texto = (listOf(CommunityRules.preamble) + CommunityRules.sections.flatMap { it.points }).joinToString(" ")
        assertTrue(texto.contains("No es un púlpito"))
        assertTrue(texto.contains("No a convertir"))
        assertTrue(texto.contains("predicar") || texto.contains("proselitismo") || texto.contains("reclutar"))
        assertTrue(CommunityRules.sections.size >= 6)
    }
}
