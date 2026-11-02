package com.onlygoodthings.app.auth

import kotlinx.coroutines.runBlocking
import kotlin.test.*

class AccountRequestAuthTest {
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
