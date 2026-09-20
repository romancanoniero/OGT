package com.onlygoodthings.app.ui.screens

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.gestures.rememberScrollableState
import androidx.compose.foundation.gestures.scrollable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import androidx.compose.ui.unit.sp
import com.onlygoodthings.app.data.LocalOgtDb
import com.onlygoodthings.app.data.LocalOgtSession
import com.onlygoodthings.app.i18n.LocalOgtCopy
import com.onlygoodthings.app.map.openWalkingDirections
import com.onlygoodthings.app.map.playParkingFoundSound
import com.onlygoodthings.app.theme.OgtColors
import com.onlygoodthings.app.ui.components.BarrioMarker
import com.onlygoodthings.app.ui.components.CarSilhouette
import com.onlygoodthings.app.ui.components.OgtBarrioMap
import com.onlygoodthings.app.ui.components.OgtCard
import com.onlygoodthings.app.ui.components.OgtCaption
import com.onlygoodthings.app.ui.components.OgtGpsBanner
import com.onlygoodthings.app.ui.components.OgtPill
import com.onlygoodthings.app.ui.components.OgtLoaderOverlay
import com.onlygoodthings.app.ui.components.OgtPrimaryButton
import com.onlygoodthings.app.ui.components.OgtRadarSearch
import com.onlygoodthings.app.ui.components.RadarSpotRow
import com.onlygoodthings.app.ui.components.OgtSecondaryButton
import com.onlygoodthings.app.ui.components.OgtSectionTitle
import com.onlygoodthings.app.ui.components.OgtTopBar
import com.onlygoodthings.app.ui.components.ParkingActionRow
import com.onlygoodthings.app.ui.components.ScreenColumn
import com.onlygoodthings.app.ui.components.gpsStatusLabel
import com.onlygoodthings.shared.data.local.LocalParkingRepository
import com.onlygoodthings.shared.domain.GeoMath
import com.onlygoodthings.shared.domain.ParkedCar
import com.onlygoodthings.shared.domain.ParkingHandoff
import com.onlygoodthings.shared.domain.ParkingMatchingPhase
import com.onlygoodthings.shared.domain.ParkingRules
import com.onlygoodthings.shared.domain.ParkingSpot
import com.onlygoodthings.shared.domain.ParkingStatus
import com.onlygoodthings.shared.domain.interestSecondsLeft
import com.onlygoodthings.shared.domain.matchingPhase
import com.onlygoodthings.shared.domain.parkingChrome
import com.onlygoodthings.shared.domain.waitSecondsLeft
import com.onlygoodthings.shared.realtime.OgtRealtime
import com.onlygoodthings.shared.realtime.OgtSdk
import com.onlygoodthings.shared.realtime.currentEpochMs
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Radar de parking colaborativo.
 * Ceder, reclamar, ticks y cierre viven en [LocalParkingRepository]
 * (el backend es la fuente de verdad cuando hay red).
 */
