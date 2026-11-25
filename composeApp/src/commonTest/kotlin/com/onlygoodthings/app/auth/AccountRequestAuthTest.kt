package com.onlygoodthings.app.auth

import kotlinx.coroutines.runBlocking
import kotlin.test.*

class AccountRequestAuthTest {
    @Test fun aRevokedOrMissingCredentialExpiresOnlyTheSameAccount() = runBlocking {
        for (clearedByFirebase in listOf(false, true)) {
            var owner: String? = "firebase-A"
            var expired = false
            assertFailsWith<AuthSessionExpired> {
                renewedSessionToken({ owner }, {
                    if (clearedByFirebase) owner = null
                    throw AuthSessionExpired()
                }, { expired = true })
            }
            assertTrue(expired)
        }
        var expired = false
        assertFailsWith<AuthSessionExpired> { renewedSessionToken({ "firebase-A" }, { null }, { expired = true }) }
        assertTrue(expired)
    }

    @Test fun aLateTokenOrRevocationFromAnotherAccountCannotAuthorizeOrSignOutTheNewAccount() = runBlocking {
        for (revoked in listOf(false, true)) {
            var owner: String? = "firebase-A"
            var expired = false
            assertFailsWith<IllegalStateException> {
                renewedSessionToken({ owner }, {
                    owner = "firebase-B"
                    if (revoked) throw AuthSessionExpired()
                    "A-token"
                }, { expired = true })
            }
            assertFalse(expired)
            assertEquals("firebase-B", owner)
        }
    }

    @Test fun temporaryRefreshFailuresPreserveTheSessionAndCancellation() = runBlocking {
        var expired = false
        val network = AuthException("Sin conexión")
        assertSame(network, assertFailsWith<AuthException> {
            renewedSessionToken({ "firebase-A" }, { throw network }, { expired = true })
        })
        assertFailsWith<kotlinx.coroutines.CancellationException> {
            renewedSessionToken({ "firebase-A" }, { throw kotlinx.coroutines.CancellationException() }, { expired = true })
        }
        assertFalse(expired)
    }

    @Test fun obtainsTheCurrentProviderCredentialInsteadOfReusingAnExpiredSessionSnapshot() = runBlocking {
        var providerToken="cached-valid"
        val owner={"firebase-A"}
        assertEquals("cached-valid",ownerScopedAccountToken(owner) { providerToken })
        providerToken="renewed-by-provider"
        assertEquals("renewed-by-provider",ownerScopedAccountToken(owner) { providerToken })
    }
    @Test fun aRefreshThatCompletesAfterAccountSwitchOrLogoutCannotAuthorizeTheOldRequest() = runBlocking {
        for(next in listOf("firebase-B","")) {
            var current="firebase-A"
            assertFailsWith<IllegalStateException> { ownerScopedAccountToken({current}) { current=next;"A-token" } }
        }
        var called=false
        assertFailsWith<IllegalStateException> { ownerScopedAccountToken({""}) { called=true;"token" } }
        assertFalse(called)
    }
}
