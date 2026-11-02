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
    @Test fun radarToneUsesSavedSoundChoiceAndVerifiedAccount() {
        val prefs=com.onlygoodthings.app.auth.AuthPrefs()
        val owner=prefs.noticeOwnerId;val saved=prefs.noticeSettingsJson
        try {
            val tone=com.onlygoodthings.shared.data.local.LocalNotification("tone","PARKING","","","",false,recipientUserId="A")
            prefs.noticeOwnerId="A"
            prefs.noticeSettingsJson="{\"parkingRadarSounds\":false,\"quietHoursEnabled\":false}"
            assertFalse(com.onlygoodthings.app.notify.currentNoticePresentation(tone)!!.sound)
            prefs.noticeSettingsJson="{\"parkingRadarSounds\":true,\"quietHoursEnabled\":false}"
            assertTrue(com.onlygoodthings.app.notify.currentNoticePresentation(tone)!!.sound)
            prefs.noticeOwnerId="B"
            assertNull(com.onlygoodthings.app.notify.currentNoticePresentation(tone))
        } finally { prefs.noticeOwnerId=owner;prefs.noticeSettingsJson=saved }
    }

    @Test fun noticesCannotCrossAccountsOrAppearAfterLogout() {
        val prefs=com.onlygoodthings.app.auth.AuthPrefs()
        val owner=prefs.noticeOwnerId;val saved=prefs.noticeSettingsJson
        try {
            prefs.noticeOwnerId="A"
            prefs.noticeSettingsJson="{}"
            val notice=com.onlygoodthings.shared.data.local.LocalNotification("qa","ALERT","Aviso","Mensaje de prueba","Ahora",false,recipientUserId="B",serverDelivered=true,serverSound=false)
            assertNull(com.onlygoodthings.app.notify.currentNoticePresentation(notice))
            val own=notice.copy(recipientUserId="A")
            assertFalse(com.onlygoodthings.app.notify.currentNoticePresentation(own)!!.sound)
            prefs.noticeOwnerId=""
            assertNull(com.onlygoodthings.app.notify.currentNoticePresentation(own))
        } finally { prefs.noticeOwnerId=owner;prefs.noticeSettingsJson=saved }
    }

    @Test fun settingsRestorePerOwnerAndSwitchingDisablesThePreviousNotificationIdentity() {
        val prefs=com.onlygoodthings.app.auth.AuthPrefs()
        val saved=prefs.settingsAccountsJson
        val owner=prefs.noticeOwnerId
        val noticeSettings=prefs.noticeSettingsJson
        try {
            prefs.settingsAccountsJson=""
            val session=OgtPreviewSession(OgtLocalDatabase(),prefs=prefs)
            session.switchSettingsAccount("firebase-A");session.currentUserId="server-A"
            session.applyAccountSettings(com.onlygoodthings.shared.domain.AccountSettings(publicProfileVisible=false,radarEnabled=false))
            assertEquals("server-A",prefs.noticeOwnerId)
            session.switchSettingsAccount("firebase-B");session.currentUserId="server-B"
            assertEquals("",prefs.noticeOwnerId)
            assertTrue(session.publicProfileVisible);assertTrue(session.radarEnabled)
            session.applyAccountSettings(com.onlygoodthings.shared.domain.AccountSettings(skillAlertPush=false))
            val restarted=OgtPreviewSession(OgtLocalDatabase(),prefs=prefs)
            restarted.switchSettingsAccount("firebase-A")
            assertFalse(restarted.publicProfileVisible);assertFalse(restarted.radarEnabled)
            restarted.deleteCurrentSettingsAccount()
            assertEquals("",prefs.noticeOwnerId)
            restarted.switchSettingsAccount("firebase-B")
            assertFalse(restarted.skillAlertPush)
        } finally {
            prefs.settingsAccountsJson=saved;prefs.noticeOwnerId=owner;prefs.noticeSettingsJson=noticeSettings
        }
    }

    @Test
    fun switchingAccountsRestoresOnlyTheirOwnParkingAndClearsTransientPrompts() {
        val prefs = com.onlygoodthings.app.auth.AuthPrefs()
        val saved = prefs.parkingAccountsJson
        val oldGarage = prefs.vehiclesJson
        try {
            prefs.parkingAccountsJson = ""
            // Old device-wide records have no reliable ownership metadata.
            prefs.vehiclesJson = "[{\"id\":\"legacy\",\"make\":\"Legacy\",\"color\":\"Gris\",\"plate\":\"OLD123\"}]"
            val session = OgtPreviewSession(OgtLocalDatabase(), prefs = prefs)
            assertTrue(session.vehicles.isEmpty())
            session.switchParkingAccount("firebase-A")
            assertTrue(session.vehicles.isEmpty())
            val a = com.onlygoodthings.shared.domain.VehicleProfile("a", "Auto A", "Gris", plate = "AA123BB")
            val a2 = a.copy(id = "a2", plate = "AA456CC")
            session.saveVehicle(a)
            session.saveVehicle(a2)
            session.selectVehicle(a.id)
            val parked = com.onlygoodthings.shared.domain.ParkedCar(-34.6, -58.4, 100L, vehicle = a)
            session.rememberParked(parked)
            session.focusParkedCar = true
            session.askYieldApproach = true
            session.showYieldNotifying = true
            session.switchParkingAccount("")
            assertTrue(session.vehicles.isEmpty())
            assertNull(session.parkedCar)
            assertFalse(session.focusParkedCar)
            assertFalse(session.askYieldApproach)
            assertFalse(session.showYieldNotifying)
            session.switchParkingAccount("firebase-B")
            assertTrue(session.vehicles.isEmpty())
            assertFalse(session.vehicle.isReady())
            assertFalse(session.isParked())
            val b = a.copy(id = "b", make = "Auto B")
            session.saveVehicle(b)
            session.switchParkingAccount("firebase-A")
            assertEquals(setOf(a, a2), session.vehicles.toSet())
            assertEquals(a, session.vehicle)
            assertEquals(parked, session.parkedCar)
            assertTrue(session.isParked())
            session.forgetParked()
            val restarted = OgtPreviewSession(OgtLocalDatabase(), prefs = prefs)
            restarted.switchParkingAccount("firebase-B")
            assertEquals(listOf(b), restarted.vehicles)
            restarted.deleteCurrentParkingAccount()
            restarted.switchParkingAccount("firebase-B")
            assertTrue(restarted.vehicles.isEmpty())
            restarted.switchParkingAccount("firebase-A")
            assertEquals(setOf(a, a2), restarted.vehicles.toSet())
            assertNull(restarted.parkedCar)
            assertTrue(prefs.vehiclesJson.contains("legacy"))
        } finally {
            prefs.parkingAccountsJson = saved
            prefs.vehiclesJson = oldGarage
        }
    }

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
