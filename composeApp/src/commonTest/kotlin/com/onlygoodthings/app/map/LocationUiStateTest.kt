package com.onlygoodthings.app.map

import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.runtime.snapshots.SnapshotStateObserver
import com.onlygoodthings.shared.domain.LocationScope
import kotlin.test.*

class LocationUiStateTest {
    private fun state(fix:DeviceLocation?=null,permission:LocationPermissionState=LocationPermissionState.GRANTED,request:(LocationScope)->Unit={} )=
        OgtLocationState(permission,true,fix,false,request,{},{},{},{})

    @Test fun gpsSamplesOnlyInvalidateReadersOfTheFix() {
        val ui=LocationUiState();ui.update(state())
        val observer=SnapshotStateObserver { it() };observer.start()
        val navigation=Any();val map=Any()
        var navigationChanges=0;var mapChanges=0
        try {
            observer.observeReads(navigation,{navigationChanges++}) { ui.permission;ui.servicesEnabled }
            observer.observeReads(map,{mapChanges++}) { ui.fix }
            ui.update(state(DeviceLocation(1.0,2.0,epochMs=1)))
            Snapshot.sendApplyNotifications()
            assertEquals(0,navigationChanges)
            assertEquals(1,mapChanges)
            ui.update(state(permission=LocationPermissionState.DENIED))
            Snapshot.sendApplyNotifications()
            assertEquals(1,navigationChanges)
        } finally { observer.stop();observer.clear() }
    }
    @Test fun updatedCallbacksAndPermissionRequestsArePreserved() {
        val ui=LocationUiState();val calls=mutableListOf<String>()
        ui.update(state(permission=LocationPermissionState.DENIED,request={calls+="old"}))
        ui.update(state(permission=LocationPermissionState.DENIED,request={calls+="new:$it"}))
        ui.ensureScope(LocationScope.WHILE_USING)
        assertEquals(listOf("new:WHILE_USING"),calls)
        assertFalse(ui.hasLiveFix)
        assertFalse(ui.canTrack)
        ui.update(state(DeviceLocation(1.0,2.0),LocationPermissionState.GRANTED_ALWAYS))
        assertTrue(ui.hasLiveFix);assertTrue(ui.canTrack);assertTrue(ui.alwaysGranted)
    }
}
