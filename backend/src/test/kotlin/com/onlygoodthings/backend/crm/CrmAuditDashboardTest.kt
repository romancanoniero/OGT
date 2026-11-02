package com.onlygoodthings.backend.crm

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class CrmAuditDashboardTest {
    @Test fun compressionUsesByteTotalsAndDoesNotInventHistoricalRatiosOrHideGrowth() {
        val empty=compressionSummary(0,0,0,null)
        assertNull(empty.inputOutputRatio);assertNull(empty.reductionPercent)
        val weighted=compressionSummary(2,1100,600,100)
        assertEquals(1100.0/600,weighted.inputOutputRatio!!,0.000001)
        assertEquals(100.0*500/1100,weighted.reductionPercent!!,0.000001)
        assertEquals(-50.0,compressionSummary(1,100,150,100).reductionPercent!!,0.000001)
    }
}
