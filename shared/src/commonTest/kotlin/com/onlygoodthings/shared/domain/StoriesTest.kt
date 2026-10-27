package com.onlygoodthings.shared.domain

import kotlin.test.*

class StoriesTest {
    @Test fun cacheCannotResurrectExpiredOrRemovedStories() {
        val row=Story("story","owner","Owner",caption="",audience=StoryAudience.COMMUNITY,
            kind=MediaKind.IMAGE,mediaUrl="https://example.test/private",durationMs=5000,createdAtEpochMs=100,expiresAtEpochMs=200)
        assertEquals(listOf(row),activeStories(listOf(row),199))
        assertTrue(activeStories(listOf(row),200).isEmpty())
        assertTrue(activeStories(listOf(row.copy(status="REMOVED")),150).isEmpty())
        assertEquals(86400000L,StoryRules.LIFETIME_MS)
    }
}
