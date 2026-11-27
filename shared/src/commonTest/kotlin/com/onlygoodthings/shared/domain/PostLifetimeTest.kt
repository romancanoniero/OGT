package com.onlygoodthings.shared.domain

import kotlin.test.*

class PostLifetimeTest {
    @Test fun legacyPostsHaveNoExpiryAndTheBoundaryIsExclusive() {
        assertTrue(PostLifetimeRules.active(null,Long.MAX_VALUE))
        assertTrue(PostLifetimeRules.active(101,100))
        assertFalse(PostLifetimeRules.active(100,100))
        assertFalse(PostLifetimeRules.active(99,100))
    }
}
