package com.onlygoodthings.backend.auth

import org.junit.jupiter.api.Test
import kotlin.test.*

class AccountErasureTest {
    @Test fun providerFailuresBackOffWithoutAnUnboundedRetryLoop() {
        assertEquals(30,deletionRetryDelaySeconds(0));assertEquals(60,deletionRetryDelaySeconds(1))
        assertEquals(3600,deletionRetryDelaySeconds(100));assertEquals(30,deletionRetryDelaySeconds(-1))
    }
    @Test fun retriesPersistCodesAndNeverProviderMessagesOrIdentityDetails() {
        assertEquals("PERMISSION_DENIED",safeDeletionError(IdentityDeletionFailure("PERMISSION_DENIED")))
        assertEquals("UNAVAILABLE",safeDeletionError(IllegalStateException("Sensitive provider details must never be stored")))
        assertEquals("UNAVAILABLE",safeDeletionError(IdentityDeletionFailure("someone@example.test")))
    }
}
