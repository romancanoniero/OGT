package com.onlygoodthings.app.media

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LegacyMediaUrlTest {
    @Test
    fun lasFotosAntiguasUsanElProxyPublico() {
        assertEquals(
            "https://onlygoodthings.lat/media/u/foto.jpg?size=640",
            canonicalMediaUrl(" http://217.216.82.209:19080/media/u/foto.jpg?size=640 "),
        )
        assertEquals(
            "https://onlygoodthings.lat/media/u/video.mp4",
            canonicalMediaUrl("https://217.216.82.209:19080/media/u/video.mp4"),
        )
    }

    @Test
    fun noSeReescribenOtrosHostsNiRutasPrivadas() {
        assertEquals(
            "https://example.com/media/u/foto.jpg",
            canonicalMediaUrl("https://example.com/media/u/foto.jpg"),
        )
        assertEquals(
            "http://217.216.82.209:19080/admin",
            canonicalMediaUrl("http://217.216.82.209:19080/admin"),
        )
        assertEquals(
            "https://onlygoodthings.lat/media/u/actual.jpg",
            canonicalMediaUrl("https://onlygoodthings.lat/media/u/actual.jpg"),
        )
    }

    @Test
    fun unaUrlHistoricaPuedeCargarseComoArchivoRemoto() {
        assertTrue(isRemoteMediaUrl("http://217.216.82.209:19080/media/u/foto.jpg"))
    }
}