@Composable
fun ParkingScreen(
    onConfirm: (String) -> Unit,
    onMap: () -> Unit,
    onParkHere: () -> Unit,
) {
    val db = LocalOgtDb.current
    val session = LocalOgtSession.current
    val gps = com.onlygoodthings.app.map.LocalOgtLocation.current
    val copy = LocalOgtCopy.current
    val me = session.me()
    val repo = remember(db, me.id) { LocalParkingRepository(db, me.id) }
    val scope = rememberCoroutineScope()
    val here = session.here()
    val hasGps = here != null
    val parked = session.hasParkedPlace()
    val seeking = !parked
    var epoch by remember { mutableStateOf(0) }
    var now by remember { mutableStateOf(currentEpochMs()) }
    var nearby by remember { mutableStateOf<List<ParkingSpot>>(emptyList()) }
    var mine by remember { mutableStateOf<List<ParkingSpot>>(emptyList()) }
    var history by remember { mutableStateOf<List<ParkingHandoff>>(emptyList()) }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var searching by remember { mutableStateOf(false) }
    var leaveHint by remember { mutableStateOf(false) }
    var radius by remember { mutableStateOf(ParkingRules.DEFAULT_RADIUS_METERS) }
    var selectedId by remember { mutableStateOf<String?>(null) }
    var knownNearbyIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var claimWait by remember { mutableStateOf<ClaimWaitUi?>(null) }
    var claimJob by remember { mutableStateOf<Job?>(null) }

    LaunchedEffect(parked) {
        if (parked) searching = false
    }
    LaunchedEffect(here?.latitude, here?.longitude) {
        val fix = here ?: return@LaunchedEffect
        db.placeRadarDemoSpots(fix)
    }
    LaunchedEffect(epoch, radius, db.parkingEpoch, session.radarEnabled, here?.latitude, here?.longitude, parked, now / 2_000) {
        mine = repo.active()
        history = repo.history()
        val next = if (seeking && session.radarEnabled && here != null) repo.nearby(here, radius) else emptyList()
        val fresh = next.map { it.id }.toSet() - knownNearbyIds
        if (knownNearbyIds.isNotEmpty() && fresh.isNotEmpty()) {
            playParkingFoundSound()
        }
        knownNearbyIds = next.map { it.id }.toSet()
        nearby = next
    }
    LaunchedEffect(Unit) {
        if (!OgtSdk.isStarted()) return@LaunchedEffect
        runCatching {
            OgtRealtime().observeParkingSpots().collect { spot ->
                db.upsertRemoteSpot(spot)
            }
        }
    }
    LaunchedEffect(Unit) {
        while (true) {
            delay(1000)
            now = currentEpochMs()
        }
    }
    LaunchedEffect(mine.firstOrNull { it.status == ParkingStatus.CLAIMED }?.id) {
        val claimed = mine.firstOrNull { it.status == ParkingStatus.CLAIMED } ?: return@LaunchedEffect
        if (claimed.claimedByUserId != me.id && claimed.ownerUserId != me.id) return@LaunchedEffect
        while (true) {
            val fix = session.here() ?: return@LaunchedEffect
            runCatching { repo.tick(claimed.id, fix, null, null) }
            delay(5_000)
        }
    }

    val live = mine.firstOrNull { it.status == ParkingStatus.CLAIMED }
        ?: mine.firstOrNull { it.status == ParkingStatus.AVAILABLE && it.ownerUserId == me.id }
    val chrome = parkingChrome(
        parked = parked,
        hasGps = hasGps,
        busy = busy,
        searching = searching,
        liveStatus = live?.status,
        liveOwnerIsMe = live?.ownerUserId == me.id,
        liveClaimantIsMe = live?.claimedByUserId == me.id,
    )
    val assignedToMe = chrome.assignedToMe
    val iAmYielding = chrome.iAmYielding
    val featured = live
        ?: if (seeking) nearby.firstOrNull { it.id == selectedId } ?: nearby.firstOrNull() else null
    val mapMarkers = buildList {
        if (here != null) add(BarrioMarker("me", "ME", copy.mePin, here.latitude, here.longitude))
        if (parked) {
            session.parkedCar?.let { car ->
                add(BarrioMarker("parked", "CAR", copy.myCarPin, car.latitude, car.longitude))
            }
        }
        if (iAmYielding) {
            live?.let { spot ->
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
        }
        if (seeking && !iAmYielding) nearby.forEach { spot ->
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
    }

    fun run(block: suspend () -> Unit) {
        scope.launch {
            busy = true
            error = null
            try {
                block()
                epoch++
            } catch (ex: Exception) {
                error = ex.message
            } finally {
                busy = false
            }
        }
    }

    fun abortClaim() {
        val id = claimWait?.spotId
        claimJob?.cancel()
        claimWait = null
        if (id != null) {
            scope.launch {
                runCatching { repo.cancel(id) }
                epoch++
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
                claimWait = null
                epoch++
            } catch (ex: CancellationException) {
                throw ex
            } catch (ex: Exception) {
                error = ex.message
                claimWait = null
            }
        }
    }

    val mapOpen = remember { mutableStateOf(false) }
    val mapCompact = remember { mutableStateOf(false) }
    val listScroll = rememberScrollState()
    LaunchedEffect(assignedToMe) {
        if (!assignedToMe) mapCompact.value = false
    }
    val reveal by animateFloatAsState(
        targetValue = if (mapOpen.value) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.86f, stiffness = 210f),
        label = "parkingHeaderReveal",
    )
    val mapBloom by animateFloatAsState(
        targetValue = if (mapOpen.value) 1f else 0f,
        animationSpec = spring(
            dampingRatio = 0.74f,
            stiffness = Spring.StiffnessVeryLow,
        ),
        label = "parkingMapBloom",
    )
    val compactBloom by animateFloatAsState(
        targetValue = if (mapCompact.value) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.82f, stiffness = 240f),
        label = "parkingMapCompact",
    )
    val nested = remember(listScroll, assignedToMe) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (available.y < -10f) {
                    if (assignedToMe && !mapCompact.value) {
                        mapCompact.value = true
                        mapOpen.value = false
                        return Offset(0f, available.y)
                    }
                    if (!assignedToMe && !mapOpen.value) {
                        mapOpen.value = true
                        return Offset(0f, available.y)
                    }
                }
                if (available.y > 10f && listScroll.value == 0) {
                    if (assignedToMe && mapCompact.value) {
                        mapCompact.value = false
                        return Offset(0f, available.y)
                    }
                    if (!assignedToMe && mapOpen.value) {
                        mapOpen.value = false
                        return Offset(0f, available.y)
                    }
                }
                return Offset.Zero
            }
        }
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
    val screenH = maxHeight
    val swipeState = rememberScrollableState { delta ->
        when {
            delta < -8f && assignedToMe && !mapCompact.value -> {
                mapCompact.value = true
                mapOpen.value = false
                delta
            }
            delta < -8f && !assignedToMe && !mapOpen.value -> {
                mapOpen.value = true
                delta
            }
            delta > 8f && assignedToMe && mapCompact.value && listScroll.value == 0 -> {
                mapCompact.value = false
                delta
            }
            delta > 8f && !assignedToMe && mapOpen.value && listScroll.value == 0 -> {
                mapOpen.value = false
                delta
            }
            else -> 0f
        }
    }
    Column(
        Modifier
            .fillMaxSize()
            .background(OgtColors.canvas)
            .scrollable(state = swipeState, orientation = Orientation.Vertical)
            .nestedScroll(nested),
    ) {
        OgtTopBar(title = copy.parkingTitle)
        Column(
            Modifier
                .fillMaxWidth()
                .pointerInput(mapOpen.value, mapCompact.value, assignedToMe) {
                    detectVerticalDragGestures { _, dy ->
                        if (dy < -12f) {
                            if (assignedToMe) {
                                mapCompact.value = true
                                mapOpen.value = false
                            } else if (!mapOpen.value) {
                                mapOpen.value = true
                            }
                        }
                        if (dy > 12f) {
                            if (assignedToMe) mapCompact.value = false
                            else if (mapOpen.value) mapOpen.value = false
                        }
                    }
                },
        ) {
        ScreenColumn {
            OgtPill(
                when {
                    parked && hasGps -> copy.gpsLive(session.locationAccuracy?.toInt())
                    parked -> copy.leftItHere
                    !session.radarEnabled -> copy.radarPaused
                    hasGps -> copy.gpsLive(session.locationAccuracy?.toInt())
                    session.locationAcquiring -> copy.seekingGps
                    else -> copy.noGps
                },
            )
            ParkingActionRow(
                parkedLabel = if (parked) copy.findMyCar else copy.parkedHere,
                yieldLabel = copy.yieldMySpot,
                searchLabel = if (searching) copy.searchingParking else copy.searchParking,
                parkedEnabled = chrome.parkedEnabled,
                yieldEnabled = chrome.yieldEnabled,
                searchEnabled = chrome.searchEnabled,
                findParked = chrome.findParked,
                yieldSelected = chrome.yieldSelected,
                showSearch = chrome.showSearch,
                showYield = chrome.showYield,
                progress = reveal,
                onParked = { onParkHere() },
                onYield = {
                    val fix = here
                    if (fix == null) {
                        error = copy.needGps
                        return@ParkingActionRow
                    }
                    if (iAmYielding) {
                        error = copy.alreadyYielding
                        return@ParkingActionRow
                    }
                    val car = session.parkedCar?.takeIf { parked }
                    val curb = car?.point() ?: fix
                    val vehicleLabel = car?.vehicle?.label()
                        ?: session.vehicle.takeIf { it.isReady() }?.label()
                    leaveHint = car != null && GeoMath.haversineMeters(fix, car.point()) > ParkingRules.YIELD_ASK_METERS
                    run {
                        repo.publishVacancy(
                            location = curb,
                            ttlMinutes = ParkingRules.DEFAULT_TTL_MINUTES,
                            notes = car?.address?.takeIf { it.isNotBlank() } ?: copy.leavingNotes(me.barrio),
                            vehicleLabel = vehicleLabel,
                            ownerLocation = fix,
                        )
                        session.forgetParked()
                        session.radarEnabled = true
                        session.showYieldNotifying = true
                    }
                },
                onSearch = {
                    if (!seeking) return@ParkingActionRow
                    session.radarEnabled = true
                    searching = true
                    scope.launch {
                        gps.refreshNow()
                        delay(1800)
                        epoch++
                        delay(400)
                        searching = false
                    }
                },
            )
            if (chrome.showCancelYield && !chrome.showOwnerHandoff) {
                live?.let { spot ->
                    OgtPrimaryButton(copy.cancelYield, enabled = !busy) {
                        run { repo.cancel(spot.id) }
                    }
                }
            }
            if (leaveHint && !chrome.iAmYielding) {
                OgtCaption(copy.waitCapHint)
            }
            if (chrome.showParkedCard) session.parkedCar?.let { car ->
                OgtCard {
                    Text(copy.leftItHere, fontWeight = FontWeight.Bold, fontSize = 20.sp, color = OgtColors.ink)
                    if (!car.address.isNullOrBlank()) OgtCaption(car.address!!)
                    OgtCaption(
                        copy.parkedHint,
                        modifier = Modifier
                            .clipToBounds()
                            .height(lerp(40.dp, 0.dp, reveal))
                            .graphicsLayer { alpha = (1f - reveal).coerceIn(0f, 1f) },
                    )
                    OgtPrimaryButton(copy.seeOnMap) {
                        openWalkingDirections(car.latitude, car.longitude)
                    }
                    OgtSecondaryButton(copy.forgetPlace) { session.forgetParked() }
                }
            }
            if (chrome.showParkedCard) {
                OgtCaption(
                    copy.parkedHidesRadar,
                    modifier = Modifier
                        .clipToBounds()
                        .height(lerp(40.dp, 0.dp, reveal))
                        .graphicsLayer { alpha = (1f - reveal).coerceIn(0f, 1f) },
                )
            } else if (chrome.showRadiusChips) {
                val remain = featured?.let { remainingLabel(it.expiresAtEpochMs, now, copy) }
                OgtCaption(
                    buildString {
                        if (remain != null) append(remain).append(" · ")
                        append(copy.freeSpotsIn(nearby.size, radius))
                    },
                )
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    listOf(400, 500, 1000, 2000).forEach { meters ->
                        Box(Modifier.clickable { radius = meters }) {
                            OgtPill(
                                if (meters >= 1000) "${meters / 1000} km" else "${meters}m",
                                if (radius == meters) OgtColors.secondary else OgtColors.sand,
                                if (radius == meters) androidx.compose.ui.graphics.Color.White else OgtColors.ink,
                            )
                        }
                    }
                }
            }
            if (!error.isNullOrBlank()) {
                OgtCaption(error!!)
            }
            if (!hasGps) {
                OgtGpsBanner()
            }
        }
        }
        val mapCenter = when {
            iAmYielding -> live?.location ?: here
            parked -> session.parkedCar?.point() ?: here
            else -> here
        }
        if (chrome.showMap && hasGps && mapCenter != null) {
            val mapClosed = 168.dp
            val mapOpened = if (seeking) {
                (screenH * 0.40f).coerceIn(220.dp, 340.dp)
            } else {
                (screenH * 0.62f).coerceAtLeast(mapClosed + 160.dp)
            }
            val mapMini = 72.dp
            val mapTall = if (mapOpen.value) mapOpened else mapClosed
            val mapH = if (assignedToMe) {
                lerp(mapTall, mapMini, compactBloom)
            } else {
                lerp(mapClosed, mapOpened, mapBloom)
            }
            val mapShape = RoundedCornerShape(lerp(22.dp, 28.dp, mapBloom))
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(mapH)
                    .padding(horizontal = lerp(16.dp, 12.dp, mapBloom), vertical = lerp(6.dp, 10.dp, mapBloom))
                    .graphicsLayer {
                        transformOrigin = TransformOrigin(0.5f, 1f)
                        val grow = 0.92f + 0.08f * mapBloom
                        scaleX = grow
                        scaleY = grow
                    }
                    .shadow(lerp(2.dp, 18.dp, mapBloom), mapShape, ambientColor = androidx.compose.ui.graphics.Color(0x140A0A0B))
                    .clip(mapShape),
            ) {
                OgtBarrioMap(
                    center = mapCenter,
                    radiusMeters = if (parked) 200 else radius,
                    markers = mapMarkers,
                    selectedId = when {
                        iAmYielding -> live?.id ?: "me"
                        parked -> "parked"
                        else -> featured?.id ?: "me"
                    },
                    onSelect = { id ->
                        if (id == "me" || id == "parked") return@OgtBarrioMap
                        selectedId = id
                        if (seeking && !assignedToMe) mapOpen.value = true
                    },
                    fill = true,
                    onCameraInteract = {
                        if (seeking && !assignedToMe && !mapOpen.value) mapOpen.value = true
                    },
                )
                if (seeking && searching) {
                    OgtRadarSearch(
                        copy.searchingParking,
                        Modifier.matchParentSize(),
                    )
                }
            }
        }
        Column(
            Modifier
                .then(
                    when {
                        // Plaza asignada: la card no cabe en el peek de 88 dp del radar vacío.
                        assignedToMe || chrome.showOwnerHandoff -> Modifier.weight(1f)
                        chrome.showNearbyList && (mapOpen.value || nearby.isNotEmpty()) -> Modifier.weight(1f)
                        chrome.showMap && hasGps && mapCenter != null -> Modifier.height(lerp(88.dp, 72.dp, mapBloom))
                        !chrome.showMap && !chrome.showNearbyList -> Modifier
                        else -> Modifier.weight(1f)
                    },
                )
                .verticalScroll(listScroll),
        ) {
            if (chrome.showNearbyList) {
                if (nearby.isEmpty()) {
                    OgtCaption(
                        copy.freeSpotsIn(0, radius),
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                }
                nearby.forEach { spot ->
                    val chosen = spot.id == selectedId || (selectedId == null && spot.id == featured?.id)
                    val otherPhase = spot.matchingPhase(now)
                    RadarSpotRow(
                        address = spot.address?.takeIf { it.isNotBlank() } ?: copy.plaza,
                        distance = walkDistanceLabel(spot.distanceMeters),
                        vehicle = spot.vehicleLabel?.takeIf { it.isNotBlank() } ?: copy.nearbySpot,
                        selected = chosen,
                        claimEnabled = hasGps && !busy && claimWait == null,
                        claimLabel = when {
                            busy && otherPhase == ParkingMatchingPhase.INTEREST -> copy.expressingInterest
                            otherPhase == ParkingMatchingPhase.INTEREST -> copy.expressInterest
                            busy -> copy.reserving
                            else -> copy.leftoverTake
                        },
                        onSelect = { selectedId = spot.id },
                        onClaim = { askForSpot(spot) },
                    )
                }
            }
        ScreenColumn {
            if (chrome.showOwnerHandoff || chrome.showAssignedCard || (!seeking && !chrome.iAmYielding)) featured?.let { spot ->
                val owner = db.userOrNull(spot.ownerUserId)
                val claimant = db.userOrNull(spot.claimedByUserId)
                val iOwn = spot.ownerUserId == me.id
                val iClaim = spot.claimedByUserId == me.id
                val phase = spot.matchingPhase(now)
                if (iClaim && spot.status == ParkingStatus.CLAIMED) {
                    OgtCard {
                        Text(copy.assignedLive, color = OgtColors.muted, fontSize = 12.sp)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                spot.address?.takeIf { it.isNotBlank() } ?: copy.plaza,
                                modifier = Modifier.weight(1f),
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 16.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            walkDistanceLabel(spot.distanceMeters)?.let { meters ->
                                Text(
                                    meters,
                                    color = OgtColors.muted,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 13.sp,
                                )
                            }
                        }
                        if (!spot.vehicleLabel.isNullOrBlank()) {
                            Text(spot.vehicleLabel!!, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                        }
                        OgtCaption(copy.assignedOwner(owner?.displayName ?: copy.neighbor))
                        if (!spot.notes.isNullOrBlank()) OgtCaption(spot.notes!!)
                    }
                } else {
                OgtCard {
                    Text(
                        when {
                            iOwn && phase == ParkingMatchingPhase.INTEREST -> copy.waitingNeighbor
                            iOwn && phase == ParkingMatchingPhase.LEFTOVER -> copy.waitingNeighbor
                            iOwn && spot.status == ParkingStatus.CLAIMED -> copy.yieldInProgress
                            !iOwn && spot.status == ParkingStatus.CLAIMED -> copy.notYourSpot
                            else -> copy.nearbySpot
                        },
                        color = OgtColors.muted,
                        fontSize = 12.sp,
                    )
                    Text(owner?.displayName ?: copy.neighbor, fontWeight = FontWeight.Bold, fontSize = 20.sp)
                    if (!spot.vehicleLabel.isNullOrBlank()) {
                        CarSilhouette("#6B7280", width = 72.dp)
                        OgtCaption(copy.lookForThisCar)
                        Text(spot.vehicleLabel!!, fontWeight = FontWeight.SemiBold)
                    }
                    OgtCaption(owner?.honorTag.orEmpty())
                    Text(spot.address ?: spot.notes.orEmpty(), fontWeight = FontWeight.SemiBold)
                    if (claimant != null && spot.status == ParkingStatus.CLAIMED) {
                        OgtCaption("Reclama ${claimant.displayName}")
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OgtPill("+${spot.rewardPoints} Pts OnlyGoodThings")
                        val etaMin = ((spot.etaSeconds ?: 180) / 60).coerceAtLeast(1)
                        val meters = spot.distanceMeters?.toInt() ?: 0
                        OgtPill("ETA $etaMin min · $meters m", OgtColors.sunset, OgtColors.sunsetText)
                    }
                    if (!spot.notes.isNullOrBlank()) OgtCaption(spot.notes!!)
                    if (phase == ParkingMatchingPhase.INTEREST) {
                        OgtCaption(copy.matchingIn(spot.interestSecondsLeft(now)))
                        if (spot.interestCount > 0) OgtCaption(copy.matchingNeighbors(spot.interestCount))
                    }
                    if (iOwn && phase == ParkingMatchingPhase.LEFTOVER) {
                        OgtCaption(copy.leftoverHint)
                    }
                    spot.ownerEtaSeconds?.let { OgtCaption(copy.ownerEtaHint(it)) }
                    val waitLeft = spot.waitSecondsLeft(now)
                    if (iOwn && waitLeft > 0) {
                        OgtCaption(copy.ownerWaitLeft(waitLeft / 60, waitLeft % 60))
                        OgtCaption(copy.waitCapHint)
                    }
                    if (!iOwn && spot.status == ParkingStatus.AVAILABLE) {
                        OgtCaption(copy.curbDisclaimer)
                    }
                    if (!iOwn && spot.viewerInterested && phase == ParkingMatchingPhase.INTEREST) {
                        OgtCaption(copy.interestRegistered)
                    }
                }
                }
                when {
                    iOwn && spot.status == ParkingStatus.AVAILABLE -> {
                        OgtPrimaryButton(copy.cancelYield, enabled = !busy) {
                            run { repo.cancel(spot.id) }
                        }
                    }
                    iOwn && spot.status == ParkingStatus.CLAIMED -> {
                        OgtPrimaryButton(if (busy) copy.reserving else copy.confirmYield, enabled = !busy && hasGps) {
                            run {
                                val fix = session.here() ?: error("Necesitamos un fix GPS real")
                                val result = repo.complete(spot.id, fix)
                                session.forgetParked()
                                session.radarEnabled = true
                                mapCompact.value = false
                                onConfirm(result.spot.id)
                            }
                        }
                        OgtSecondaryButton(copy.cancelYield) { run { repo.cancel(spot.id) } }
                    }
                    iClaim && spot.status == ParkingStatus.CLAIMED -> {
                        OgtPrimaryButton(if (busy) copy.reserving else copy.arrivedConfirm, enabled = !busy && hasGps) {
                            run {
                                val curb = spot.location
                                repo.complete(spot.id, curb)
                                session.rememberParked(
                                    ParkedCar(
                                        latitude = curb.latitude,
                                        longitude = curb.longitude,
                                        parkedAtEpochMs = currentEpochMs(),
                                        address = spot.address,
                                        vehicle = session.vehicle.takeIf { it.isReady() },
                                    ),
                                )
                                session.radarEnabled = false
                                mapCompact.value = false
                                selectedId = null
                            }
                        }
                        OgtSecondaryButton(copy.foundOtherPlace, enabled = !busy) {
                            run {
                                repo.foundOtherPlace(spot.id)
                                session.radarEnabled = true
                                selectedId = null
                            }
                        }
                    }
                    spot.status == ParkingStatus.AVAILABLE && !iOwn && phase == ParkingMatchingPhase.INTEREST -> {
                        if (!spot.viewerInterested) {
                            OgtPrimaryButton(if (busy) copy.expressingInterest else copy.expressInterest, enabled = !busy && hasGps) {
                                askForSpot(spot)
                            }
                        }
                    }
                    spot.status == ParkingStatus.AVAILABLE && !iOwn -> {
                        OgtPrimaryButton(if (busy) copy.reserving else copy.leftoverTake, enabled = !busy && hasGps) {
                            askForSpot(spot)
                        }
                    }
                }
            }
            if (!seeking) {
                OgtSecondaryButton(copy.barrioMap) { onMap() }
            }
            if (history.isNotEmpty() && live?.claimedByUserId != me.id) {
                OgtSectionTitle(copy.recentYields)
                history.take(5).forEach { handoff ->
                    val peerId = if (handoff.ownerUserId == me.id) handoff.claimantUserId else handoff.ownerUserId
                    val peer = db.userOrNull(peerId)
                    OgtCard {
                        Text(peer?.displayName ?: copy.neighbor, fontWeight = FontWeight.SemiBold)
                        OgtCaption("+${handoff.pointsAwarded} pts · ${handoff.proximityMeters.toInt()} m de proximidad")
                    }
                }
            }
            Spacer(Modifier.height(88.dp))
            Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
        }
        }
    }
        claimWait?.let { wait ->
            ClaimWaitingDialog(wait = wait, onCancel = { abortClaim() })
        }
        if (session.showYieldNotifying) {
            YieldNotifyingDialog(onDismiss = { session.showYieldNotifying = false })
        }
        OgtLoaderOverlay(visible = busy && claimWait == null && !session.showYieldNotifying, label = copy.loading)
    }
}

/** Distancia del buscador a la plaza, en la misma línea que la dirección. */
private fun walkDistanceLabel(meters: Double?): String? {
    if (meters == null) return null
    val whole = meters.toInt().coerceAtLeast(0)
    if (whole >= 1000) {
        val tenths = ((whole + 50) / 100)
        val km = tenths / 10
        val frac = tenths % 10
        return if (frac == 0) "$km km" else "$km,$frac km"
    }
    return "$whole m"
}

private fun remainingLabel(expiresAtEpochMs: Long, now: Long, copy: com.onlygoodthings.app.i18n.OgtCopy): String? {
    if (expiresAtEpochMs <= 0L) return null
    val left = (expiresAtEpochMs - now).coerceAtLeast(0L)
    val totalSec = (left / 1000).toInt()
    val min = totalSec / 60
    val sec = totalSec % 60
    return if (left <= 0L) copy.expired else copy.freeingIn(min, sec)
}
