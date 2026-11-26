package com.onlygoodthings.app.auth

import com.onlygoodthings.app.OgtApplication
import kotlinx.coroutines.runBlocking
import kotlin.test.*

class SavedAuthSessionPreferencesTest {
    @Test fun logoutAndSubsequentLoginSurviveNewPreferenceInstances() {
        val app = OgtApplication.instance
        check(app.packageName == "com.onlygoodthings.app.qa")
        val prefs = AuthPrefs()
        val before = prefs.sessionSignedOut
        try {
            prefs.sessionSignedOut = true
            assertTrue(AuthPrefs().sessionSignedOut)
            assertTrue(savedLogoutOnDisk())
            prefs.sessionSignedOut = false
            assertFalse(AuthPrefs().sessionSignedOut)
            assertFalse(savedLogoutOnDisk())
        } finally {
            prefs.sessionSignedOut = before
        }
    }

    @Test fun anOldUncheckedRememberSessionPreferenceDoesNotDiscardTheSavedIdentity() = runBlocking {
        val app = OgtApplication.instance
        check(app.packageName == "com.onlygoodthings.app.qa")
        val storage = app.getSharedPreferences("ogt_auth", 0)
        val hadKeep = storage.contains("keep")
        val oldKeep = storage.getBoolean("keep", true)
        val prefs = AuthPrefs()
        val wasSignedOut = prefs.sessionSignedOut
        val user = AuthUser("saved-firebase-user", null, null, "Vecina", null)
        try {
            storage.edit().putBoolean("keep", false).commit()
            prefs.sessionSignedOut = false
            var opened: AuthUser? = null
            val restored = restoreSavedAuthSession(
                explicitlySignedOut = AuthPrefs().sessionSignedOut,
                biometricEnabled = false,
                currentUser = { user },
                openSession = { opened = it },
                onboardingDone = { true },
                onSessionExpired = { fail("The old checkbox is not an explicit logout") },
            )
            assertEquals(AuthRestore.Home, restored)
            assertEquals(user, opened)
        } finally {
            prefs.sessionSignedOut = wasSignedOut
            storage.edit().apply {
                if (hadKeep) putBoolean("keep", oldKeep) else remove("keep")
            }.commit()
        }
    }

    private fun savedLogoutOnDisk(): Boolean {
        val file = java.io.File(OgtApplication.instance.applicationInfo.dataDir, "shared_prefs/ogt_auth.xml")
        file.reader().use { reader ->
            val parser = android.util.Xml.newPullParser()
            parser.setInput(reader)
            while (parser.eventType != org.xmlpull.v1.XmlPullParser.END_DOCUMENT) {
                if (parser.eventType == org.xmlpull.v1.XmlPullParser.START_TAG &&
                    parser.name == "boolean" && parser.getAttributeValue(null, "name") == "signed_out") {
                    return parser.getAttributeValue(null, "value").toBooleanStrict()
                }
                parser.next()
            }
        }
        error("Logout state was not persisted to disk")
    }
}
