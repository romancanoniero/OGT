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
}
