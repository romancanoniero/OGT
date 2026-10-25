package com.onlygoodthings.backend.crm

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class CrmUserProfileTest {
    @Test fun onlyTrustSafetyAndOwnerCanInspectContentReports() {
        assertTrue(crmCanReviewUserReports("OWNER"))
        assertTrue(crmCanReviewUserReports("TRUST_SAFETY"))
        assertFalse(crmCanReviewUserReports("SUPPORT"))
        assertFalse(crmCanReviewUserReports("OPERATIONS"))
        assertFalse(crmCanReviewUserReports("USER"))
        assertFalse(crmCanReviewUserReports(null))
    }

    @Test fun publicPostsCursorMustBeValidAndCreatedAtDescending() {
        val cursor = crmEncodePostPageCursor(CrmPostPageCursor(
            "createdAt", "desc", "", "2026-10-24T12:00:00Z",
            "11111111-1111-4111-8111-111111111111",
        ))
        assertTrue(crmParseUserPostsCursor(cursor) == cursor)
        assertNull(crmParseUserPostsCursor("garbage"))
        assertNull(crmParseUserPostsCursor(crmEncodePostPageCursor(CrmPostPageCursor(
            "author", "desc", "alice", "2026-10-24T12:00:00Z",
            "11111111-1111-4111-8111-111111111111",
        ))))
    }
}
