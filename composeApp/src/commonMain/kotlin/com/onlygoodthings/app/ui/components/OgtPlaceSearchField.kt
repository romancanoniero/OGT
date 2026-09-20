package com.onlygoodthings.app.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.unit.dp
import com.onlygoodthings.app.data.LocalOgtSession
import com.onlygoodthings.app.i18n.LocalOgtCopy
import com.onlygoodthings.app.i18n.OgtLang
import com.onlygoodthings.app.map.AddressHit
import com.onlygoodthings.app.map.LocalOgtLocation
import com.onlygoodthings.app.map.awaitHere
import com.onlygoodthings.app.map.reverseGeocode
import com.onlygoodthings.app.map.searchAddress
import com.onlygoodthings.app.resources.Res
import com.onlygoodthings.app.resources.qs_gps
import com.onlygoodthings.app.resources.qs_map
import com.onlygoodthings.app.theme.OgtColors
import com.onlygoodthings.app.theme.OgtDimens
import com.onlygoodthings.app.theme.OgtMotion
import com.onlygoodthings.app.theme.ogtOutlinedFieldColors
import com.onlygoodthings.shared.domain.GeoPoint
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Calle incremental (Nominatim) + GPS + mapa. El radio de alerta no se elige acá.
 */
@Composable
fun OgtPlaceSearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    point: GeoPoint?,
    onResolved: (AddressHit) -> Unit,
    missing: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val session = LocalOgtSession.current
    val gps = LocalOgtLocation.current
    val copy = LocalOgtCopy.current
    val lang = if (copy.lang == OgtLang.EN) "en" else "es"
    val scope = rememberCoroutineScope()
    var hits by remember { mutableStateOf<List<AddressHit>>(emptyList()) }
    var mapOpen by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var searching by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var committed by remember { mutableStateOf("") }
    var fromGps by remember { mutableStateOf(false) }

    LaunchedEffect(query) {
        val q = query.trim()
        if (q.length < 3 || q == committed) {
            hits = emptyList()
            searching = false
            return@LaunchedEffect
        }
        delay(420)
        searching = true
        error = null
        hits = runCatching { searchAddress(q, lang) }.getOrDefault(emptyList())
        if (hits.isEmpty()) error = copy.noAddressHits
        searching = false
    }

    var mapDraft by remember { mutableStateOf<GeoPoint?>(null) }
    LaunchedEffect(mapDraft, mapOpen) {
        val draft = mapDraft
        if (!mapOpen || draft == null) return@LaunchedEffect
        delay(480)
        val hit = runCatching { reverseGeocode(draft, lang) }.getOrNull() ?: return@LaunchedEffect
        committed = hit.label
        fromGps = false
        onQueryChange(hit.label)
        onResolved(AddressHit(hit.label, draft))
    }

    fun applyHit(hit: AddressHit, gpsFix: Boolean) {
        committed = hit.label
        fromGps = gpsFix
        hits = emptyList()
        error = null
        onQueryChange(hit.label)
        onResolved(hit)
    }

    Box(modifier) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedTextField(
                value = query,
                onValueChange = {
                    if (it.length <= 120) {
                        fromGps = false
                        onQueryChange(it)
                    }
                },
                modifier = Modifier.weight(1f),
                placeholder = { Text(copy.addressHint, color = OgtColors.muted) },
                shape = RoundedCornerShape(OgtDimens.buttonRadius),
                colors = ogtOutlinedFieldColors(missing = missing),
                singleLine = true,
            )
            CircleIconButton(
                art = Res.drawable.qs_gps,
                label = copy.useMyLocation,
                onClick = {
                    mapOpen = false
                    hits = emptyList()
                    scope.launch {
                        busy = true
                        error = null
                        val fix = awaitHere(session, gps)
                        if (fix == null) {
                            session.askLocationScope = true
                            error = copy.needGps
                        } else {
                            val hit = runCatching { reverseGeocode(fix, lang) }.getOrNull()
                                ?: AddressHit("${fix.latitude}, ${fix.longitude}", fix)
                            applyHit(hit, gpsFix = true)
                        }
                        busy = false
                    }
                },
                filled = fromGps && point != null,
                enabled = !busy,
            )
            CircleIconButton(
                art = Res.drawable.qs_map,
                label = copy.locateOnMap,
                onClick = {
                    hits = emptyList()
                    if (!mapOpen) {
                        val seed = point ?: session.here() ?: GeoPoint(-34.6037, -58.3816)
                        mapDraft = seed
                        if (point == null) {
                            onResolved(AddressHit(query.trim().ifBlank { copy.locateOnMap }, seed))
                        }
                    }
                    mapOpen = !mapOpen
                },
                filled = mapOpen,
                enabled = !busy,
            )
        }
        if (searching) OgtCaption(copy.resolvingAddress)
        if (fromGps && point != null) OgtCaption(copy.placeFromGps)
        AnimatedVisibility(
            visible = hits.isNotEmpty(),
            enter = OgtMotion.enterUp,
            exit = OgtMotion.exitDown,
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(OgtDimens.buttonRadius))
                    .background(OgtColors.sand),
            ) {
                hits.forEach { hit ->
                    Text(
                        hit.label,
                        color = OgtColors.ink,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { applyHit(hit, gpsFix = false) }
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                    )
                }
            }
        }
        AnimatedVisibility(
            visible = mapOpen,
            enter = OgtMotion.enterUp,
            exit = OgtMotion.exitDown,
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                val center = mapDraft ?: point ?: session.here() ?: GeoPoint(-34.6037, -58.3816)
                OgtBarrioMap(
                    center = center,
                    radiusMeters = 200,
                    markers = listOf(
                        BarrioMarker("lost-place", "SIGHT", query.ifBlank { copy.locateOnMap }, center.latitude, center.longitude),
                    ),
                    selectedId = "lost-place",
                    onSelect = {},
                    height = 240.dp,
                    onViewMoved = { moved ->
                        fromGps = false
                        mapDraft = moved
                        onResolved(AddressHit(query.trim().ifBlank { copy.locateOnMap }, moved))
                    },
                )
                OgtCaption(copy.adjustOnMap)
            }
        }
        if (!error.isNullOrBlank()) OgtCaption(error!!)
        if (point == null && query.isNotBlank() && hits.isEmpty() && !searching && !mapOpen) {
            OgtCaption("Elegí una sugerencia, tu ubicación o el mapa.")
        }
    }
    OgtLoaderOverlay(visible = busy, label = copy.locatingGps)
    }
}
