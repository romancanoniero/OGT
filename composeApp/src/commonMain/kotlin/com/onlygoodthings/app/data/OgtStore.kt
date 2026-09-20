package com.onlygoodthings.app.data

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import com.onlygoodthings.app.auth.AuthController
import com.onlygoodthings.app.auth.AuthPrefs
import com.onlygoodthings.app.media.OgtMediaCache
import com.onlygoodthings.app.i18n.LocalOgtCopy
import com.onlygoodthings.app.i18n.OgtLang
import com.onlygoodthings.app.i18n.ogtCopy
import com.onlygoodthings.app.map.LocationPermissionState
import com.onlygoodthings.shared.data.local.LocalUser
import com.onlygoodthings.shared.data.local.OgtIds
import com.onlygoodthings.shared.data.local.OgtLocalDatabase
import com.onlygoodthings.shared.domain.GeoPoint
import com.onlygoodthings.shared.domain.ParkedCar
import com.onlygoodthings.shared.domain.VehicleProfile
import com.onlygoodthings.shared.realtime.OgtSdk
import com.onlygoodthings.shared.realtime.currentEpochMs
import com.onlygoodthings.shared.realtime.gatewayEndpointFromApiBase
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

val LocalOgtDb = staticCompositionLocalOf<OgtLocalDatabase> {
    error("OgtLocalDatabase no fue provisto. Envolvé la UI con OgtPreviewStore.")
}

val LocalOgtSession = staticCompositionLocalOf<OgtPreviewSession> {
    error("OgtPreviewSession no fue provisto.")
}

val LocalAuth = staticCompositionLocalOf<AuthController> {
    error("AuthController no fue provisto.")
}

