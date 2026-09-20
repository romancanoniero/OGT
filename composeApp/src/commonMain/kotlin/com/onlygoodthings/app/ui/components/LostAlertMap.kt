package com.onlygoodthings.app.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.onlygoodthings.app.data.LocalOgtDb
import com.onlygoodthings.app.resources.Res
import com.onlygoodthings.app.resources.qs_expand
import com.onlygoodthings.app.theme.OgtColors
import com.onlygoodthings.shared.data.local.LocalAnimalListing
import com.onlygoodthings.shared.data.local.LocalSighting
import com.onlygoodthings.shared.domain.GeoPoint
import com.onlygoodthings.shared.domain.lostTimelineStops
import com.onlygoodthings.shared.domain.movementRoute
import com.onlygoodthings.shared.domain.sightingsUntil
import com.onlygoodthings.shared.realtime.currentEpochMs
import org.jetbrains.compose.resources.painterResource

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
    val fitPoints = if (fill) {
        buildList {
            add(GeoPoint(originLat, originLng))
            visible.forEach { add(GeoPoint(it.latitude, it.longitude)) }
        }
    } else {
        emptyList()
    }
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
            ),
        )
        visible.forEach { row ->
            val who = db.userOrNull(row.userId)
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
            add(BarrioMarker("me", "ME", "Vos", here.latitude, here.longitude))
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
                    Image(
                        painter = painterResource(neighborAvatarArt(row.userId)),
                        contentDescription = db.userOrNull(row.userId)?.displayName,
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .border(1.dp, OgtColors.hairline, CircleShape)
                            .clickable {
                                selectedId = row.id
                                onSelectSighting(row)
                            },
                        contentScale = ContentScale.Crop,
                    )
                }
                OgtCaption("${sightings.size} lo vieron cerca")
            }
        }
    }
}
