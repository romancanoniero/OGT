package com.onlygoodthings.shared.data.local

import com.onlygoodthings.shared.domain.*
import kotlin.test.*

class FeedCacheOwnershipTest {
    @Test fun personalizedOrderAndOlderRecommendationsSurviveRestartWithoutCrossingAccounts() {
        val db=cache();db.bindSocialFeedViewer("first")
        val old=db.posts.single().copy(id="00000000-0000-4000-8000-000000000002",createdAtEpochMs=0,hashtags=listOf(Hashtags.free("Ciencia")))
        db.posts+=old
        repeat(90) { index -> db.posts+=old.copy(id="noise-$index",createdAtEpochMs=1000+index.toLong()) }
        db.rememberFeedOrder("first",FeedMode.HOME,null,listOf(old.id))
        val restored=OgtLocalDatabase();restored.importSocialFeed(db.exportSocialFeed("first"));restored.bindSocialFeedViewer("first")
        assertEquals(listOf(old.id),restored.cachedFeedOrder("first",FeedMode.HOME,null))
        assertEquals(old.hashtags,restored.posts.first { it.id==old.id }.hashtags)
        restored.bindSocialFeedViewer("second")
        assertNull(restored.cachedFeedOrder("second",FeedMode.HOME,null))
    }
    private fun cache():OgtLocalDatabase = OgtLocalDatabase().apply {
        upsertRemoteSocial(SocialPost("00000000-0000-4000-8000-000000000001",AuthorKind.USER,"author","Nombre completo",
            body="Contenido protegido",mediaUrls=emptyList(),impactCount=7,commentCount=3,isStory=false,
            createdAtEpochMs=1,viewerHasImpacted=true))
    }
    @Test fun restartingTheSameAccountPreservesItsReactionsAndCachedContent() {
        val db=cache()
        val restarted=OgtLocalDatabase();restarted.importSocialFeed(db.exportSocialFeed("viewer"))
        restarted.bindSocialFeedViewer("viewer")
        assertEquals("viewer",restarted.socialFeedViewerId)
        assertTrue(restarted.posts.single().viewerHasImpacted)
        assertEquals("Contenido protegido",restarted.posts.single().body)
    }
    @Test fun changingAccountsOnlyResetsViewerSpecificFlagsNotPostsOrPublicCounters() {
        val db=cache();db.importSocialFeed(db.exportSocialFeed("first"))
        db.bindSocialFeedViewer("second")
        assertFalse(db.posts.single().viewerHasImpacted)
        assertEquals(7,db.posts.single().impactCount)
        assertEquals(3,db.posts.single().commentCount)
        assertEquals("Contenido protegido",db.posts.single().body)
    }
    @Test fun legacyCacheWithoutAnOwnerStillLoadsAndDoesNotAttributeReactionsToAnUnknownViewer() {
        val db=cache()
        val json=db.exportSocialFeed("viewer").replace("\"viewerId\":\"viewer\",", "").replace(",\"viewerId\":\"viewer\"", "")
        val restored=OgtLocalDatabase();restored.importSocialFeed(json)
        assertEquals(1,restored.posts.size)
        assertNull(restored.socialFeedViewerId)
        restored.bindSocialFeedViewer("viewer")
        assertFalse(restored.posts.single().viewerHasImpacted)
    }
}
