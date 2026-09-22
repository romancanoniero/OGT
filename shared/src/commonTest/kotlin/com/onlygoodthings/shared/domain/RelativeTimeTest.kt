package com.onlygoodthings.shared.domain

import kotlin.test.Test
import kotlin.test.assertEquals

class RelativeTimeTest {
    @Test
    fun etiquetaSegunAntiguedadYMetaSinHuecos() {
        val now = 1_700_000_000_000L
        assertEquals("Ahora", relativeTimeLabel(now - 20_000L, now))
        assertEquals("Hace 12 min", relativeTimeLabel(now - 12 * 60_000L, now))
        assertEquals("Hace 5 h", relativeTimeLabel(now - 5 * 3_600_000L, now))
        assertEquals("Ayer", relativeTimeLabel(now - 30 * 3_600_000L, now))
        assertEquals("Palermo · Hace 2 h", postMetaLine("Palermo", "", now - 2 * 3_600_000L, now))
        assertEquals("Hace 2 h", postMetaLine("  ", "", now - 2 * 3_600_000L, now))
        assertEquals("Palermo · Hace 20 min", postMetaLine("Palermo", "Hace 20 min", 0L, now))
    }
}