/** Sesión de preview: Mariana recorre la app hasta que Firebase adjunta a la persona real. */
class OgtPreviewSession(
    val db: OgtLocalDatabase,
    initialUserId: String = OgtIds.Mariana,
    private val prefs: AuthPrefs = AuthPrefs(),
) {
    var currentUserId by mutableStateOf(initialUserId)
    var overlayUser by mutableStateOf<LocalUser?>(null)
    var radarEnabled by mutableStateOf(true)
    var locationGranted by mutableStateOf(false)
    var locationPermission by mutableStateOf(LocationPermissionState.UNKNOWN)
    var locationServicesEnabled by mutableStateOf(false)
    var locationAcquiring by mutableStateOf(false)
    var locationAccuracy by mutableStateOf<Float?>(null)
    /** Solo un fix real del dispositivo. Nunca el lat/lng del seed. */
    var deviceLocation by mutableStateOf<GeoPoint?>(null)
    var vehicles by mutableStateOf(loadGarage(prefs))
    var selectedVehicleId by mutableStateOf(prefs.selectedVehicleId)
    var vehicle by mutableStateOf(pickVehicle(loadGarage(prefs), prefs.selectedVehicleId))
    var parkedCar by mutableStateOf(loadParked(prefs))
    var focusParkedCar by mutableStateOf(false)
    var askLocationScope by mutableStateOf(false)
    var language by mutableStateOf(OgtLang.fromCode(prefs.appLanguage))
    /** Dijo que iba a dejar el lugar; esperamos que se acerque. */
    var willLeaveParked by mutableStateOf(false)
    var farthestFromParkedMeters by mutableStateOf(0.0)
    var yieldAsked by mutableStateOf(false)
    var askYieldApproach by mutableStateOf(false)
    /** Aviso temporal: ya se publicó la vacante y se está avisando a la comunidad. */
    var showYieldNotifying by mutableStateOf(false)
    var wasNearParkedCar by mutableStateOf(false)
    var sawVehicleDepart by mutableStateOf(false)
    var leaveAsked by mutableStateOf(false)
    var askLeaveWithoutYield by mutableStateOf(false)
    var staleAsked by mutableStateOf(false)
    var askStaleParked by mutableStateOf(false)
    var lastParkedConfirmEpochMs by mutableStateOf(loadParked(prefs)?.parkedAtEpochMs ?: 0L)
    /** Token de `onlygoodthings.app/h/{token}` pendiente de reivindicar. */
    var pendingHonorToken by mutableStateOf(prefs.pendingHonorToken.takeIf { it.isNotBlank() })

    init {
        db.importHonors(prefs.honorsJson)
        db.importPublishedAnimals(prefs.publishedAnimalsJson)
        db.postMedia.forEach { row ->
            OgtMediaCache.ingest(row.url)
            row.posterUrl?.let { OgtMediaCache.ingest(it) }
        }
        if (!hasParkedPlace()) forgetParked()
    }

    fun persistPublishedAnimals() {
        prefs.publishedAnimalsJson = db.exportPublishedAnimals()
    }

    fun rememberHonorToken(token: String?) {
        pendingHonorToken = token
        prefs.pendingHonorToken = token.orEmpty()
    }

    fun takeHonorReferrerSlot(): Boolean {
        if (prefs.honorReferrerConsumed) return false
        prefs.honorReferrerConsumed = true
        return true
    }

    fun persistHonors() {
        prefs.honorsJson = db.exportHonors()
    }

    fun applyLanguage(next: OgtLang) {
        language = next
        prefs.appLanguage = next.code
    }
    fun me(): LocalUser = overlayUser ?: db.user(currentUserId)
    fun here(): GeoPoint? = deviceLocation
    /** Lugar fijado al estacionar (ESTACIONE), no un pin residual ni el GPS del perfil. */
    fun hasParkedPlace(): Boolean {
        val car = parkedCar ?: return false
        if (!prefs.parkedSetByUser || car.parkedAtEpochMs <= 0L) return false
        if (!car.latitude.isFinite() || !car.longitude.isFinite()) return false
        if (car.latitude == 0.0 && car.longitude == 0.0) return false
        if (kotlin.math.abs(car.latitude) > 90.0 || kotlin.math.abs(car.longitude) > 180.0) return false
        return true
    }

    fun isParked(): Boolean = hasParkedPlace()
    fun saveVehicle(next: VehicleProfile) {
        upsertVehicle(next)
    }

    fun upsertVehicle(next: VehicleProfile): VehicleProfile {
        val ready = next.ensureId()
        vehicles = vehicles.filterNot {
            it.id == ready.id || it.plate.equals(ready.plate, ignoreCase = true)
        } + ready
        selectedVehicleId = ready.id
        vehicle = ready
        persistGarage()
        return ready
    }

    fun selectVehicle(id: String) {
        val found = vehicles.firstOrNull { it.id == id } ?: return
        selectedVehicleId = found.id
        vehicle = found
        persistGarage()
    }

    private fun persistGarage() {
        prefs.vehiclesJson = garageJson.encodeToString(vehicles)
        prefs.selectedVehicleId = selectedVehicleId
        prefs.vehicleMake = vehicle.make
        prefs.vehicleColor = vehicle.color
        prefs.vehicleColorHex = vehicle.colorHex
        prefs.vehiclePlate = vehicle.plate
    }
    fun rememberParked(car: ParkedCar) {
        val withCar = if (car.vehicle != null) car else car.copy(vehicle = vehicle.takeIf { it.isReady() })
        parkedCar = withCar
        prefs.parkedLatitude = withCar.latitude
        prefs.parkedLongitude = withCar.longitude
        prefs.parkedAtEpochMs = withCar.parkedAtEpochMs
        prefs.parkedHeading = withCar.headingDegrees ?: 0f
        prefs.parkedManeuver = withCar.maneuver.orEmpty()
        prefs.parkedAddress = withCar.address.orEmpty()
        prefs.parkedSetByUser = true
        withCar.vehicle?.let { upsertVehicle(it) }
        willLeaveParked = false
        farthestFromParkedMeters = 0.0
        yieldAsked = false
        askYieldApproach = false
        resetLeaveState()
        lastParkedConfirmEpochMs = withCar.parkedAtEpochMs
    }

    fun confirmStillParked() {
        val car = parkedCar ?: return
        val now = currentEpochMs()
        rememberParked(car.copy(parkedAtEpochMs = now))
        askStaleParked = false
        staleAsked = false
        lastParkedConfirmEpochMs = now
    }

    private fun resetLeaveState() {
        wasNearParkedCar = false
        sawVehicleDepart = false
        leaveAsked = false
        askLeaveWithoutYield = false
        staleAsked = false
        askStaleParked = false
    }

    fun forgetParked() {
        parkedCar = null
        prefs.parkedAtEpochMs = 0L
        prefs.parkedManeuver = ""
        prefs.parkedAddress = ""
        prefs.parkedSetByUser = false
        willLeaveParked = false
        farthestFromParkedMeters = 0.0
        yieldAsked = false
        askYieldApproach = false
        resetLeaveState()
        lastParkedConfirmEpochMs = 0L
    }
}

