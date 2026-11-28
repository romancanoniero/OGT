package com.onlygoodthings.app.ui.components

import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.onlygoodthings.app.data.LocalOgtDb
import com.onlygoodthings.app.resources.Res
import com.onlygoodthings.app.resources.qs_expand
import com.onlygoodthings.app.resources.qs_gps
import androidx.compose.material3.Text
import com.onlygoodthings.app.theme.OgtColors
import com.onlygoodthings.shared.data.local.LocalAnimalListing
import com.onlygoodthings.shared.data.local.LocalSighting
import com.onlygoodthings.shared.domain.GeoPoint
import com.onlygoodthings.shared.domain.lostTimelineStops
import com.onlygoodthings.shared.domain.movementRoute
import com.onlygoodthings.shared.domain.sightingsUntil
import com.onlygoodthings.app.ui.screens.OgtNeighborAvatar
import com.onlygoodthings.shared.realtime.currentEpochMs

/** Mapa de un aviso: origen, radio y avistajes con avatar. */
@Composable
fun LostAlertMap(
    listing: LocalAnimalListing,
    sightings: List<LocalSighting>,
    here: GeoPoint?,
    height: Dp,
    modifier: Modifier = Modifier,
    onSelectSighting: (LocalSighting) -> Unit = {},
    interactive: Boolean = true,
    fill: Boolean = false,
    reportBadges: Boolean = false,
    onExpand: (() -> Unit)? = null,
) {
    val db = LocalOgtDb.current
    val socialTick by db.socialTick.collectAsState()
    val knownFaces = remember(socialTick, sightings) {
        sightings.associate { it.id to db.userOrNull(it.userId) }
    }
    val originLat = listing.latitude
    val originLng = listing.longitude
    if (originLat == null || originLng == null) return
    var selectedId by remember(listing.id) { mutableStateOf(listing.id) }
    val now = remember { currentEpochMs() }
    val stops = remember(listing.id, sightings, now) { lostTimelineStops(listing, sightings, now) }
    var dayIndex by remember(listing.id, stops.size) { mutableStateOf(stops.lastIndex.coerceAtLeast(0)) }
    val cutoff = stops.getOrNull(dayIndex)?.dayEndEpochMs ?: Long.MAX_VALUE
    val visible = if (fill && stops.isNotEmpty()) sightingsUntil(sightings, cutoff) else sightings
    val route = if (fill) movementRoute(listing, visible) else emptyList()
    var fitVersion by remember(listing.id) { mutableStateOf(0) }
    val fitPoints = remember(listing.id, visible, here != null, fitVersion) {
        buildList {
            add(GeoPoint(originLat, originLng))
            visible.forEach { add(GeoPoint(it.latitude, it.longitude)) }
            here?.let { add(it) }
        }
    }
    val photo = remember(listing.id, socialTick) {
        db.posts.firstOrNull { it.listingId == listing.id }?.let { db.mediaOf(it.id).firstOrNull() }
    }
    val selectedSighting = visible.firstOrNull { it.id == selectedId }
    val distance = petMapDistance(here, selectedSighting?.let { GeoPoint(it.latitude, it.longitude) }
        ?: GeoPoint(originLat, originLng))
    LaunchedEffect(visible.map { it.id }.joinToString()) {
        if (selectedId != listing.id && visible.none { it.id == selectedId }) {
            selectedId = visible.lastOrNull()?.id ?: listing.id
        }
    }
    val markers = buildList {
        add(
            BarrioMarker(
                id = listing.id,
                kind = "ORIGIN",
                label = listing.petName.ifBlank { "Última vista" },
                latitude = originLat,
                longitude = originLng,
                petPhoto = photo,
            ),
        )
        visible.forEach { row ->
            val who = knownFaces[row.id]
            add(
                BarrioMarker(
                    id = row.id,
                    kind = "SIGHT",
                    label = who?.displayName?.substringBefore(" ") ?: "Avistaje",
                    latitude = row.latitude,
                    longitude = row.longitude,
                    avatar = neighborAvatarArt(row.userId),
                    saw = true,
                    wrote = row.wroteNote(),
                    badgesInsteadOfName = reportBadges,
                ),
            )
        }
        if (here != null) {
            add(BarrioMarker("me", "LOCATION", "Tu ubicación actual", here.latitude, here.longitude))
        }
    }
    Column(
        modifier.fillMaxWidth().then(if (fill) Modifier.fillMaxSize() else Modifier),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(Modifier.fillMaxWidth().then(if (fill) Modifier.fillMaxSize() else Modifier)) {
            OgtBarrioMap(
                center = GeoPoint(originLat, originLng),
                radiusMeters = listing.alertRadiusM,
                markers = markers,
                selectedId = selectedId,
                onSelect = { id ->
                    selectedId = id
                    visible.firstOrNull { it.id == id }?.let(onSelectSighting)
                },
                height = height,
                fill = fill,
                interactive = interactive,
                route = route,
                fitPoints = fitPoints,
                fitRequest = fitVersion,
            )
            if (distance != null) {
                val destination = if (selectedSighting != null) "del avistamiento" else if (listing.kind == "FOUND") "del encuentro" else "del lugar de la pérdida"
                Text("A $distance $destination · línea recta",
                    modifier = Modifier.align(Alignment.TopStart)
                        .then(if (fill) Modifier.windowInsetsPadding(WindowInsets.statusBars) else Modifier)
                        .padding(start = 10.dp, top = 10.dp, end = 64.dp)
                        .background(OgtColors.surface, androidx.compose.foundation.shape.RoundedCornerShape(12.dp))
                        .padding(8.dp), color = OgtColors.ink,
                    fontSize = 11.sp)
            }
            if (here != null && interactive) CircleIconButton(
                art = Res.drawable.qs_gps, label = "Ver mi ubicación y la mascota",
                onClick = { fitVersion++ },
                modifier = Modifier.align(Alignment.TopEnd)
                    .then(if (fill) Modifier.windowInsetsPadding(WindowInsets.statusBars) else Modifier)
                    .padding(end = 10.dp, top = if (fill || onExpand != null) 66.dp else 10.dp),
                filled = true,
            )
            if (onExpand != null) {
                CircleIconButton(
                    art = Res.drawable.qs_expand,
                    label = "Ampliar mapa",
                    onClick = onExpand,
                    modifier = Modifier.align(Alignment.TopEnd).padding(10.dp),
                    filled = true,
                )
            }
            if (fill && stops.isNotEmpty()) {
                LostSightingTimelineBar(
                    stops = stops,
                    selectedIndex = dayIndex,
                    onSelect = { dayIndex = it },
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .windowInsetsPadding(WindowInsets.navigationBars)
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                )
            }
        }
        if (!fill && sightings.isNotEmpty()) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                sightings.take(6).forEach { row ->
                    val who = knownFaces[row.id]
                    OgtNeighborAvatar(
                        userId = row.userId,
                        photoUrl = who?.photoUrl,
                        contentDescription = who?.displayName,
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .border(1.dp, OgtColors.hairline, CircleShape)
                            .clickable {
                                selectedId = row.id
                                onSelectSighting(row)
                            },
                    )
                }
                OgtCaption("${sightings.size} lo vieron cerca")
            }
        }
    }
}
