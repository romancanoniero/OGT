package com.onlygoodthings.app.data

import com.onlygoodthings.shared.domain.ParkedCar
import com.onlygoodthings.shared.domain.VehicleProfile
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AccountParkingStoreTest {
    private val carA = VehicleProfile("a", "Auto A", "Gris", plate = "AA123BB")
    private val carB = VehicleProfile("b", "Auto B", "Azul", plate = "BB456CC")
    private val parked = ParkedCar(-34.6, -58.4, 123L, address = "Lugar de A", vehicle = carA)

    @Test fun accountsKeepSeparateGaragesSelectionsAndLocationsAcrossRestart() {
        var disk = ""
        val store = AccountParkingStore({ disk }, { disk = it })
        val a = AccountParking(listOf(carA), carA.id, parked)
        val b = AccountParking(listOf(carB), carB.id)
        store.save("firebase-A", a)
        assertEquals(AccountParking(), store.load("firebase-B"))
        assertEquals(AccountParking(), store.load(""))
        store.save("firebase-B", b)
        val restarted = AccountParkingStore({ disk }, { disk = it })
        assertEquals(a, restarted.load("firebase-A"))
        assertEquals(b, restarted.load("firebase-B"))
        assertNull(restarted.load("firebase-B").parkedCar)
        restarted.save("firebase-B", AccountParking())
        assertEquals(a, restarted.load("firebase-A"))
    }

    @Test fun signedOutCannotReadOrPersistAnotherAccountsParking() {
        var disk = ""
        val store = AccountParkingStore({ disk }, { disk = it })
        store.save("", AccountParking(listOf(carA), carA.id, parked))
        assertEquals("", disk)
        assertTrue(store.load("new-account").vehicles.isEmpty())
    }

    @Test fun accountDeletionRemovesOnlyItsOwnData() {
        var disk = ""
        val store = AccountParkingStore({ disk }, { disk = it })
        val b = AccountParking(listOf(carB), carB.id)
        store.save("A", AccountParking(listOf(carA), carA.id, parked))
        store.save("B", b)
        store.remove("A")
        assertEquals(AccountParking(), store.load("A"))
        assertEquals(b, store.load("B"))
    }

    @Test fun corruptedStorageCannotBeSilentlyOverwritten() {
        var disk = "corrupted"
        val store = AccountParkingStore({ disk }, { disk = it })
        assertFails { store.save("A", AccountParking()) }
        assertEquals("corrupted", disk)
    }
}
