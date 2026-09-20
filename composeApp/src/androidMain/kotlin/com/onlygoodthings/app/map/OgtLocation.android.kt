package com.onlygoodthings.app.map

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.google.android.gms.common.api.ResolvableApiException
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.LocationSettingsRequest
import com.google.android.gms.location.Priority
import com.google.android.gms.location.CurrentLocationRequest
import com.google.android.gms.tasks.CancellationTokenSource
import com.onlygoodthings.shared.domain.LocationScope

@Composable
actual fun rememberOgtLocation(track: Boolean): OgtLocationState {
    val context = LocalContext.current
    val activity = context as? Activity
    val lifecycleOwner = LocalLifecycleOwner.current
    var permission by remember { mutableStateOf(readPermission(context)) }
    var servicesEnabled by remember { mutableStateOf(isLocationEnabled(context)) }
    var fix by remember { mutableStateOf<DeviceLocation?>(null) }
    var acquiring by remember { mutableStateOf(false) }
    var pendingAlways by remember { mutableStateOf(false) }

    val backgroundLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) {
        permission = readPermission(context, asked = true)
        servicesEnabled = isLocationEnabled(context)
        pendingAlways = false
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) {
        permission = readPermission(context, asked = true)
        servicesEnabled = isLocationEnabled(context)
        if (permission.isGranted() && pendingAlways && Build.VERSION.SDK_INT >= 29) {
            pendingAlways = false
            backgroundLauncher.launch(arrayOf(Manifest.permission.ACCESS_BACKGROUND_LOCATION))
        }
    }
    val settingsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult(),
    ) {
        servicesEnabled = isLocationEnabled(context)
    }

    fun openAppOrLocationSettings(forever: Boolean) {
        val intent = if (forever) {
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.fromParts("package", context.packageName, null)
            }
        } else {
            Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
        }
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }

    fun requestPermission(scope: LocationScope) {
        pendingAlways = scope == LocationScope.ALWAYS
        val current = readPermission(context)
        if (current == LocationPermissionState.GRANTED_ALWAYS) {
            permission = current
            return
        }
        if (current == LocationPermissionState.GRANTED && scope == LocationScope.WHILE_USING) {
            permission = current
            return
        }
        if (current == LocationPermissionState.DENIED_FOREVER) {
            permission = LocationPermissionState.DENIED_FOREVER
            openAppOrLocationSettings(forever = true)
            return
        }
        if (current.isGranted() && scope == LocationScope.ALWAYS && Build.VERSION.SDK_INT >= 29) {
            backgroundLauncher.launch(arrayOf(Manifest.permission.ACCESS_BACKGROUND_LOCATION))
            return
        }
        permissionLauncher.launch(
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION,
            ),
        )
    }

    fun pullCurrent() {
        if (!readPermission(context).isGranted()) return
        acquiring = true
        requestCurrentFix(context) { next ->
            if (next != null) fix = next
            acquiring = false
        }
    }

    fun resync() {
        permission = readPermission(context)
        servicesEnabled = isLocationEnabled(context)
        if (permission.isGranted() && servicesEnabled) {
            pullCurrent()
        } else if (!permission.isGranted()) {
            fix = null
            acquiring = false
        }
    }

    fun ensureServices() {
        servicesEnabled = isLocationEnabled(context)
        if (servicesEnabled) return
        if (!hasPlayServices(context) || activity == null) {
            openAppOrLocationSettings(forever = false)
            return
        }
        val request = LocationSettingsRequest.Builder()
            .addLocationRequest(highAccuracyRequest())
            .setAlwaysShow(true)
            .build()
        LocationServices.getSettingsClient(activity)
            .checkLocationSettings(request)
            .addOnSuccessListener { servicesEnabled = true }
            .addOnFailureListener { error ->
                val resolvable = error as? ResolvableApiException
                if (resolvable != null) {
                    settingsLauncher.launch(
                        IntentSenderRequest.Builder(resolvable.resolution).build(),
                    )
                } else {
                    openAppOrLocationSettings(forever = false)
                }
            }
    }

    LaunchedEffect(permission, servicesEnabled) {
        if (permission.isGranted() && servicesEnabled) {
            pullCurrent()
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) resync()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    DisposableEffect(permission, servicesEnabled, track) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context?, intent: Intent?) {
                servicesEnabled = isLocationEnabled(context)
            }
        }
        val filter = IntentFilter(LocationManager.PROVIDERS_CHANGED_ACTION).apply {
            addAction(LocationManager.MODE_CHANGED_ACTION)
        }
        ContextCompat.registerReceiver(context, receiver, filter, ContextCompat.RECEIVER_EXPORTED)

        val canListen = permission.isGranted() && servicesEnabled && track
        val session = if (canListen) startLiveUpdates(context) { next ->
            acquiring = false
            fix = next
        } else null
        if (canListen) acquiring = fix == null

        onDispose {
            runCatching { context.unregisterReceiver(receiver) }
            session?.close()
        }
    }

    return OgtLocationState(
        permission = permission,
        servicesEnabled = servicesEnabled,
        fix = fix,
        acquiring = acquiring,
        requestPermission = { requestPermission(it) },
        ensureServices = { ensureServices() },
        openSettings = { openAppOrLocationSettings(permission == LocationPermissionState.DENIED_FOREVER) },
        refreshNow = { pullCurrent() },
        resync = { resync() },
    )
}

