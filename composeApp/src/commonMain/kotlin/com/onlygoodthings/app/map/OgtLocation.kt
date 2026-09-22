package com.onlygoodthings.app.map

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import com.onlygoodthings.shared.domain.GeoPoint
import com.onlygoodthings.shared.domain.LocationScope

enum class LocationPermissionState {
    UNKNOWN,
    GRANTED,
    GRANTED_ALWAYS,
    DENIED,
    DENIED_FOREVER,
    UNSUPPORTED,
}

fun LocationPermissionState.isGranted(): Boolean =
    this == LocationPermissionState.GRANTED || this == LocationPermissionState.GRANTED_ALWAYS

/** “Siempre” no alcanza con el permiso de primer plano. */
fun LocationPermissionState.covers(scope: LocationScope): Boolean = when (scope) {
    LocationScope.ALWAYS -> this == LocationPermissionState.GRANTED_ALWAYS
    LocationScope.WHILE_USING -> isGranted()
}

/** Fix real del chip GPS / Fused Location. Nunca coordenadas de seed. */
data class DeviceLocation(
    val latitude: Double,
    val longitude: Double,
    val accuracyMeters: Float? = null,
    val speedMps: Float? = null,
    val bearingDegrees: Float? = null,
    val epochMs: Long = 0L,
) {
    fun toGeoPoint(): GeoPoint = GeoPoint(latitude, longitude)
}

/** Estado de GPS nativo. Las acciones abren diálogos reales del sistema. */
data class OgtLocationState(
    val permission: LocationPermissionState,
    val servicesEnabled: Boolean,
    val fix: DeviceLocation?,
    val acquiring: Boolean,
    val requestPermission: (com.onlygoodthings.shared.domain.LocationScope) -> Unit,
    val ensureServices: () -> Unit,
    val openSettings: () -> Unit,
    val refreshNow: () -> Unit,
    val resync: () -> Unit,
) {
    val hasLiveFix: Boolean = fix != null
    val canTrack: Boolean = permission.isGranted() && servicesEnabled
    val alwaysGranted: Boolean = permission == LocationPermissionState.GRANTED_ALWAYS

    fun ensureScope(scope: LocationScope) {
        if (permission.covers(scope)) {
            if (!servicesEnabled) ensureServices()
            return
        }
        if (permission == LocationPermissionState.DENIED_FOREVER) {
            openSettings()
            return
        }
        requestPermission(scope)
        if (!servicesEnabled) ensureServices()
    }
}

val LocalOgtLocation = staticCompositionLocalOf<OgtLocationState> {
    error("OgtLocationState no fue provisto. Envolvé la UI con rememberOgtLocation.")
}

@Composable
expect fun rememberOgtLocation(track: Boolean): OgtLocationState

/** Zoom aproximado para que [radiusMeters] quepa en el viewport. */
fun zoomForRadiusMeters(radiusMeters: Int): Double = when {
    radiusMeters <= 400 -> 16.0
    radiusMeters <= 500 -> 15.0
    radiusMeters <= 1000 -> 14.0
    else -> 13.0
}
