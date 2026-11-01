package com.onlygoodthings.app.ui.screens

import io.ktor.http.parseQueryString
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class InvitationContentTest {
    @Test fun whatsappPreservesUnicodeAndThePublicSponsorLink() {
        val uri = whatsappInvitationUri(sponsorInvitationText)
        assertTrue(uri.startsWith("https://wa.me/?"))
        val query = parseQueryString(uri.substringAfter('?'))
        assertEquals(setOf("text"), query.names())
        assertEquals(sponsorInvitationText, query["text"])
        assertTrue(query["text"]!!.contains(sponsorInvitationUrl))
    }

    @Test fun emailPreservesTheMessageWithoutCreatingExtraRecipientsOrParameters() {
        val text = "Una invitación de Ana & Luis + OGT\n" + sponsorInvitationUrl + "\n&cc=nadie@example.test"
        val uri = emailInvitationUri("Invitación: Only Good Things", text)
        assertTrue(uri.startsWith("mailto:?"))
        val query = parseQueryString(uri.substringAfter('?'))
        assertEquals(setOf("subject", "body"), query.names())
        assertEquals("Invitación: Only Good Things", query["subject"])
        assertEquals(text, query["body"])
    }
}