@Composable
fun OgtPreviewStore(content: @Composable () -> Unit) {
    val db = remember { OgtLocalDatabase.seeded() }
    val session = remember(db) { OgtPreviewSession(db) }
    val auth = remember(db, session) { AuthController(db, session) }
    remember(auth) {
        auth.ensureDevBearer()
        if (!OgtSdk.isStarted()) {
            runCatching {
                OgtSdk.start(
                    gatewayEndpoint = gatewayEndpointFromApiBase(auth.session.apiBaseUrl),
                    tokenProvider = auth.realtimeTokenProvider(),
                )
            }
        }
        true
    }
    LaunchedEffect(Unit) {
        auth.hydratePublishedAnimals(db)
        session.persistPublishedAnimals()
    }
    CompositionLocalProvider(
        LocalOgtDb provides db,
        LocalOgtSession provides session,
        LocalAuth provides auth,
        LocalOgtCopy provides ogtCopy(session.language),
        content = content,
    )
}

private val garageJson = Json { ignoreUnknownKeys = true; encodeDefaults = true }

private fun loadVehicle(prefs: AuthPrefs) = VehicleProfile(
    make = prefs.vehicleMake,
    color = prefs.vehicleColor,
    colorHex = prefs.vehicleColorHex,
    plate = prefs.vehiclePlate,
).ensureId()

private fun loadGarage(prefs: AuthPrefs): List<VehicleProfile> {
    val parsed = runCatching {
        if (prefs.vehiclesJson.isBlank()) emptyList()
        else garageJson.decodeFromString<List<VehicleProfile>>(prefs.vehiclesJson)
    }.getOrDefault(emptyList()).map { it.ensureId() }.filter { it.isReady() }
    if (parsed.isNotEmpty()) return parsed
    val legacy = loadVehicle(prefs)
    return if (legacy.isReady()) listOf(legacy) else emptyList()
}

private fun pickVehicle(garage: List<VehicleProfile>, selectedId: String): VehicleProfile =
    garage.firstOrNull { it.id == selectedId } ?: garage.firstOrNull() ?: VehicleProfile()

private fun loadParked(prefs: AuthPrefs): ParkedCar? {
    if (!prefs.parkedSetByUser || prefs.parkedAtEpochMs <= 0L) return null
    if (prefs.parkedLatitude == 0.0 && prefs.parkedLongitude == 0.0) return null
    val vehicle = loadVehicle(prefs).takeIf { it.isReady() }
    return ParkedCar(
        latitude = prefs.parkedLatitude,
        longitude = prefs.parkedLongitude,
        parkedAtEpochMs = prefs.parkedAtEpochMs,
        headingDegrees = prefs.parkedHeading.takeIf { it != 0f },
        maneuver = prefs.parkedManeuver.takeIf { it.isNotBlank() },
        address = prefs.parkedAddress.takeIf { it.isNotBlank() },
        vehicle = vehicle,
    )
}
