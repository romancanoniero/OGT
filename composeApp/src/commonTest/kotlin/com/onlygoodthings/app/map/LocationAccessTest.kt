package com.onlygoodthings.app.map

import com.onlygoodthings.app.nav.OgtRoute
import com.onlygoodthings.shared.domain.LocationScope
import kotlin.test.*

class LocationAccessTest {
    private fun state(permission:LocationPermissionState,services:Boolean,calls:MutableList<String>) = OgtLocationState(
        permission,services,null,false,
        requestPermission={calls+="permission:$it"},ensureServices={calls+="services"},
        openSettings={calls+="settings"},refreshNow={calls+="fix"},resync={calls+="resync"},
    )
    @Test fun disabledInOgtTakesPrecedenceEvenWithPermissionsAndAnOldFix() {
        assertEquals(LocationRequirement.APP_DISABLED,locationRequirement(false,LocationPermissionState.GRANTED_ALWAYS,true,true,false))
        assertEquals(LocationRequirement.APP_DISABLED,locationRequirement(false,LocationPermissionState.DENIED_FOREVER,false,false,false))
    }
    @Test fun permissionSystemLocationAcquisitionAndNoSignalAreDifferentStates() {
        assertEquals(LocationRequirement.PERMISSION_REQUIRED,locationRequirement(true,LocationPermissionState.UNKNOWN,false,false,false))
        assertEquals(LocationRequirement.PERMISSION_REQUIRED,locationRequirement(true,LocationPermissionState.DENIED,true,false,false))
        assertEquals(LocationRequirement.PERMISSION_BLOCKED,locationRequirement(true,LocationPermissionState.DENIED_FOREVER,false,false,false))
        assertEquals(LocationRequirement.SYSTEM_DISABLED,locationRequirement(true,LocationPermissionState.GRANTED,false,false,false))
        assertEquals(LocationRequirement.ACQUIRING,locationRequirement(true,LocationPermissionState.GRANTED,true,false,true))
        assertEquals(LocationRequirement.NO_FIX,locationRequirement(true,LocationPermissionState.GRANTED,true,false,false))
        assertEquals(LocationRequirement.READY,locationRequirement(true,LocationPermissionState.GRANTED,true,true,false))
        assertEquals(LocationRequirement.UNSUPPORTED,locationRequirement(true,LocationPermissionState.UNSUPPORTED,true,false,false))
    }
    @Test fun anActionWithGpsOffOnlyOpensTheDisclosureAndNeverEnablesTrackingOrRequestsPermissions() {
        val calls=mutableListOf<String>();val gps=LocationUiState()
        gps.update(state(LocationPermissionState.UNKNOWN,false,calls))
        assertFalse(ensureLocationAccess(false,gps) {calls+="disclosure"})
        assertEquals(listOf("disclosure"),calls)
    }
    @Test fun foregroundPermissionComesBeforeSystemSettingsAndOnlyThenTheFixIsRequested() {
        val calls=mutableListOf<String>();val gps=LocationUiState()
        gps.update(state(LocationPermissionState.UNKNOWN,false,calls))
        assertFalse(ensureLocationAccess(true,gps) {calls+="disclosure"})
        assertEquals(listOf("permission:WHILE_USING"),calls)
        gps.update(state(LocationPermissionState.GRANTED,false,calls))
        assertFalse(ensureLocationAccess(true,gps) {calls+="disclosure"})
        assertEquals(listOf("permission:WHILE_USING","services"),calls)
        gps.update(state(LocationPermissionState.GRANTED,true,calls))
        if(ensureLocationAccess(true,gps) {calls+="disclosure"})gps.refreshNow()
        assertEquals(listOf("permission:WHILE_USING","services","fix"),calls)
    }
    @Test fun aBlockedPermissionOpensAppSettingsOnlyAfterTheUserAction() {
        val calls=mutableListOf<String>();val gps=LocationUiState()
        gps.update(state(LocationPermissionState.DENIED_FOREVER,false,calls))
        assertTrue(calls.isEmpty())
        assertFalse(ensureLocationAccess(true,gps) {calls+="disclosure"})
        assertEquals(listOf("settings"),calls)
    }
    @Test fun whileUsingPermissionIsEnoughForParkingAndDoesNotRequestAlways() {
        for(permission in listOf(LocationPermissionState.GRANTED,LocationPermissionState.GRANTED_ALWAYS)) {
            val calls=mutableListOf<String>();val gps=LocationUiState();gps.update(state(permission,true,calls))
            assertTrue(ensureLocationAccess(true,gps) {calls+="disclosure"})
            assertTrue(calls.isEmpty())
        }
    }
    @Test fun anExplicitAlwaysChoiceIsPreservedAndAlreadyGrantedPermissionsAreNotRequestedAgain() {
        val calls=mutableListOf<String>();val gps=LocationUiState()
        gps.update(state(LocationPermissionState.GRANTED,true,calls))
        requestLocationAuthorization(gps,LocationScope.WHILE_USING)
        assertTrue(calls.isEmpty())
        requestLocationAuthorization(gps,LocationScope.ALWAYS)
        assertEquals(listOf("permission:ALWAYS"),calls)
        calls.clear();gps.update(state(LocationPermissionState.GRANTED_ALWAYS,true,calls))
        requestLocationAuthorization(gps,LocationScope.ALWAYS)
        assertTrue(calls.isEmpty())
    }
    @Test fun confirmingActivationWithABlockedPermissionGuidesToSettingsWithoutAnotherPermissionPrompt() {
        val calls=mutableListOf<String>();val gps=LocationUiState()
        gps.update(state(LocationPermissionState.DENIED_FOREVER,false,calls))
        requestLocationAuthorization(gps,LocationScope.WHILE_USING)
        assertEquals(listOf("settings"),calls)
    }
    @Test fun unsupportedDevicesDoNotLaunchPermissionOrSettingsRequests() {
        val calls=mutableListOf<String>();val gps=LocationUiState();gps.update(state(LocationPermissionState.UNSUPPORTED,false,calls))
        assertFalse(ensureLocationAccess(true,gps) {calls+="disclosure"})
        assertTrue(calls.isEmpty())
    }
    @Test fun automaticPromptIsLimitedToParkingThatNeedsALivePosition() {
        assertTrue(promptsForParkingLocation(OgtRoute.Parking))
        assertTrue(promptsForParkingLocation(OgtRoute.ParkingConfirm))
        assertFalse(promptsForParkingLocation(OgtRoute.Parking,hasParkedPlace=true))
        for(route in listOf(OgtRoute.Feed,OgtRoute.Settings,OgtRoute.ParkHere,OgtRoute.ReportAnimal,OgtRoute.Map,OgtRoute.PetsMap))
            assertFalse(promptsForParkingLocation(route))
    }
}
