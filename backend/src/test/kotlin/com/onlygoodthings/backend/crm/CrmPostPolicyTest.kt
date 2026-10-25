package com.onlygoodthings.backend.crm

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import com.onlygoodthings.shared.domain.SocialLiveCounters
import com.onlygoodthings.shared.protocol.WsCodec
import com.onlygoodthings.shared.realtime.OgtDbPaths
import com.onlygoodthings.shared.realtime.socialLiveCountersFrom
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
        assertTrue(CRM_VISIBLE_POST_SEARCH.contains("post_media"))
        assertTrue(CRM_VISIBLE_POST_SEARCH.contains("m.url"))
        assertTrue(CRM_VISIBLE_POST_SEARCH.contains("moderation_status = 'VISIBLE'"))
        assertFalse(CRM_VISIBLE_POST_SEARCH.contains("REMOVED"))
        assertFalse(CRM_VISIBLE_POST_SEARCH.contains("DELETE"))
        assertTrue(CRM_POST_REMOVE.contains("moderation_status = 'REMOVED'"))
        assertTrue(CRM_POST_REMOVE.contains("moderation_status = 'VISIBLE'"))
        assertFalse(CRM_POST_REMOVE.contains("DELETE"))
        assertTrue(CRM_VISIBLE_POST_SEARCH.contains("author_user_id"))
        assertTrue(CRM_VISIBLE_POST_SEARCH.contains("author_photo"))
        assertTrue(CRM_VISIBLE_POST_SEARCH.contains("Alguien de la comunidad"))
        assertTrue(CRM_VISIBLE_POST_SEARCH.contains("p.topic"))
        assertTrue(CRM_VISIBLE_POST_SEARCH.contains("alt_text"))
        assertTrue(crmQueryUsesAppLabel("alguien"))
        assertTrue(crmQueryUsesAppLabel("Alguien de la comunidad"))
        assertFalse(crmQueryUsesAppLabel("fauna"))
        assertFalse(crmQueryUsesAppLabel("de"))
    }

    @Test fun imageUrlUsesTheStoredHttpAddressOnly() {
        val stored = "https://onlygoodthings.lat/media/u/34b9a70e-7779-492e-81f9-c305a53201ad.jpg"
        assertEquals(stored, crmPostImageUrl("  $stored  "))
        assertNull(crmPostImageUrl(null))
        assertNull(crmPostImageUrl("  "))
        assertNull(crmPostImageUrl("file:///tmp/foto.jpg"))
    }

    @Test fun publicationSearchReadsTheIndex() {
        assertTrue(CRM_INDEX_MATCH.contains("crm_publication_index"))
        assertTrue(CRM_INDEX_MATCH.contains("origin <> 'label'"))
        assertTrue(CRM_INDEX_MATCH_WITH_LABEL.contains("crm_publication_index"))
        assertFalse(CRM_INDEX_MATCH_WITH_LABEL.contains("origin <> 'label'"))
        assertTrue(CRM_INDEX_SUGGEST.contains("origin = ?"))
        assertFalse(CRM_INDEX_MATCH.contains("REMOVED"))
        assertFalse(CRM_INDEX_MATCH.contains("DELETE"))
        assertEquals("Don Héctor", crmSuggestText("  Don   Héctor  "))
        val long = "a".repeat(120)
        assertEquals(88, crmSuggestText(long).length)
        assertTrue(crmSuggestText(long).endsWith("…"))
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

    @Test fun pageCursorRemembersOrderAndRejectsMalformedInput() {
        val id = UUID.randomUUID().toString()
        val cursor = CrmPostPageCursor("author", "asc", "ana", "2026-10-25T10:30:00.123456Z", id)
        val encoded = crmEncodePostPageCursor(cursor)
        assertEquals(cursor, parseCrmPostPageCursor(encoded))
        assertNull(parseCrmPostPageCursor("v2.not-a-valid-cursor"))
        assertFalse(crmValidPostSort("DROP TABLE posts", "asc"))
        assertFalse(crmValidPostSort("author", "random"))
        assertTrue(crmValidPostSort("createdAt", "desc"))
        assertTrue(crmValidPostSort("text", "asc"))
        assertEquals("createdAt", parseCrmPostPageCursor(crmPostCursor(1_700_000_000_000, id))?.sortBy)
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

    @Test fun removalOfCommentAndAnecdoteKeepsTheRowAndProjectsTheListenedNode() {
        val postId = UUID.randomUUID().toString()
        val commentId = UUID.randomUUID().toString()
        val anecdoteId = UUID.randomUUID().toString()
        val comment = crmRemovedCommentLive(postId, commentId, "author", "gran hombre")
        assertEquals("REMOVED", comment.moderationStatus)
        assertEquals("ogt/social/posts/$postId/comments/$commentId", crmRemovedCommentPath(postId, commentId))
        assertFalse(CRM_COMMENT_REMOVE.contains("DELETE"))
        assertTrue(CRM_COMMENT_REMOVE.contains("moderation_status = 'REMOVED'"))
        val anecdote = crmRemovedAnecdoteLive(postId, anecdoteId, 1, 0)
        assertEquals(anecdoteId, anecdote.removedAnecdoteId)
        assertEquals("VISIBLE", anecdote.moderationStatus)
        assertEquals("ogt/social/posts/$postId", crmRemovedAnecdotePath(postId))
        assertFalse(CRM_ANECDOTE_REMOVE.contains("DELETE"))
        assertTrue(CRM_ANECDOTE_REMOVE.contains("moderation_status = 'REMOVED'"))
        assertEquals(commentId, crmOneId(mapOf("commentId" to commentId), "commentId"))
        assertEquals("texto ajeno", crmActionReason(mapOf("reason" to "texto ajeno")))
        assertNull(crmActionReason(mapOf("reason" to "no")))
    }

    @Test fun attributionKeepsTheRowAndProjectsTheSamePostNode() {
        val postId = UUID.randomUUID().toString()
        val userId = UUID.randomUUID().toString()
        val live = SocialLiveCounters(
            id = postId,
            commentCount = 2,
            impactCount = 1,
            authorUserId = userId,
            authorName = "Administrador",
            moderationStatus = "VISIBLE",
        )
        assertEquals("Administrador", live.authorName)
        assertEquals(userId, live.authorUserId)
        assertEquals("VISIBLE", live.moderationStatus)
        assertEquals("ogt/social/posts/$postId", crmRemovedPostPath(postId))
        assertFalse(CRM_POST_ATTRIBUTE.contains("DELETE"))
        assertTrue(CRM_POST_ATTRIBUTE.contains("author_user_id"))
        assertTrue(CRM_POST_ATTRIBUTE.contains("moderation_status = 'VISIBLE'"))
        assertTrue(CRM_USER_SEARCH.contains("display_name"))
        assertTrue(CRM_USER_SEARCH.contains("email"))
        assertFalse(CRM_USER_SEARCH.contains("firebase_uid"))
        assertEquals(userId, crmOneId(mapOf("userId" to userId), "userId"))
    }

    @Test fun frameKeepsTheFileAndUsesTheSixteenByNineWindow() {
        val frame = crmPostFrame(0.5, 0.32, 1.4)
        assertEquals(0.32, frame?.frameY)
        assertEquals(1.4, frame?.frameZoom)
        assertNull(crmPostFrame(null, 0.5, 1.0))
        assertNull(crmPostFrame(1.2, 0.5, 1.0))
        assertNull(crmPostFrame(0.5, 0.5, 0.5))
        assertEquals(1.0, crmPostFrame(0.2, 0.8, null)?.frameZoom)
        assertFalse(CRM_POST_FRAME.contains("DELETE"))
        assertTrue(CRM_POST_FRAME.contains("original_url = COALESCE(m.original_url, m.url)"))
        assertTrue(CRM_POST_FRAME.contains("url = ?"))
        assertTrue(CRM_POST_FRAME.contains("frame_x"))
        assertTrue(CRM_POST_FRAME.contains("moderation_status = 'VISIBLE'"))
        assertFalse(CRM_POST_DISPLAY_URL.contains("DELETE"))
        assertTrue(CRM_VISIBLE_POST_SEARCH.contains("m.url"))
        assertTrue(CRM_VISIBLE_POST_SEARCH.contains("original_url"))
        assertTrue(CRM_VISIBLE_POST_SEARCH.contains("frame_x"))
        val photo = "https://onlygoodthings.lat/media/u/aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa.jpg"
        val parsed = socialLiveCountersFrom(
            mapOf(
                "id" to "p",
                "url" to photo,
                "mediaUrls" to listOf(photo),
            ),
            "p",
        )
        assertEquals(photo, parsed?.url)
        assertEquals(listOf(photo), parsed?.mediaUrls)
        assertEquals("ogt/social/posts/p", OgtDbPaths.socialPost("p"))
        val encoded = WsCodec.json.encodeToString(
            SocialLiveCounters.serializer(),
            SocialLiveCounters(
                id = "p",
                commentCount = 1,
                impactCount = 1,
                url = photo,
                mediaUrls = listOf(photo),
                imageUrl = photo,
            ),
        )
        val roundTrip = WsCodec.json.decodeFromString(SocialLiveCounters.serializer(), encoded)
        assertEquals(photo, roundTrip.url)
        assertEquals(listOf(photo), roundTrip.mediaUrls)
        assertEquals(photo, roundTrip.imageUrl)
        assertTrue(encoded.contains("\"url\":"))
        assertTrue(encoded.contains("\"mediaUrls\":"))
    }
}
