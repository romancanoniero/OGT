package com.onlygoodthings.app.data

import com.onlygoodthings.shared.domain.AccountSettings
import kotlin.test.*

class AccountSettingsStoreTest {
    @Test fun settingsSurviveReopeningWithoutCrossingAccountsAndDeletionPreservesTheOtherOwner() {
        var raw=""
        val store=AccountSettingsStore({raw},{raw=it})
        val a=AccountSettings(publicProfileVisible=false,animalAlertPush=false,radarEnabled=false)
        val b=AccountSettings(skillAlertPush=false,parkingRadarSounds=false)
        store.save("firebase-A",a);store.save("firebase-B",b)
        val reopened=AccountSettingsStore({raw},{raw=it})
        assertEquals(a,reopened.load("firebase-A"));assertEquals(b,reopened.load("firebase-B"))
        assertEquals(AccountSettings(),reopened.load(""));assertEquals(AccountSettings(),reopened.load("new-account"))
        reopened.remove("firebase-A");assertEquals(b,reopened.load("firebase-B"))
        assertEquals(AccountSettings(),reopened.load("firebase-A"))
    }
    @Test fun blankOwnersCannotWriteAndCorruptStorageIsNotSilentlyOverwritten() {
        var raw="not-json"
        val store=AccountSettingsStore({raw},{raw=it})
        store.save("",AccountSettings());assertEquals("not-json",raw)
        assertFails { store.save("A",AccountSettings()) };assertEquals("not-json",raw)
    }
}