private fun isEmulator(): Boolean {
    val fingerprint = Build.FINGERPRINT.lowercase()
    val model = Build.MODEL.lowercase()
    val product = Build.PRODUCT.lowercase()
    val hardware = Build.HARDWARE.lowercase()
    return fingerprint.contains("generic") ||
        fingerprint.contains("emulator") ||
        model.contains("sdk") ||
        model.contains("emulator") ||
        product.contains("sdk") ||
        product.contains("emulator") ||
        hardware.contains("ranchu") ||
        hardware.contains("goldfish") ||
        Build.MANUFACTURER.contains("Genymotion", ignoreCase = true)
}

private fun highAccuracyRequest(): LocationRequest {
    val emulator = isEmulator()
    return LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, if (emulator) 1_000L else 2_000L)
        .setMinUpdateIntervalMillis(if (emulator) 500L else 1_000L)
        .setMinUpdateDistanceMeters(if (emulator) 0f else 5f)
        .setWaitForAccurateLocation(!emulator)
        .build()
}

private fun hasPlayServices(context: Context): Boolean =
    GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(context) == ConnectionResult.SUCCESS

private fun isLocationEnabled(context: Context): Boolean {
    val manager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    return LocationManagerCompat.isLocationEnabled(manager)
}

private fun readPermission(context: Context, asked: Boolean = false): LocationPermissionState {
    val fine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
    val coarse = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION)
    val grantedForeground = fine == PackageManager.PERMISSION_GRANTED || coarse == PackageManager.PERMISSION_GRANTED
    if (grantedForeground) {
        val background = if (Build.VERSION.SDK_INT >= 29) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_BACKGROUND_LOCATION) ==
                PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
        return if (background) LocationPermissionState.GRANTED_ALWAYS else LocationPermissionState.GRANTED
    }
    val activity = context as? Activity
    val rationale = activity != null && (
        ActivityCompat.shouldShowRequestPermissionRationale(activity, Manifest.permission.ACCESS_FINE_LOCATION) ||
            ActivityCompat.shouldShowRequestPermissionRationale(activity, Manifest.permission.ACCESS_COARSE_LOCATION)
        )
    return when {
        asked && !rationale -> LocationPermissionState.DENIED_FOREVER
        asked -> LocationPermissionState.DENIED
        else -> LocationPermissionState.UNKNOWN
    }
}

