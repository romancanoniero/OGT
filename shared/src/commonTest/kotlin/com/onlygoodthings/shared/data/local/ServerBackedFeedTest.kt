package com.onlygoodthings.shared.data.local

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ServerBackedFeedTest {
    @Test
    fun mockAndUnconfirmedPostsAreNeverConsideredPublished() {
        assertFalse(isServerBackedPostId("post-pub-1730000000000"))
        assertFalse(isServerBackedPostId(OgtIds.PostTrueque))
        assertFalse(isServerBackedPostId(OgtIds.PostAnaComedor))
        assertFalse(isServerBackedPostId("asset://seed_n01"))
        assertFalse(isServerBackedPostId("user-mariana"))
    }

    @Test
    fun confirmedServerUuidsAreValidForMobileFeedAndCrm() {
        assertTrue(isServerBackedPostId("d3f0e661-5121-4a22-8233-8747cd4c0018"))
        assertTrue(isServerBackedPostId("550E8400-E29B-41D4-A716-446655440000"))
    }

    @Test
    fun localUnconfirmedPostsNeverLeakIntoRenderedFeed() {
        val db = OgtLocalDatabase()
        db.posts += LocalSocialPost(
            id = "post-pub-1730000000000",
            authorKind = com.onlygoodthings.shared.domain.AuthorKind.USER,
            authorUserId = "user-me",
            authorCompanyId = null,
            place = "",
            timeLabel = "Ahora",
            tag = "Trueque",
            body = "Contenido local sin confirmar",
            impactCount = 0,
            commentCount = 0,
            isStory = false,
            storyLabel = null,
            createdAtEpochMs = 1L,
        )
        assertTrue(db.composeVisibleRiver("user-me").isEmpty())
    }
}
