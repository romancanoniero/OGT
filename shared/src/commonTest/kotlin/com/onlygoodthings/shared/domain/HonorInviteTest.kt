package com.onlygoodthings.shared.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class HonorInviteTest {
    @Test
    fun linkYToken() {
        assertEquals("https://onlygoodthings.lat/h/ana.p1a2b3", honorClaimLink("ana.p1a2b3"))
        assertEquals("ana.p1a2b3", parseHonorToken("https://onlygoodthings.app/h/ana.p1a2b3"))
        assertEquals("ana.p1a2b3", parseHonorToken("onlygoodthings.app/h/ana.p1a2b3?src=wa"))
        assertEquals("ana.p1a2b3", parseHonorToken("ana.p1a2b3"))
        assertEquals("ana.p1a2b3", parseHonorToken("ogt://h/ana.p1a2b3"))
        assertNull(parseHonorToken(""))
        assertNull(parseHonorToken("ab"))
    }

    @Test
    fun deepLinkDePost() {
        assertEquals("ogt://p/post-taller", postDeepLink("post-taller"))
        assertEquals("post-taller", parsePostDeepLink("ogt://p/post-taller"))
        assertEquals("post-taller", parsePostDeepLink("https://onlygoodthings.app/p/post-taller?src=push"))
        assertNull(parsePostDeepLink("ogt://h/ana.p1a2b3"))
        assertNull(parsePostDeepLink(""))
    }

    @Test
    fun contactoMailYTelefono() {
        assertTrue(honorContactOk(HonorChannel.EMAIL, " Ana@Casa.ORG "))
        assertEquals("ana@casa.org", normalizeHonorContact(HonorChannel.EMAIL, " Ana@Casa.ORG "))
        assertTrue(honorContactOk(HonorChannel.WHATSAPP, "+54 11 5555-1234"))
        assertTrue(honorContactOk(HonorChannel.WHATSAPP, ""))
        assertEquals("whatsapp", normalizeHonorContact(HonorChannel.WHATSAPP, ""))
        assertEquals("541155551234", normalizeHonorContact(HonorChannel.WHATSAPP, "+54 11 5555-1234"))
        assertTrue(honorContactOk(HonorChannel.WHATSAPP, "11 5555-1234"))
        assertEquals("541155551234", normalizeHonorContact(HonorChannel.SMS, "+54 11 5555-1234"))
        assertFalse(honorContactOk(HonorChannel.SMS, "123"))
        assertFalse(honorContactOk(HonorChannel.EMAIL, "sin-arroba"))
    }

    @Test
    fun textoDeInvitacionLlevaLink() {
        val text = honorShareText("Mariana", "Ana", "ana.token1")
        assertTrue(text.contains("Ana"))
        assertTrue(text.contains("onlygoodthings.lat/h/ana.token1"))
    }

    @Test
    fun landingYTiendasParaInstalar() {
        val token = "ana.p1a2b3"
        val play = honorPlayStoreUrl(token)
        assertTrue(play.contains("play.google.com/store/apps/details?id=com.onlygoodthings.app"))
        assertTrue(play.contains("referrer="))
        assertEquals(token, parseHonorReferrer("honor=$token"))
        assertEquals(token, parseHonorReferrer(percentDecode(play.substringAfter("referrer="))))
        assertEquals(token, parseHonorClipboard("https://onlygoodthings.lat/h/$token"))
        assertEquals(token, parseHonorClipboard("ogt://h/$token"))
        assertNull(parseHonorClipboard("whatsapp"))
        val html = honorLandingHtml("Ana <script>", "Mariana", token)
        assertTrue(html.contains("ogt://h/$token"))
        assertTrue(html.contains("intent://h/$token#Intent;scheme=ogt"))
        assertFalse(html.contains("document.hidden"))
        assertTrue(html.contains("play.google.com"))
        assertTrue(html.contains("apps.apple.com"))
        assertTrue(html.contains("Ana &lt;script&gt;"))
        assertFalse(html.contains("<script>alert"))
        assertTrue(assetLinksJson(listOf(OgtAndroidDebugSha256)).contains(OgtAndroidDebugSha256))
        assertTrue(appleAppSiteAssociationJson().contains("/h/*"))
    }
}
