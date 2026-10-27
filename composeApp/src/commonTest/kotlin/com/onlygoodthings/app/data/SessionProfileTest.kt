package com.onlygoodthings.app.data

import com.onlygoodthings.shared.data.local.LocalUser
import com.onlygoodthings.shared.data.local.OgtLocalDatabase
import com.onlygoodthings.shared.domain.UserRole
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SessionProfileTest {
    private fun user(id: String, photo: String? = null) = LocalUser(
        id, "firebase-$id", null, null, id, photo, UserRole.USER,
        0, "invite", "community", "level", "honor", 0.0, 0.0,
    )

    @Test
    fun confirmedRemovalIsPersistedBeforeTheNextNetworkRequestAndSurvivesRestart() {
        val prefs = com.onlygoodthings.app.auth.AuthPrefs()
        val previousCache = prefs.feedCacheJson
        try {
            prefs.feedCacheJson = ""
            val db = OgtLocalDatabase()
            val me = user("me")
            db.users += me
            val template = com.onlygoodthings.shared.data.local.LocalSocialPost(
                id = "template", authorKind = com.onlygoodthings.shared.domain.AuthorKind.USER,
                authorUserId = me.id, authorCompanyId = null, place = "", timeLabel = "",
                tag = "Comunidad", body = "Cache QA", impactCount = 0, commentCount = 0,
                isStory = false, storyLabel = null,
            )
            val removed = "10000000-0000-4000-8000-000000000001"
            val otherPage = "10000000-0000-4000-8000-000000000002"
            val pending = "post-pub-pending"
            db.posts += listOf(removed, otherPage, pending).map { template.copy(id = it) }
            val session = OgtPreviewSession(db, me.id, prefs)
            session.persistSocialFeed()
            assertTrue(prefs.feedCacheJson.contains(removed))
            session.reconcileFeedVisibility(setOf(removed), emptySet())
            // No collector, subsequent request or manual export is needed to persist the deletion.
            assertFalse(prefs.feedCacheJson.contains(removed))
            val restarted = OgtLocalDatabase()
            OgtPreviewSession(restarted, me.id, prefs)
            assertFalse(restarted.posts.any { it.id == removed })
            assertTrue(restarted.posts.any { it.id == otherPage })
            assertTrue(restarted.posts.any { it.id == pending })
        } finally { prefs.feedCacheJson = previousCache }
    }

    @Test
    fun currentProfileReceivesServerPhotoAndNameWithoutLosingItsOtherFields() = runBlocking {
        val db = OgtLocalDatabase()
        val original = user("me")
        db.users += original
        val session = OgtPreviewSession(db, original.id).apply { overlayUser = original }
        val observer = launch(start = CoroutineStart.UNDISPATCHED) { session.observeProfileUpdates() }
        try {
            db.applyUserLive("firebase-me", "Updated", "https://example.test/photo.jpg")
            yield()
            assertEquals(original.copy(displayName = "Updated", photoUrl = "https://example.test/photo.jpg"), session.me())
            assertEquals(db.userOrNull(original.id), session.me())
        } finally { observer.cancelAndJoin() }
    }

    @Test
    fun anotherAuthorsPhotoNeverBecomesTheCurrentAccountsPhoto() = runBlocking {
        val db = OgtLocalDatabase()
        val original = user("me")
        db.users += original
        val session = OgtPreviewSession(db, original.id).apply { overlayUser = original }
        val observer = launch(start = CoroutineStart.UNDISPATCHED) { session.observeProfileUpdates() }
        try {
            db.applyUserLive("other", "Other", "https://example.test/other.jpg")
            yield()
            assertNull(session.me().photoUrl)
            assertEquals(original, session.me())
            session.currentUserId = ""
            session.overlayUser = null
            db.applyUserLive("me", null, "https://example.test/previous-account.jpg")
            yield()
            assertNull(session.overlayUser)
        } finally { observer.cancelAndJoin() }
    }

    @Test
    fun localPreviewIsReplacedBySavedRemotePhoto() = runBlocking {
        val db = OgtLocalDatabase()
        val original = user("me", "https://example.test/before.jpg")
        db.users += original
        val session = OgtPreviewSession(db, original.id).apply { overlayUser = original }
        val observer = launch(start = CoroutineStart.UNDISPATCHED) { session.observeProfileUpdates() }
        try {
            session.applyProfilePhoto("/tmp/picked.jpg")
            assertEquals("file:///tmp/picked.jpg", session.me().photoUrl)
            db.applyUserLive("me", null, "https://example.test/saved.jpg")
            yield()
            assertEquals("https://example.test/saved.jpg", session.me().photoUrl)
        } finally { observer.cancelAndJoin() }
    }
}
