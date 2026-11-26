package com.onlygoodthings.app.map

import androidx.compose.runtime.*
import com.onlygoodthings.app.auth.AuthController
import com.onlygoodthings.app.data.OgtPreviewSession
import com.onlygoodthings.app.nav.OgtRoute
import com.onlygoodthings.shared.data.local.OgtLocalDatabase
import com.onlygoodthings.shared.domain.*
import com.onlygoodthings.shared.realtime.currentEpochMs

/** GPS sampling and parking detection stay active without invalidating the navigation composition. */
@Composable
fun LocationCoordinator(ui:LocationUiState,session:OgtPreviewSession,auth:AuthController,db:OgtLocalDatabase,route:OgtRoute,gpsAwaiting:Boolean,onGpsReady:()->Unit,onManeuver:(ParkingManeuver)->Unit) {
    val location = rememberOgtLocation(track = session.gpsEnabled)
    SideEffect { ui.update(location) }
    val detector = remember { DriveParkingDetector() }
    LaunchedEffect(location.permission, location.servicesEnabled, location.fix, location.acquiring) {
        session.locationPermission = location.permission
        session.locationGranted = location.permission.isGranted()
        session.locationServicesEnabled = location.servicesEnabled
        session.locationAcquiring = location.acquiring
        session.locationAccuracy = location.fix?.accuracyMeters
        session.deviceLocation = location.fix?.toGeoPoint()
    }
    LaunchedEffect(Unit) {
        OgtLocationSync.bind { lat, lng, acc ->
            auth.updateMyLocation(lat, lng, acc)
        }
    }
    LaunchedEffect(session.gpsEnabled, session.locationScope, location.permission, location.servicesEnabled) {
        OgtLocationSync.enabled = session.gpsEnabled && location.permission.isGranted()
        val always = session.locationScope == LocationScope.ALWAYS
        if (session.gpsEnabled && always && location.permission.covers(LocationScope.ALWAYS) && location.servicesEnabled) {
            OgtBackgroundLocation.start()
        } else {
            OgtBackgroundLocation.stop()
        }
    }
    LaunchedEffect(route, session.gpsEnabled, location.permission) {
        val needsFix = route == OgtRoute.Parking ||
            route == OgtRoute.ParkingConfirm ||
            route == OgtRoute.ParkHere ||
            route == OgtRoute.Map ||
            route == OgtRoute.PetsMap ||
            route == OgtRoute.ReportAnimal
        if (!session.gpsEnabled || !needsFix) return@LaunchedEffect
        when {
            // No saltar a Ajustes solos: tapa el diálogo del sistema y Play lo toma como permiso sin aviso.
            location.permission == LocationPermissionState.DENIED_FOREVER -> Unit
            !location.permission.covers(session.locationScope) -> location.ensureScope(session.locationScope)
            !location.servicesEnabled -> location.ensureServices()
            else -> location.resync()
        }
    }
    LaunchedEffect(location.fix?.epochMs, location.fix?.latitude, location.fix?.longitude) {
        val fix = location.fix ?: return@LaunchedEffect
        val found = detector.onSample(
            MotionSample(fix.latitude, fix.longitude, fix.speedMps, fix.bearingDegrees, fix.epochMs),
        )
        if (found != null) {
            val existing = session.parkedCar
            val alreadyHere = existing != null &&
                GeoMath.haversineMeters(existing.point(), GeoPoint(fix.latitude, fix.longitude)) < 40.0
            if (!alreadyHere) onManeuver(found)
        }
    }
    LaunchedEffect(location.permission, location.servicesEnabled, gpsAwaiting, route) {
        if (!gpsAwaiting) return@LaunchedEffect
        if (location.permission.isGranted() && !location.servicesEnabled) {
            location.ensureServices()
        }
        if (location.permission.isGranted()) {
            location.refreshNow()
            onGpsReady()
        }
    }
    LaunchedEffect(
        location.fix?.latitude,
        location.fix?.longitude,
        location.fix?.speedMps,
        location.fix?.epochMs,
        session.parkedCar,
        session.willLeaveParked,
        db.parkingEpoch,
    ) {
        val car = session.parkedCar ?: return@LaunchedEffect
        val fix = location.fix ?: return@LaunchedEffect
        val meters = GeoMath.haversineMeters(GeoPoint(fix.latitude, fix.longitude), car.point())
        if (meters > session.farthestFromParkedMeters) session.farthestFromParkedMeters = meters
        if (shouldAskYieldOnApproach(session.willLeaveParked, session.farthestFromParkedMeters, meters, session.yieldAsked)) {
            session.yieldAsked = true
            session.askYieldApproach = true
        }
        val yielding = db.parkingSpots.any {
            it.ownerUserId == session.me().id &&
                it.status in listOf(ParkingStatus.AVAILABLE, ParkingStatus.CLAIMED)
        }
        val confirmAt = session.lastParkedConfirmEpochMs.takeIf { it > 0L } ?: car.parkedAtEpochMs
        when (
            decideParkedPresence(
                ParkedPresenceTick(
                    metersFromCar = meters,
                    speedMps = fix.speedMps,
                    nowEpochMs = currentEpochMs(),
                    lastConfirmEpochMs = confirmAt,
                    wasNearCar = session.wasNearParkedCar,
                    sawVehicleDepart = session.sawVehicleDepart,
                    leaveAsked = session.leaveAsked,
                    hasActiveYield = yielding,
                    staleAsked = session.staleAsked,
                ),
            )
        ) {
            ParkedPresenceAction.MARK_NEAR -> session.wasNearParkedCar = true
            ParkedPresenceAction.ASK_LEAVE -> {
                session.sawVehicleDepart = true
                session.leaveAsked = true
                session.askLeaveWithoutYield = true
            }
            ParkedPresenceAction.FORGET_SILENT, ParkedPresenceAction.FORGET_STALE -> session.forgetParked()
            ParkedPresenceAction.ASK_STALE -> {
                session.staleAsked = true
                session.askStaleParked = true
            }
            ParkedPresenceAction.NONE -> Unit
        }
    }
}
