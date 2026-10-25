package com.onlygoodthings.backend.crm

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.util.UUID

class CrmUserModerationTest {
    private val alice = "11111111-1111-1111-1111-111111111111"
    private val bob = "22222222-2222-2222-2222-222222222222"

    @Test fun `roles gate destructive changes`() {
        assertTrue(crmCanModerateUsers("OWNER", "DELETE"))
        assertTrue(crmCanModerateUsers("TRUST_SAFETY", "BLOCK"))
        assertTrue(crmCanModerateUsers("TRUST_SAFETY", "UNBLOCK"))
        assertFalse(crmCanModerateUsers("TRUST_SAFETY", "DELETE"))
        assertFalse(crmCanModerateUsers("OPERATIONS", "BLOCK"))
        assertFalse(crmCanModerateUsers("SUPPORT", "DELETE"))
        assertFalse(crmCanModerateUsers(null, "DELETE"))
        assertFalse(crmCanModerateUsers("OWNER", "PURGE_ALL"))
    }

    @Test fun `batch requires canonical valid distinct identifiers`() {
        assertEquals(listOf(alice, bob), crmModerationIds(listOf(alice, bob)))
        assertNull(crmModerationIds(emptyList<String>()))
        assertNull(crmModerationIds(listOf(alice, alice)))
        assertNull(crmModerationIds(listOf("not-a-uuid")))
        assertNull(crmModerationIds(List(26) { UUID.randomUUID().toString() }))
        assertNull(crmModerationIds("all"))
    }

    @Test fun `reason cannot be empty vague or excessively long`() {
        assertNull(crmModerationReason("  "))
        assertNull(crmModerationReason("bad"))
        assertEquals("Denuncias comprobadas", crmModerationReason("  Denuncias comprobadas "))
        assertNull(crmModerationReason("x".repeat(501)))
    }

    @Test fun `only local upload filenames qualify for cleanup`() {
        val file = "12345678-1234-1234-1234-123456789abc.jpg"
        assertEquals(file, crmLocalMediaName("https://onlygoodthings.lat/media/u/" + file))
        assertEquals(file, crmLocalMediaName("/media/u/" + file + "?v=2"))
        assertNull(crmLocalMediaName("https://other.example/photo.jpg"))
        assertNull(crmLocalMediaName("/media/u/../secret.txt"))
        assertNull(crmLocalMediaName("/media/u/123.png"))
    }

    @Test fun `erased identity key is stable but never raw uid`() {
        val hash = crmFirebaseTombstoneHash("firebase-user-123")
        assertEquals(64, hash.length)
        assertTrue(hash.matches(Regex("[a-f0-9]{64}")))
        assertEquals(hash, crmFirebaseTombstoneHash("firebase-user-123"))
        assertNotEquals(hash, crmFirebaseTombstoneHash("firebase-user-124"))
    }

    @Test fun `read routes remain available while every contribution path is restricted`() {
        assertFalse(crmContributionPath("/api/v1/social/feed"))
        assertFalse(crmContributionPath("/api/v1/social/comments"))
        assertFalse(crmContributionPath("/api/v1/social/report"))
        assertFalse(crmContributionPath("/api/v1/social/post/delete"))
        assertFalse(crmContributionPath("/api/v1/crm/platform/users/list"))
        for (path in listOf(
            "/api/v1/social/publish", "/api/v1/social/comment",
            "/api/v1/social/anecdote", "/api/v1/social/anecdote/repost",
            "/api/v1/media/upload", "/api/v1/animals/publish",
            "/api/v1/parking/publish", "/api/v1/timebank/need",
            "/api/v1/timebank/message/send",
        )) assertTrue(crmContributionPath(path), "write must be denied: $path")
    }
}
