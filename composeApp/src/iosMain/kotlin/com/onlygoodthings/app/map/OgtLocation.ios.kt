package com.onlygoodthings.app.map

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.useContents
import platform.CoreLocation.CLAuthorizationStatus
import platform.CoreLocation.CLLocation
import platform.CoreLocation.CLLocationManager
import platform.CoreLocation.CLLocationManagerDelegateProtocol
import platform.CoreLocation.kCLAuthorizationStatusAuthorizedAlways
import platform.CoreLocation.kCLAuthorizationStatusAuthorizedWhenInUse
import platform.CoreLocation.kCLAuthorizationStatusDenied
import platform.CoreLocation.kCLAuthorizationStatusNotDetermined
import platform.CoreLocation.kCLAuthorizationStatusRestricted
import platform.CoreLocation.kCLLocationAccuracyBestForNavigation
import platform.Foundation.NSNotificationCenter
import platform.Foundation.NSProcessInfo
import platform.Foundation.NSURL
import platform.UIKit.UIApplication
import platform.UIKit.UIApplicationDidBecomeActiveNotification
import platform.UIKit.UIApplicationOpenSettingsURLString
import com.onlygoodthings.shared.domain.LocationScope
import com.onlygoodthings.shared.realtime.currentEpochMs
import platform.darwin.NSObject

@Composable
actual fun rememberOgtLocation(track: Boolean): OgtLocationState {
    val holder = IosLocationRuntime
    var permission by remember { mutableStateOf(holder.permission()) }
    var servicesEnabled by remember { mutableStateOf(CLLocationManager.locationServicesEnabled()) }
    var fix by remember { mutableStateOf(holder.lastFix) }
    var acquiring by remember { mutableStateOf(holder.acquiring) }

    DisposableEffect(track, permission, servicesEnabled) {
        holder.onChange = {
            permission = holder.permission()
            servicesEnabled = CLLocationManager.locationServicesEnabled()
            fix = holder.lastFix
            acquiring = holder.acquiring
        }
        if (track && permission.isGranted() && servicesEnabled) {
            acquiring = true
            holder.start(background = permission == LocationPermissionState.GRANTED_ALWAYS)
        } else if (!track) {
            holder.stop()
            acquiring = false
        }
        val observer = NSNotificationCenter.defaultCenter.addObserverForName(
            UIApplicationDidBecomeActiveNotification,
            null,
            null,
        ) { _ ->
            permission = holder.permission()
            servicesEnabled = CLLocationManager.locationServicesEnabled()
            fix = holder.lastFix
            if (track && permission.isGranted() && servicesEnabled) holder.refresh()
        }
        onDispose {
            NSNotificationCenter.defaultCenter.removeObserver(observer)
            holder.onChange = null
            // No stop(): el runtime de proceso sigue midiendo en segundo plano.
        }
    }

    return OgtLocationState(
        permission = permission,
        servicesEnabled = servicesEnabled,
        fix = fix,
        acquiring = acquiring,
        requestPermission = { scope ->
            holder.request(scope)
            permission = holder.permission()
            servicesEnabled = CLLocationManager.locationServicesEnabled()
            if (permission.isGranted() && servicesEnabled) {
                holder.start(background = scope == LocationScope.ALWAYS || permission == LocationPermissionState.GRANTED_ALWAYS)
            }
        },
        ensureServices = {
            servicesEnabled = CLLocationManager.locationServicesEnabled()
            if (!servicesEnabled) holder.openSettings()
        },
        openSettings = { holder.openSettings() },
        refreshNow = {
            if (permission.isGranted()) {
                acquiring = true
                holder.refresh()
            }
        },
        resync = {
            permission = holder.permission()
            servicesEnabled = CLLocationManager.locationServicesEnabled()
            fix = holder.lastFix
            if (permission.isGranted() && servicesEnabled) holder.refresh()
            else if (!permission.isGranted()) {
                fix = null
                acquiring = false
            }
        },
    )
}

@OptIn(ExperimentalForeignApi::class)
internal object IosLocationRuntime {
    var lastFix: DeviceLocation? = null
    var acquiring: Boolean = false
    var onChange: (() -> Unit)? = null
    var pendingAlways: Boolean = false
    /** iOS solo muestra “Siempre” una vez; el segundo intento abre Ajustes. */
    var askedAlways: Boolean = false
    private val manager = CLLocationManager()
    private val delegate = IosLocationDelegate()

    init {
        manager.delegate = delegate
        manager.desiredAccuracy = kCLLocationAccuracyBestForNavigation
        manager.distanceFilter = 5.0
        manager.pausesLocationUpdatesAutomatically = true
    }

