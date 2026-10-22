package com.onlygoodthings.backend.crm

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class CrmStepUpPolicyTest {
    private val now = 1_800_000_000L

    @Test fun validStepUp() {
        assertTrue(crmStepUpAllowed(true, true, now - 60, now))
        assertTrue(crmStepUpAllowed(true, true, now - 900, now))
    }

    @Test fun rejectsMissingOrUnverifiedFactors() {
        assertFalse(crmStepUpAllowed(false, true, now, now))
        assertFalse(crmStepUpAllowed(true, false, now, now))
        assertFalse(crmStepUpAllowed(true, true, null, now))
    }

    @Test fun rejectsExpiredAndImplausibleFutureSessions() {
        assertFalse(crmStepUpAllowed(true, true, now - 901, now))
        assertFalse(crmStepUpAllowed(true, true, now + 61, now))
    }
}
