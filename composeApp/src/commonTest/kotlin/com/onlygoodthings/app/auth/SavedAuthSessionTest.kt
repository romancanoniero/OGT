package com.onlygoodthings.app.auth

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import kotlin.test.*

class SavedAuthSessionTest {
    private val user = AuthUser("saved-firebase-user", "neighbor@example.test", null, "Vecina", null)

    @Test fun reopeningRestoresTheSavedIdentityAndKeepsItsOnboardingDestination() = runBlocking {
        for (onboarded in listOf(false, true)) {
            var restoredUser: AuthUser? = null
            val restored = restoreSavedAuthSession(
                explicitlySignedOut = false,
                appLockEnabled = false,
                currentUser = { user },
                openSession = { restoredUser = it },
                onboardingDone = { onboarded },
                onSessionExpired = { fail("A saved credential must not be signed out") },
            )
            assertEquals(user, restoredUser)
            assertEquals(if (onboarded) AuthRestore.Home else AuthRestore.FirstRun, restored)
        }
    }

    @Test fun explicitLogoutPreventsRestorationEvenIfNativeCredentialCleanupFailed() = runBlocking {
        val restored = restoreSavedAuthSession(
            explicitlySignedOut = true,
            appLockEnabled = true,
            currentUser = { fail("Do not reuse Firebase's leftover identity after logout") },
            openSession = { fail("An explicitly closed session must stay closed") },
            onboardingDone = { true },
            onSessionExpired = { fail("Already signed out") },
        )
        assertEquals(AuthRestore.LoggedOut, restored)
    }

    @Test fun aFreshInstallationWithoutCredentialsRemainsLoggedOut() = runBlocking {
        assertEquals(AuthRestore.LoggedOut, restoreSavedAuthSession(
            explicitlySignedOut = false,
            appLockEnabled = false,
            currentUser = { null },
            openSession = { fail("No identity was authenticated") },
            onboardingDone = { true },
            onSessionExpired = { fail("No session was revoked") },
        ))
    }

    @Test fun explicitlyOptingIntoAppLockStillProtectsTheSavedSession() = runBlocking {
        assertEquals(AuthRestore.NeedsBiometric, restoreSavedAuthSession(
            explicitlySignedOut = false,
            appLockEnabled = true,
            currentUser = { user },
            openSession = { fail("Wait for biometric confirmation") },
            onboardingDone = { true },
            onSessionExpired = { fail("Biometrics do not discard the credential") },
        ))
    }

    @Test fun aTemporaryFailureCanRetryTheSameSessionWithoutSigningInAgain() = runBlocking {
        val network = AuthException("Sin conexión")
        var attempts = 0
        var signedOut = false
        suspend fun restore() = restoreSavedAuthSession(
            explicitlySignedOut = signedOut,
            appLockEnabled = false,
            currentUser = { user },
            openSession = {
                assertEquals(user, it)
                if (++attempts == 1) throw network
            },
            onboardingDone = { true },
            onSessionExpired = { signedOut = true },
        )
        assertSame(network, assertFailsWith<AuthException> { restore() })
        assertFalse(signedOut)
        assertEquals(AuthRestore.Home, restore())
        assertEquals(2, attempts)
    }

    @Test fun aRevokedCredentialStillRequiresAuthenticationAndStaysClosedAfterRestart() = runBlocking {
        var signedOut = false
        var attempts = 0
        suspend fun restore() = restoreSavedAuthSession(
            explicitlySignedOut = signedOut,
            appLockEnabled = false,
            currentUser = { user },
            openSession = { attempts++; throw AuthSessionExpired() },
            onboardingDone = { true },
            onSessionExpired = { signedOut = true },
        )
        assertEquals(AuthRestore.LoginRequired, restore())
        assertTrue(signedOut)
        assertEquals(AuthRestore.LoggedOut, restore())
        assertEquals(1, attempts)
    }

    @Test fun leavingStartupDoesNotTurnCancellationIntoLogoutOrARetryError() = runBlocking {
        var signedOut = false
        assertFailsWith<CancellationException> {
            restoreSavedAuthSession(
                explicitlySignedOut = false,
                appLockEnabled = false,
                currentUser = { user },
                openSession = { throw CancellationException("Startup left") },
                onboardingDone = { true },
                onSessionExpired = { signedOut = true },
            )
        }
        assertFalse(signedOut)
    }
}