    fun permission(): LocationPermissionState = mapStatus(manager.authorizationStatus)

    fun request(scope: LocationScope) {
        pendingAlways = scope == LocationScope.ALWAYS
        when (manager.authorizationStatus) {
            kCLAuthorizationStatusNotDetermined -> manager.requestWhenInUseAuthorization()
            kCLAuthorizationStatusAuthorizedWhenInUse -> {
                if (scope != LocationScope.ALWAYS) {
                    start(background = false)
                    return
                }
                if (askedAlways) {
                    openSettings()
                    return
                }
                askedAlways = true
                manager.requestAlwaysAuthorization()
            }
            kCLAuthorizationStatusAuthorizedAlways -> start(background = true)
            else -> openSettings()
        }
    }

    fun start(background: Boolean) {
        acquiring = true
        val always = manager.authorizationStatus == kCLAuthorizationStatusAuthorizedAlways
        if (background && always) {
            manager.allowsBackgroundLocationUpdates = true
            manager.showsBackgroundLocationIndicator = true
            manager.pausesLocationUpdatesAutomatically = false
        }
        manager.startUpdatingLocation()
        manager.requestLocation()
        apply(manager.location)
        onChange?.invoke()
    }

    fun refresh() {
        acquiring = true
        manager.requestLocation()
        onChange?.invoke()
    }

    fun stop() {
        manager.allowsBackgroundLocationUpdates = false
        manager.showsBackgroundLocationIndicator = false
        manager.pausesLocationUpdatesAutomatically = true
        manager.stopUpdatingLocation()
        acquiring = false
    }

    fun apply(location: CLLocation?) {
        val next = location.toDeviceOrNull() ?: return
        lastFix = next
        acquiring = false
        OgtLocationSync.offer(next)
        onChange?.invoke()
    }

    fun openSettings() {
        val url = NSURL.URLWithString(UIApplicationOpenSettingsURLString) ?: return
        UIApplication.sharedApplication.openURL(url)
    }
}

private class IosLocationDelegate : NSObject(), CLLocationManagerDelegateProtocol {
    override fun locationManager(manager: CLLocationManager, didUpdateLocations: List<*>) {
        val location = didUpdateLocations.lastOrNull() as? CLLocation ?: return
        IosLocationRuntime.apply(location)
    }

    override fun locationManager(manager: CLLocationManager, didFailWithError: platform.Foundation.NSError) {
        IosLocationRuntime.acquiring = false
        IosLocationRuntime.onChange?.invoke()
    }

    override fun locationManagerDidChangeAuthorization(manager: CLLocationManager) {
        if (IosLocationRuntime.pendingAlways && IosLocationRuntime.permission() == LocationPermissionState.GRANTED) {
            IosLocationRuntime.pendingAlways = false
            manager.requestAlwaysAuthorization()
            return
        }
        IosLocationRuntime.onChange?.invoke()
        if (IosLocationRuntime.permission().isGranted() &&
            CLLocationManager.locationServicesEnabled()
        ) {
            IosLocationRuntime.start(
                background = IosLocationRuntime.permission() == LocationPermissionState.GRANTED_ALWAYS,
            )
        }
    }
}

@OptIn(ExperimentalForeignApi::class)
private fun CLLocation?.toDeviceOrNull(): DeviceLocation? {
    val location = this ?: return null
    if (location.horizontalAccuracy < 0) return null
    val simulated = runCatching {
        location.sourceInformation?.isSimulatedBySoftware() == true
    }.getOrDefault(false)
    val isSimulator = NSProcessInfo.processInfo.environment["SIMULATOR_DEVICE_NAME"] != null
    if (simulated && !isSimulator) return null
    return location.coordinate.useContents {
        DeviceLocation(
            latitude = latitude,
            longitude = longitude,
            accuracyMeters = location.horizontalAccuracy.toFloat(),
            speedMps = if (location.speed >= 0) location.speed.toFloat() else null,
            bearingDegrees = if (location.course >= 0) location.course.toFloat() else null,
            epochMs = currentEpochMs(),
        )
    }
}

private fun mapStatus(status: CLAuthorizationStatus): LocationPermissionState = when (status) {
    kCLAuthorizationStatusAuthorizedAlways -> LocationPermissionState.GRANTED_ALWAYS
    kCLAuthorizationStatusAuthorizedWhenInUse -> LocationPermissionState.GRANTED
    kCLAuthorizationStatusDenied -> LocationPermissionState.DENIED_FOREVER
    kCLAuthorizationStatusRestricted -> LocationPermissionState.DENIED
    kCLAuthorizationStatusNotDetermined -> LocationPermissionState.UNKNOWN
    else -> LocationPermissionState.UNKNOWN
}
