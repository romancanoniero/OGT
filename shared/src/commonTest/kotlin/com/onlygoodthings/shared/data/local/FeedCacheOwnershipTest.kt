package com.onlygoodthings.shared.data.local

import com.onlygoodthings.shared.domain.*
import kotlin.test.*

class FeedCacheOwnershipTest {
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
