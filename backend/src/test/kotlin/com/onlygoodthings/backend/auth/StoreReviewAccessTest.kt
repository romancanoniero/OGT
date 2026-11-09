package com.onlygoodthings.backend.auth

import com.onlygoodthings.shared.domain.UserRole
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

class StoreReviewAccessTest {
    private val now=Instant.parse("2026-11-09T12:00:00Z")
    private val clock=Clock.fixed(now,ZoneOffset.UTC)
    private fun user(uid:String,role:UserRole=UserRole.USER)=VerifiedFirebaseUser(uid,null,null,null,role)

    @Test fun exactOrdinaryReviewersOnlyBeforeExpiry() {
        val policy=StoreReviewAccess(setOf("reviewer"),now.plusSeconds(60),clock)
        assertTrue(policy.allows(user("reviewer")))
        assertEquals(60_000L, policy.remainingMillis())
        assertFalse(policy.allows(user("someone-else")))
        assertFalse(policy.allows(user("reviewer-suffix")))
        UserRole.entries.filter { it!=UserRole.USER }.forEach { assertFalse(policy.allows(user("reviewer",it))) }
        assertFalse(StoreReviewAccess(setOf("reviewer"),now,clock).allows(user("reviewer")))
        assertEquals(0L, StoreReviewAccess(setOf("reviewer"),now.minusSeconds(1),clock).remainingMillis())
        assertFalse(StoreReviewAccess(setOf("reviewer"),null,clock).allows(user("reviewer")))
        assertFalse(StoreReviewAccess(emptySet(),now.plusSeconds(60),clock).allows(user("reviewer")))
    }

    @Test fun disabledByDefaultAndInvalidConfigurationsCannotOpenAccess() {
        assertFalse(StoreReviewAccess.from { null }.allows(user("reviewer")))
        assertThrows(IllegalArgumentException::class.java) { StoreReviewAccess.from { if(it=="OGT_STORE_REVIEW_UIDS")"reviewer" else null } }
        assertThrows(IllegalArgumentException::class.java) { StoreReviewAccess.from { if(it=="OGT_STORE_REVIEW_UIDS")"*" else null } }
    }
}
