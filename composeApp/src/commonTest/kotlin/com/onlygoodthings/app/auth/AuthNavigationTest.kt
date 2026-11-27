package com.onlygoodthings.app.auth

import com.onlygoodthings.app.nav.OgtRoute
import com.onlygoodthings.app.nav.authenticatedStartRoute
import com.onlygoodthings.app.nav.restoredSessionStartRoute
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

    @Test fun reopeningABiometricallyProtectedSessionUnlocksItWithoutReturningToLogin() {
        for (needsConsent in listOf(false, true)) {
            for (needsProfile in listOf(false, true)) {
                for (onboarded in listOf(false, true)) {
                    assertEquals(OgtRoute.UnlockSession, restoredSessionStartRoute(
                        AuthRestore.NeedsBiometric, needsConsent, needsProfile, onboarded,
                    ))
                }
            }
        }
    }

    @Test fun restoredSessionsKeepTheirConsentProfileAndOnboardingGates() {
        for (restored in listOf(AuthRestore.Home, AuthRestore.FirstRun)) {
            assertEquals(OgtRoute.Feed, restoredSessionStartRoute(restored, false, false, true))
            assertEquals(OgtRoute.CommunityAgreement, restoredSessionStartRoute(restored, true, true, true))
            assertEquals(OgtRoute.CompleteProfile, restoredSessionStartRoute(restored, false, true, true))
            assertEquals(OgtRoute.Permissions, restoredSessionStartRoute(restored, false, false, false))
        }
    }

    @Test fun missingOrRevokedCredentialsNeverReachTheSavedSessionUnlockOrFeed() {
        assertEquals(OgtRoute.Landing, restoredSessionStartRoute(AuthRestore.LoggedOut, false, false, true))
        assertEquals(OgtRoute.Login, restoredSessionStartRoute(AuthRestore.LoginRequired, false, false, true))
    }
}
