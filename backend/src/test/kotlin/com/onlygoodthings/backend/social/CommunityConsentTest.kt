package com.onlygoodthings.backend.social

import com.onlygoodthings.shared.domain.COMMUNITY_TERMS_VERSION
import com.onlygoodthings.shared.domain.CommunityConsent
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class CommunityConsentTest {
    private val valid = mapOf("version" to COMMUNITY_TERMS_VERSION, "adult" to true, "accepted" to true)

    @Test fun requiresExplicitAdultAndCurrentTermsWithoutAcceptingAnotherIdentity() {
        validateCommunityConsent(valid)
        for (invalid in listOf(valid - "adult", valid + ("adult" to false), valid + ("adult" to "true"),
            valid + ("accepted" to false), valid + ("version" to "old"), valid + ("userId" to "someone-else"))) {
            assertThrows(IllegalArgumentException::class.java) { validateCommunityConsent(invalid) }
        }
    }

    @Test fun oldMissingOrIncompleteAcceptanceDoesNotPassTheGate() {
        assertFalse(CommunityConsent().current)
        assertFalse(CommunityConsent("old", true, 1).current)
        assertFalse(CommunityConsent(COMMUNITY_TERMS_VERSION, false, 1).current)
        assertFalse(CommunityConsent(COMMUNITY_TERMS_VERSION, true, 0).current)
        assertTrue(CommunityConsent(COMMUNITY_TERMS_VERSION, true, 1).current)
    }
}
