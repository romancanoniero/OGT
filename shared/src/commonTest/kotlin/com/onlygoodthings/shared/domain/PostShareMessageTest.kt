package com.onlygoodthings.shared.domain

import kotlin.test.*

class PostShareMessageTest {
    @Test fun theLinkSurvivesLongCaptionsAndDoesNotRepeat() {
        val link=postPublicLink("qa-post")
        val body=postShareMessage("Una historia larga ".repeat(100)+link,link)
        assertTrue(body.startsWith(link+"\n\n"))
        assertEquals(800,body.length)
        assertEquals(1,body.split(link).size-1)
        assertEquals(link,postShareMessage(link,link))
    }
    @Test fun existingShortSharesAndLocalQaLinksRemainUsable() {
        val link="http://localhost:19082/#/p/qa"
        assertEquals(link+"\n\nUna historia",postShareMessage("Una historia\n\n$link",link))
        assertFailsWith<IllegalArgumentException> { postShareMessage("Texto","javascript:bad") }
        assertFailsWith<IllegalArgumentException> { postShareMessage("Texto","https://example.test/bad\npath") }
    }
}
