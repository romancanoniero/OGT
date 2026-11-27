package com.onlygoodthings.app.platform

import io.ktor.http.Url
import kotlin.test.*

class PostShareAppsTest {
    @Test fun appDraftsEncodeTheTextAndLinkWithoutChoosingARecipientOrSending() {
        val link="https://onlygoodthings.lat/p/qa?x=1&y=2"
        val text="Una historia 🐱 & compañía\n\n$link"
        val whatsapp=Url(assertNotNull(postShareAppUri("whatsapp",text,link)))
        assertEquals("whatsapp",whatsapp.protocol.name);assertEquals(text,whatsapp.parameters["text"])
        assertNull(whatsapp.parameters["phone"])
        val telegram=Url(assertNotNull(postShareAppUri("telegram",text,link)))
        assertEquals(link,telegram.parameters["url"])
        assertEquals("Una historia 🐱 & compañía",telegram.parameters["text"])
        assertNull(postShareAppUri("unknown",text,link))
    }
}
