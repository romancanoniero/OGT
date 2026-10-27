package com.onlygoodthings.backend.stories

import org.junit.jupiter.api.Test
import kotlin.test.*

class StoryReportTest {
    @Test fun reportsAcceptTheSameStableReasonsAsTheExistingPublicationSheet() {
        for(code in listOf("HARM_HARASSMENT","SCAM_LINK","HONOR_CONSENT","OFF_TOPIC_PROMO","OTHER")) validateStoryReport(code,"Detalle opcional")
        validateStoryReport("OTHER",null);validateStoryReport("OTHER","x".repeat(500))
    }
    @Test fun invalidCodesAndOversizedDetailsAreRejectedBeforeWritingAnyReport() {
        for(code in listOf("","<script>","harm","ARBITRARY")) assertFailsWith<IllegalArgumentException> { validateStoryReport(code,null) }
        assertFailsWith<IllegalArgumentException> { validateStoryReport("OTHER","x".repeat(501)) }
    }
}
