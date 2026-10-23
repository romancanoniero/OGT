package com.onlygoodthings.backend.crm

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import com.onlygoodthings.shared.realtime.OgtDbPaths
import org.junit.jupiter.api.Test
import java.util.UUID

class CrmPostPolicyTest {
    @Test fun onlyOperationalStaffCanManageVisiblePosts() {
        for (role in listOf("OWNER", "OPERATIONS", "TRUST_SAFETY", "SUPPORT")) {
            assertTrue(crmCanManagePosts(role))
        }
        for (role in listOf("ANALYST", "FINANCE", null, "", "USER")) {
            assertFalse(crmCanManagePosts(role))
        }
    }

    @Test fun searchStaysOnVisibleRowsAndRemovalDoesNotDelete() {
        assertTrue(CRM_VISIBLE_POST_SEARCH.contains("moderation_status = 'VISIBLE'"))
        assertFalse(CRM_VISIBLE_POST_SEARCH.contains("REMOVED"))
        assertFalse(CRM_VISIBLE_POST_SEARCH.contains("DELETE"))
        assertTrue(CRM_POST_REMOVE.contains("moderation_status = 'REMOVED'"))
        assertTrue(CRM_POST_REMOVE.contains("moderation_status = 'VISIBLE'"))
        assertFalse(CRM_POST_REMOVE.contains("DELETE"))
    }

    @Test fun likePatternDoesNotTreatOperatorTextAsWildcards() {
        assertEquals("%plaza%", crmLikeContains("plaza"))
        assertEquals("%100\\%\\_off%", crmLikeContains("100%_off"))
    }

    @Test fun cursorRoundTripRejectsGarbage() {
        val id = UUID.randomUUID().toString()
        val raw = crmPostCursor(1_700_000_000_000, id)
        assertEquals(1_700_000_000_000L to id, parseCrmPostCursor(raw))
        assertNull(parseCrmPostCursor("no"))
        assertNull(parseCrmPostCursor("-1|$id"))
        assertNull(parseCrmPostCursor("10|not-a-uuid"))
    }

    @Test fun removalProjectsTheExistingPostNodeAsRemoved() {
        val id = UUID.randomUUID().toString()
        val live = crmRemovedPostLive(id)
        assertEquals("REMOVED", live.moderationStatus)
        assertEquals("ogt/social/posts/$id", crmRemovedPostPath(id))
        assertEquals(OgtDbPaths.socialPost(id), crmRemovedPostPath(id))
        assertEquals(listOf(id), crmPostIds(mapOf("postId" to id)))
        assertEquals(listOf(id), crmPostIds(mapOf("postIds" to listOf(id, id))))
    }
}
