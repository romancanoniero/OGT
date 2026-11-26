package com.onlygoodthings.shared.domain

import kotlin.test.*

class HashtagsTest {
    @Test fun normalizationUnifiesAccentsCaseAndCatalogSpaces() {
        assertEquals("buenaaccion",Hashtags.key(" #Buena Acción "))
        assertEquals("adopcion",Hashtags.key("ADOPCIÓN"))
        assertEquals("huerta_en_casa",Hashtags.key("#Huerta_En_Casa"))
    }
    @Test fun rejectsLinksPunctuationEmptyNumericAndOverlongHashtags() {
        for(raw in listOf("#","123","https://example.test","<script>","a".repeat(41),"tag-name"))assertFailsWith<IllegalArgumentException> { Hashtags.key(raw) }
    }
    @Test fun generalPostHashtagsDoNotBecomeOperationalForms() {
        assertEquals(FeedCardKind.COMMUNITY,feedCardKind("Mascota perdida",publicationKind="POST"))
        assertEquals(FeedCardKind.COMMUNITY,feedCardKind("Homenaje",publicationKind="POST"))
        assertFalse(isGatheringPost("Reforestación","POST"))
        assertFalse(GoodActionRecognition.isAction("Reforestación","POST"))
    }
}
