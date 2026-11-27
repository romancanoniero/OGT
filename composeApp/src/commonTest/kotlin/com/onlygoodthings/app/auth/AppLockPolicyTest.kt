package com.onlygoodthings.app.auth

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import kotlin.test.*

class AppLockPolicyTest {
    @Test fun enablingRequiresAnExistingSessionAndNeverPromptsAnAnonymousUser() = runBlocking<Unit> {
        assertFailsWith<AuthException> {
            updateSavedSessionLock(true, { null }, { fail("No authenticated owner") },
                { fail("No authenticated owner") }, { fail("Do not save") })
        }
    }

    @Test fun explicitEnablingPersistsOnlyAfterSuccessfulDeviceAuthentication() = runBlocking<Unit> {
        val events = mutableListOf<String>()
        updateSavedSessionLock(true, { "owner" }, { true }, { events += "authenticated"; true },
            { assertTrue(it); events += "saved" })
        assertEquals(listOf("authenticated", "saved"), events)
    }

    @Test fun cancelingTheNativePromptLeavesThePreviousChoiceUntouched() = runBlocking<Unit> {
        assertFailsWith<AuthException> {
            updateSavedSessionLock(true, { "owner" }, { true }, { false }, { fail("Do not enable after cancellation") })
        }
    }

    @Test fun anUnavailableBiometricMethodCannotEnableAppLock() = runBlocking<Unit> {
        assertFailsWith<AuthException> {
            updateSavedSessionLock(true, { "owner" }, { false }, { fail("Unsupported hardware") }, { fail("Do not save") })
        }
    }

    @Test fun aSessionChangedDuringThePromptCannotChangeAnotherAccountsDevicePreference() = runBlocking<Unit> {
        var owner = "first"
        assertFailsWith<AuthException> {
            updateSavedSessionLock(true, { owner }, { true }, { owner = "second"; true }, { fail("Session changed") })
        }
    }

    @Test fun turningOffTheOptionalLockFromAnOpenAuthenticatedSessionDoesNotRequireAnotherPrompt() = runBlocking<Unit> {
        var saved: Boolean? = null
        updateSavedSessionLock(false, { "owner" }, { fail("No prompt needed") },
            { fail("No prompt needed") }, { saved = it })
        assertEquals(false, saved)
    }

    @Test fun leavingSettingsDuringVerificationDoesNotEnableTheLock() = runBlocking<Unit> {
        assertFailsWith<CancellationException> {
            updateSavedSessionLock(true, { "owner" }, { true },
                { throw CancellationException("Settings left") }, { fail("Do not save") })
        }
    }
}
