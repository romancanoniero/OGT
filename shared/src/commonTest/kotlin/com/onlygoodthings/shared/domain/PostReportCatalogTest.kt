package com.onlygoodthings.shared.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class PostReportCatalogTest {
    @Test
    fun homenajeSumaMotivoPropioYElRestoNo() {
        val honor = PostReportCatalog.motives(FeedCardKind.HOMENAJE).map { it.code }
        val community = PostReportCatalog.motives(FeedCardKind.COMMUNITY).map { it.code }
        assertTrue("HONOR" in honor)
        assertTrue("HONOR" !in community)
        assertTrue("OFF_TOPIC" in community)
        assertNotNull(PostReportCatalog.byCode("OFF_TOPIC_PROSELYTISM"))
        assertTrue("FALSE" in community)
        assertTrue("SCAM" in community)
        assertTrue("HARM" in community)
        assertTrue("OTHER" in community)
    }

    @Test
    fun losCodigosLleganAModeracion() {
        assertNotNull(PostReportCatalog.byCode("FALSE_LOST_PET"))
        assertEquals("SCAM_ADOPTION", PostReportCatalog.byCode("SCAM_ADOPTION")?.code)
    }
}
