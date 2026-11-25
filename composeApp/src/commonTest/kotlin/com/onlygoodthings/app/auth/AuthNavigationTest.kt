package com.onlygoodthings.app.auth

import com.onlygoodthings.app.nav.OgtRoute
import com.onlygoodthings.app.nav.authenticatedStartRoute
import kotlin.test.*

class AuthNavigationTest {
    @Test fun aNewGoogleAccountMustCompleteItsProfileEvenOnAnAlreadyOnboardedDevice() {
        for (onboarded in listOf(false, true)) {
            assertEquals(OgtRoute.CompleteProfile, authenticatedStartRoute(false, true, onboarded))
        }
    }

    @Test fun theCommunityAgreementRemainsRequiredBeforeCompletingTheProfile() {
        for (incomplete in listOf(false, true)) {
            assertEquals(OgtRoute.CommunityAgreement, authenticatedStartRoute(true, incomplete, true))
        }
    }

    @Test fun anExistingCompleteProfileKeepsItsPermissionsOrHomeDestination() {
        assertEquals(OgtRoute.Permissions, authenticatedStartRoute(false, false, false))
        assertEquals(OgtRoute.Feed, authenticatedStartRoute(false, false, true))
    }
}
