package com.onlygoodthings.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.onlygoodthings.app.data.LocalOgtDb
import com.onlygoodthings.app.data.LocalOgtSession
import com.onlygoodthings.app.i18n.LocalOgtCopy
import com.onlygoodthings.app.theme.OgtColors
import com.onlygoodthings.app.ui.components.BarrioMarker
import com.onlygoodthings.app.ui.components.OgtBarrioMap
import com.onlygoodthings.app.ui.components.OgtCard
import com.onlygoodthings.app.ui.components.OgtCaption
import com.onlygoodthings.app.ui.components.OgtGpsBanner
import com.onlygoodthings.app.ui.components.gpsStatusLabel
import com.onlygoodthings.app.ui.components.OgtPill
import com.onlygoodthings.app.ui.components.OgtPrimaryButton
import com.onlygoodthings.app.ui.components.OgtSectionTitle
import com.onlygoodthings.app.ui.components.OgtTopBar
import com.onlygoodthings.app.ui.components.ScreenColumn
import com.onlygoodthings.app.ui.components.ogtDismissImeOnScroll
import com.onlygoodthings.shared.data.local.LocalParkingRepository
import com.onlygoodthings.shared.domain.ParkingMatchingPhase
import com.onlygoodthings.shared.domain.ParkingSpot
import com.onlygoodthings.shared.domain.matchingPhase
import com.onlygoodthings.shared.realtime.currentEpochMs
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun MapScreen(onBack: () -> Unit = {}, onClaimed: (String) -> Unit = {}) {
    val db = LocalOgtDb.current
    val session = LocalOgtSession.current
    val me = session.me()
    val repo = remember(db, me.id) { LocalParkingRepository(db, me.id) }
    val scope = rememberCoroutineScope()
    val here = session.here()
    val hasGps = here != null
    val mapCenter = when {
        session.focusParkedCar && session.isParked() -> session.parkedCar!!.point()
        here != null -> here
        else -> null
    }
    var radius by remember { mutableStateOf(1_000) }
    var query by remember { mutableStateOf("") }
    var spots by remember { mutableStateOf<List<ParkingSpot>>(emptyList()) }
    var selectedId by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var claimWait by remember { mutableStateOf<ClaimWaitUi?>(null) }
    var claimJob by remember { mutableStateOf<Job?>(null) }
    val copy = LocalOgtCopy.current

    fun abortClaim() {
        val id = claimWait?.spotId
        claimJob?.cancel()
        claimWait = null
        if (id != null) {
            scope.launch {
                runCatching { repo.cancel(id) }
            }
        }
    }

    fun askForSpot(spot: ParkingSpot) {
        if (claimWait != null) return
        val owner = db.userOrNull(spot.ownerUserId)
        claimWait = ClaimWaitUi(
            spotId = spot.id,
            address = spot.address?.takeIf { it.isNotBlank() } ?: copy.plaza,
            vehicle = spot.vehicleLabel,
            ownerName = owner?.displayName ?: copy.neighbor,
        )
        claimJob = scope.launch {
            try {
                val fix = session.here() ?: error("Necesitamos un fix GPS real")
                val phase = spot.matchingPhase(currentEpochMs())
                if (phase == ParkingMatchingPhase.INTEREST) {
                    repo.expressInterest(spot.id, fix)
                } else {
                    repo.claim(spot.id, spot.version, fix)
                }
                delay(2_800)
                claimWait = claimWait?.copy(confirmed = true)
                delay(750)
                val id = claimWait?.spotId ?: spot.id
                claimWait = null
                onClaimed(id)
            } catch (ex: CancellationException) {
                throw ex
            } catch (ex: Exception) {
                error = ex.message
                claimWait = null
            }
        }
    }

    LaunchedEffect(radius, db.parkingEpoch, here?.latitude, here?.longitude) {
        spots = if (here != null) repo.nearby(here, radius) else emptyList()
        if (selectedId == null) selectedId = spots.firstOrNull()?.id
    }

    val visibleSpots = spots.filter { spot ->
        query.isBlank() ||
            (spot.address?.contains(query, ignoreCase = true) == true) ||
            (spot.notes?.contains(query, ignoreCase = true) == true)
    }
    val selected = visibleSpots.firstOrNull { it.id == selectedId } ?: visibleSpots.firstOrNull()
    val markers = buildList {
        if (here != null) add(BarrioMarker("me", "ME", copy.mePin, here.latitude, here.longitude))
        if (session.isParked()) {
            session.parkedCar?.let { car ->
                add(BarrioMarker("parked", "CAR", copy.myCarPin, car.latitude, car.longitude))
            }
        }
        visibleSpots.forEach { spot ->
            add(
                BarrioMarker(
                    id = spot.id,
                    kind = "PARKING",
                    label = spot.address ?: copy.plaza,
                    latitude = spot.location.latitude,
                    longitude = spot.location.longitude,
                ),
            )
        }
        db.mapPins.filter { it.kind == "PARKING" }.forEach { pin ->
            if (none { it.id == pin.id }) {
                add(BarrioMarker(pin.id, pin.kind, pin.title, pin.latitude, pin.longitude))
            }
        }
    }

    Column(Modifier.fillMaxSize().background(OgtColors.canvas)) {
        OgtTopBar(title = copy.parkingMapTitle, onBack = onBack)
        ScreenColumn {
            Box(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(24.dp))
                    .background(Color.White)
                    .padding(horizontal = 14.dp, vertical = 12.dp),
            ) {
                if (query.isBlank()) {
                    Text("Buscar cerca de mi ubicación…", color = OgtColors.muted, fontSize = 14.sp)
                }
                BasicTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
            }
            OgtCaption(
                if (hasGps) gpsStatusLabel(true, session.locationAccuracy)
                else "El mapa espera el GPS del dispositivo o del emulador. No usamos la ubicación del perfil.",
            )
            if (!hasGps) {
                OgtGpsBanner()
            }
            Row(
                Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(end = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OgtPill("${me.barrio}")
                if (session.isParked()) {
                    Box(Modifier.clickable { session.focusParkedCar = true }) {
                        OgtPill(LocalOgtCopy.current.findMyCar, OgtColors.sunset, OgtColors.sunsetText)
                    }
                }
                listOf(400, 500, 1000, 2000).forEach { meters ->
                    Box(Modifier.clickable { radius = meters }) {
                        OgtPill(
                            if (meters >= 1000) "${meters / 1000} km" else "${meters}m",
                            if (radius == meters) OgtColors.primary else OgtColors.sand,
                            if (radius == meters) Color.White else OgtColors.ink,
                        )
                    }
                }
            }
            OgtCaption(copy.freeSpotsIn(visibleSpots.size, radius))
        }
        if (mapCenter != null) {
            Box(Modifier.padding(horizontal = 16.dp).weight(1f, fill = true)) {
                OgtBarrioMap(
                    center = mapCenter,
                    radiusMeters = radius,
                    markers = markers,
                    selectedId = selectedId,
                    onSelect = { selectedId = it },
                    height = 360.dp,
                )
            }
        }
        Column(Modifier.ogtDismissImeOnScroll().verticalScroll(rememberScrollState())) {
        ScreenColumn {
            if (!error.isNullOrBlank()) OgtCaption(error!!)
            selected?.let { spot ->
                val owner = db.userOrNull(spot.ownerUserId)
                OgtCard {
                    OgtPill("Espacio Libre")
                    Text(spot.address ?: "Plaza vecinal", fontWeight = FontWeight.Bold)
                    OgtCaption("@${owner?.displayName.orEmpty()} · ${spot.distanceMeters?.toInt() ?: 0} m")
                    if (!spot.vehicleLabel.isNullOrBlank()) {
                        OgtCaption("${copy.lookForThisCar}: ${spot.vehicleLabel}")
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OgtPill(spot.notes ?: "Confirmado")
                        OgtPill("+${spot.rewardPoints} pts al llegar", OgtColors.sunset, OgtColors.sunsetText)
                    }
                    OgtCaption(copy.curbDisclaimer)
                    Spacer(Modifier.height(8.dp))
                    val phase = spot.matchingPhase(currentEpochMs())
                    OgtPrimaryButton(
                        if (phase == ParkingMatchingPhase.INTEREST) copy.expressInterest else copy.leftoverTake,
                        enabled = hasGps,
                    ) {
                        askForSpot(spot)
                    }
                }
            }
            OgtSectionTitle(copy.otherSpots)
            db.mapPins.filter { it.kind == "PARKING" }.forEach { pin ->
                OgtCard {
                    Text(pin.title, fontWeight = FontWeight.SemiBold)
                    OgtCaption(pin.subtitle)
                }
            }
            Spacer(Modifier.height(80.dp))
        }
        }
    }
    claimWait?.let { wait ->
        ClaimWaitingDialog(wait = wait, onCancel = { abortClaim() })
    }
}
