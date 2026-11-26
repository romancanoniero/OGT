package com.onlygoodthings.app.map

import androidx.compose.runtime.*
import com.onlygoodthings.shared.domain.LocationScope

/** Readers subscribe to only the fields they display, not to every GPS sample or callback. */
@Stable
class LocationUiState {
    var permission by mutableStateOf(LocationPermissionState.UNKNOWN);private set
    var servicesEnabled by mutableStateOf(false);private set
    var fix by mutableStateOf<DeviceLocation?>(null);private set
    var acquiring by mutableStateOf(false);private set
    private var actions:OgtLocationState?=null
    fun update(value:OgtLocationState) {
        actions=value
        permission=value.permission;servicesEnabled=value.servicesEnabled;fix=value.fix;acquiring=value.acquiring
    }
    val hasLiveFix:Boolean get()=fix!=null
    val canTrack:Boolean get()=permission.isGranted() && servicesEnabled
    val alwaysGranted:Boolean get()=permission==LocationPermissionState.GRANTED_ALWAYS
    fun openSettings() { actions?.openSettings?.invoke() }
    fun requestPermission(scope:LocationScope) { actions?.requestPermission?.invoke(scope) }
    fun ensureServices() { actions?.ensureServices?.invoke() }
    fun refreshNow() { actions?.refreshNow?.invoke() }
    fun ensureScope(scope:LocationScope) { actions?.ensureScope(scope) }
    fun resync() { actions?.resync?.invoke() }
}
