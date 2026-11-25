package com.onlygoodthings.app.auth

import com.onlygoodthings.shared.domain.ApiResponse
import com.onlygoodthings.shared.domain.UserProfile
import com.onlygoodthings.shared.domain.UserRole
import kotlin.test.*

class AuthSessionPolicyTest {
    private val profile = UserProfile("ogt-id", "firebase-id", "Mi nombre en OGT", "https://example.test/saved.jpg", UserRole.USER, 12, "invite")

    @Test fun onlyAConfirmedMatchingProfileBecomesTheSession() {
        assertEquals(profile, confirmedSessionProfile(ApiResponse.ok(profile), "firebase-id"))
    }
    @Test fun serverRejectionCannotBecomeAnInventedLocalProfile() {
        val rejection = ApiResponse(false, profile, "Private internal detail", "ACCOUNT_UNAVAILABLE")
        val failure = assertFailsWith<AuthException> { confirmedSessionProfile(rejection, "firebase-id") }
        assertEquals("Esta cuenta está deshabilitada o fue eliminada", failure.message)
    }
    @Test fun missingProfileDoesNotAuthenticate() {
        assertFailsWith<AuthException> { confirmedSessionProfile(ApiResponse(true), "firebase-id") }
    }
    @Test fun anotherFirebaseIdentityIsNeverAttachedLocally() {
        assertFailsWith<AuthException> { confirmedSessionProfile(ApiResponse.ok(profile), "another-firebase-id") }
    }
    @Test fun emptyIdentityOrServerIdCannotOpenTheSession() {
        assertFailsWith<AuthException> { confirmedSessionProfile(ApiResponse.ok(profile), "") }
        assertFailsWith<AuthException> { confirmedSessionProfile(ApiResponse.ok(profile.copy(id = "")), "firebase-id") }
    }

    @Test fun rejectedSessionTokensRequireLoginWithoutForwardingProviderDetails() {
        for (code in listOf("INVALID_TOKEN", "UNAUTHENTICATED")) {
            val failure = assertFailsWith<AuthSessionExpired> {
                confirmedSessionProfile(ApiResponse(false, profile, "The user's credential is no longer valid", code), "firebase-id")
            }
            assertEquals("Volvé a ingresar", failure.message)
        }
    }

    @Test fun sessionExpiryIsDistinctFromWrongPasswordMfaAndTemporaryFailures() {
        for (code in listOf("ERROR_USER_TOKEN_EXPIRED", "ERROR_INVALID_USER_TOKEN", "auth/user-token-expired", "auth/invalid-user-token")) {
            assertTrue(isExpiredSessionCode(code), code)
        }
        for (code in listOf(null, "ERROR_INVALID_CREDENTIAL", "ERROR_WRONG_PASSWORD", "ERROR_SESSION_EXPIRED", "auth/code-expired", "auth/network-request-failed", "ACCOUNT_UNAVAILABLE")) {
            assertFalse(isExpiredSessionCode(code), code)
        }
    }

    @Test fun googleNamesDoNotSubstituteForTheMissingCommunityAndSavedProfilesStayComplete() {
        val photo = "https://example.test/google.jpg"
        assertTrue(profileNeedsCompletion("Nombre Google", "", "google-uid", photo))
        assertTrue(profileNeedsCompletion("persona@example.test", "Palermo", "google-uid", photo))
        assertTrue(profileNeedsCompletion("google-uid", "Palermo", "google-uid", photo))
        assertTrue(profileNeedsCompletion("Alguien De La Comunidad", "Palermo", "google-uid", photo))
        assertTrue(profileNeedsCompletion("Nombre Google", "Palermo", "google-uid", null))
        assertTrue(profileNeedsCompletion("Nombre Google", "Palermo", "google-uid", "  "))
        assertFalse(profileNeedsCompletion("Nombre guardado en OGT", "Palermo", "google-uid", photo))
    }
}
