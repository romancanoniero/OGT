package com.onlygoodthings.backend.realtime

import com.onlygoodthings.shared.realtime.OgtDbPaths
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class DbListenPolicyTest {
    private val userA = "aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa"
    private val userB = "bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb"

    @Test fun `public trees and own presence are allowed`() {
        for (path in listOf(
            OgtDbPaths.PARKING_SPOTS,
            "${OgtDbPaths.PARKING_SPOTS}/spot-1",
            OgtDbPaths.PARKING_TICKS,
            OgtDbPaths.IMPACT_ALERTS,
            OgtDbPaths.SOCIAL_FEED,
            "${OgtDbPaths.SOCIAL_FEED}/post-1/comments",
            OgtDbPaths.USERS,
            OgtDbPaths.user(userA),
            OgtDbPaths.presence(userA),
            "${OgtDbPaths.presence(userA)}/meta",
        )) {
            assertTrue(dbListenAllowed(path, userA), path)
        }
    }

    @Test fun `account B cannot listen to private, parent or arbitrary paths of A`() {
        for (path in listOf(
            "",
            "/",
            "ogt",
            "ogt/",
            OgtDbPaths.PRESENCE,
            OgtDbPaths.presence(userA),
            "${OgtDbPaths.presence(userA)}/lat",
            "ogt/social",
            "ogt/parking",
            "ogt/private/inbox",
            "secret",
            "../ogt/social/posts",
            "ogt/social/posts/../presence/$userA",
        )) {
            assertFalse(dbListenAllowed(path, userB), path)
        }
    }
}