private fun Location.isSimulated(): Boolean =
    if (Build.VERSION.SDK_INT >= 31) isMock else @Suppress("DEPRECATION") isFromMockProvider

/** El emulador inyecta GPS por Fused Location y lo marca isMock: eso sí es el fix del dispositivo. */
private fun Location.isRejectedMock(): Boolean = isSimulated() && !isEmulator()

private fun Location.toDeviceOrNull(): DeviceLocation? {
    if (isRejectedMock()) return null
    if (!latitude.isFinite() || !longitude.isFinite()) return null
    if (accuracy < 0f) return null
    return DeviceLocation(
        latitude = latitude,
        longitude = longitude,
        accuracyMeters = accuracy,
        speedMps = if (hasSpeed()) speed else null,
        bearingDegrees = if (hasBearing()) bearing else null,
        epochMs = time,
    )
}

@SuppressLint("MissingPermission")
private fun lastKnownFromManager(context: Context): DeviceLocation? {
    val manager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    return listOf(
        LocationManager.GPS_PROVIDER,
        LocationManager.NETWORK_PROVIDER,
        LocationManager.PASSIVE_PROVIDER,
    ).mapNotNull { provider ->
        runCatching { manager.getLastKnownLocation(provider) }.getOrNull()
    }.filter { !it.isRejectedMock() }.maxByOrNull { it.time }?.toDeviceOrNull()
}

@SuppressLint("MissingPermission")
private fun requestCurrentFix(context: Context, onResult: (DeviceLocation?) -> Unit) {
    val cached = lastKnownFromManager(context)
    if (cached != null) onResult(cached)
    if (!hasPlayServices(context)) {
        if (cached == null) onResult(null)
        return
    }
    val fused = LocationServices.getFusedLocationProviderClient(context)
    val cancel = CancellationTokenSource()
    val current = CurrentLocationRequest.Builder()
        .setPriority(Priority.PRIORITY_HIGH_ACCURACY)
        .setDurationMillis(15_000L)
        .setMaxUpdateAgeMillis(if (isEmulator()) 120_000L else 8_000L)
        .build()
    fused.getCurrentLocation(current, cancel.token)
        .addOnSuccessListener { location ->
            val live = location?.toDeviceOrNull()
            if (live != null) {
                onResult(live)
            } else {
                fused.lastLocation
                    .addOnSuccessListener { last ->
                        onResult(last?.toDeviceOrNull() ?: cached)
                    }
                    .addOnFailureListener { onResult(cached) }
            }
        }
        .addOnFailureListener { onResult(cached) }
}

private class LiveSession(val close: () -> Unit)

@SuppressLint("MissingPermission")
private fun startLiveUpdates(context: Context, onFix: (DeviceLocation) -> Unit): LiveSession {
    if (hasPlayServices(context)) {
        val fused = LocationServices.getFusedLocationProviderClient(context)
        val callback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                result.lastLocation?.toDeviceOrNull()?.let(onFix)
            }
        }
        fused.requestLocationUpdates(highAccuracyRequest(), callback, context.mainLooper)
        val manager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val gpsListener = android.location.LocationListener { location ->
            location.toDeviceOrNull()?.let(onFix)
        }
        if (isEmulator() && manager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
            manager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1_000L, 0f, gpsListener, context.mainLooper)
        }
        lastKnownFromManager(context)?.let(onFix)
        return LiveSession {
            fused.removeLocationUpdates(callback)
            runCatching { manager.removeUpdates(gpsListener) }
        }
    }
    val manager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    val listener = android.location.LocationListener { location ->
        location.toDeviceOrNull()?.let(onFix)
    }
    if (manager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
        manager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 2_000L, 5f, listener, context.mainLooper)
    }
    if (manager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
        manager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 4_000L, 15f, listener, context.mainLooper)
    }
    return LiveSession { runCatching { manager.removeUpdates(listener) } }
}
