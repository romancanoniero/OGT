package com.onlygoodthings.app.map

import com.onlygoodthings.app.nav.OgtRoute
import com.onlygoodthings.shared.domain.LocationScope

enum class LocationRequirement {
    APP_DISABLED, UNSUPPORTED, PERMISSION_BLOCKED, PERMISSION_REQUIRED,
    SYSTEM_DISABLED, ACQUIRING, NO_FIX, READY,
}

fun locationRequirement(enabled:Boolean,permission:LocationPermissionState,services:Boolean,hasFix:Boolean,acquiring:Boolean):LocationRequirement = when {
    !enabled -> LocationRequirement.APP_DISABLED
    permission==LocationPermissionState.UNSUPPORTED -> LocationRequirement.UNSUPPORTED
    permission==LocationPermissionState.DENIED_FOREVER -> LocationRequirement.PERMISSION_BLOCKED
    !permission.isGranted() -> LocationRequirement.PERMISSION_REQUIRED
    !services -> LocationRequirement.SYSTEM_DISABLED
    hasFix -> LocationRequirement.READY
    acquiring -> LocationRequirement.ACQUIRING
    else -> LocationRequirement.NO_FIX
}

/** Browsing a saved car or choosing a manual address remains available without GPS. */
fun promptsForParkingLocation(route:OgtRoute,hasParkedPlace:Boolean=false):Boolean =
    (route==OgtRoute.Parking && !hasParkedPlace) || route==OgtRoute.ParkingConfirm

/** Resolve one prerequisite at a time; enabling collection always uses the disclosure first. */
fun ensureLocationAccess(enabled:Boolean,gps:LocationUiState,onEnableApp:()->Unit):Boolean = when {
    !enabled -> { onEnableApp();false }
    gps.permission==LocationPermissionState.UNSUPPORTED -> false
    gps.permission==LocationPermissionState.DENIED_FOREVER -> { gps.openSettings();false }
    !gps.permission.isGranted() -> { gps.requestPermission(LocationScope.WHILE_USING);false }
    !gps.servicesEnabled -> { gps.ensureServices();false }
    else -> true
}

/** Called only after the user confirms the disclosure or explicitly changes its scope. */
fun requestLocationAuthorization(gps:LocationUiState,scope:LocationScope) {
    when {
        gps.permission==LocationPermissionState.UNSUPPORTED -> Unit
        gps.permission==LocationPermissionState.DENIED_FOREVER -> gps.openSettings()
        !gps.permission.covers(scope) -> gps.requestPermission(scope)
    }
}
