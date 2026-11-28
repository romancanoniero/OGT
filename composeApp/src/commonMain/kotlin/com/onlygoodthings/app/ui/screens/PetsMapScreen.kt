package com.onlygoodthings.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.onlygoodthings.app.data.LocalOgtDb
import com.onlygoodthings.app.data.LocalAuth
import com.onlygoodthings.app.data.LocalOgtSession
import com.onlygoodthings.app.i18n.LocalOgtCopy
import com.onlygoodthings.app.theme.OgtColors
import com.onlygoodthings.app.ui.components.BarrioMarker
import com.onlygoodthings.app.ui.components.OgtBarrioMap
import com.onlygoodthings.app.ui.components.OgtCard
import com.onlygoodthings.app.ui.components.OgtCaption
import com.onlygoodthings.app.ui.components.OgtGpsBanner
import com.onlygoodthings.app.ui.components.OgtPill
import com.onlygoodthings.app.ui.components.OgtSectionTitle
import com.onlygoodthings.app.ui.components.OgtTopBar
import com.onlygoodthings.app.ui.components.ScreenColumn
import com.onlygoodthings.app.resources.Res
import com.onlygoodthings.app.resources.qs_gps
import com.onlygoodthings.app.ui.components.CircleIconButton
import com.onlygoodthings.app.ui.components.petMapDistance
import com.onlygoodthings.shared.domain.GeoPoint
import com.onlygoodthings.shared.domain.AdoptionProximity

/** Lost and found alerts share a map; adoption remains in the pet listing screen. */
@Composable
fun PetsMapScreen(onBack: () -> Unit, onReport: () -> Unit, onOpenPost: (String) -> Unit = {}) {
    val db = LocalOgtDb.current
    val session = LocalOgtSession.current
    val auth = LocalAuth.current
    val copy = LocalOgtCopy.current
    val here = auth.feedTimeline.viewerLocation
    val current = auth.discoveryDevicePoint()
    val radius=auth.feedTimeline.presentation.adoptionRadiusKm
    var selectedId by remember { mutableStateOf<String?>(null) }
    var fitVersion by remember { mutableStateOf(0) }
    var filter by remember { mutableStateOf(com.onlygoodthings.shared.domain.PetListingFilter.ALL) }
    val owner = session.me().id
    val socialTick by db.socialTick.collectAsState()
    val lost = remember(owner, socialTick,filter,here,radius) { db.animals.filter { com.onlygoodthings.shared.domain.isPetAlert(it.kind) && filter.matches(it.kind,it.resolved) &&
        com.onlygoodthings.shared.domain.PetDiscovery.allows(session.me().owns(it.reporterUserId),here,com.onlygoodthings.shared.domain.AdoptionProximity.point(it.latitude,it.longitude),radius) } }
    LaunchedEffect(owner,radius,here) { runCatching { auth.refreshDiscoveryScope();auth.refreshAnimalListings(db) } }
    val remoteIds = lost.filter { it.kind=="LOST" }.map { it.id }.filter {
        it.matches(Regex("[0-9a-fA-F]{8}(-[0-9a-fA-F]{4}){3}-[0-9a-fA-F]{12}"))
    }.sorted()
    var sightingsError by remember(owner) { mutableStateOf(false) }
    LaunchedEffect(owner, remoteIds) {
        sightingsError = false
        remoteIds.forEach { id ->
            try { auth.refreshAnimalSightings(id) }
            catch (cancel: kotlinx.coroutines.CancellationException) { throw cancel }
            catch (_: Exception) { sightingsError = true }
        }
    }
    val pins = db.mapPins.filter { it.kind == "ANIMAL" && com.onlygoodthings.shared.domain.PetDiscovery.allows(false,here,com.onlygoodthings.shared.domain.GeoPoint(it.latitude,it.longitude),radius) && (filter==com.onlygoodthings.shared.domain.PetListingFilter.ALL || lost.any { row ->row.id==it.id }) }
    val markers = buildList {
        if (here != null && auth.feedTimeline.viewerUsesManualPlace) add(BarrioMarker("zone", "ME", "Tu zona aproximada", here.latitude, here.longitude))
        current?.let { add(BarrioMarker("me", "LOCATION", "Tu ubicación actual", it.latitude, it.longitude)) }
        lost.forEach { animal ->
            val point = AdoptionProximity.point(animal.latitude, animal.longitude) ?: return@forEach
            val photo = db.posts.firstOrNull { it.listingId == animal.id }?.let { db.mediaOf(it.id).firstOrNull() }
            if (none { it.id == animal.id }) {
                add(
                    BarrioMarker(
                        animal.id,
                        "ORIGIN",
                        animal.petName.ifBlank { animal.title },
                        point.latitude,
                        point.longitude,
                        petPhoto = photo,
                    ),
                )
            }
            db.sightingsOf(animal.id).forEach { row ->
                add(
                    BarrioMarker(
                        row.id,
                        "SIGHT",
                        db.userOrNull(row.userId)?.displayName?.substringBefore(" ") ?: "Avistaje",
                        row.latitude,
                        row.longitude,
                        avatar = com.onlygoodthings.app.ui.components.neighborAvatarArt(row.userId),
                        photoUrl = db.userOrNull(row.userId)?.photoUrl,
                    ),
                )
            }
        }
        pins.filter { pin -> none { it.id == pin.id } }.forEach { pin ->
            add(BarrioMarker(pin.id, "ANIMAL", pin.title, pin.latitude, pin.longitude))
        }
    }
    val focus = markers.firstOrNull { it.id == selectedId && it.kind in setOf("ANIMAL", "ORIGIN", "SIGHT") }
        ?: markers.firstOrNull { it.kind in setOf("ORIGIN", "ANIMAL") }
        ?: markers.firstOrNull()
    val center = focus?.let { com.onlygoodthings.shared.domain.GeoPoint(it.latitude, it.longitude) } ?: here
    val selectedAnimal = lost.firstOrNull { animal -> animal.id == selectedId || db.sightingsOf(animal.id).any { it.id == selectedId } }
    val manualArea = here.takeIf { auth.feedTimeline.viewerUsesManualPlace }
    val markerPlaces = markers.filter { it.kind != "LOCATION" }.map { Triple(it.id, it.latitude, it.longitude) }
    val fitPoints = remember(selectedId, markerPlaces, current != null, fitVersion) {
        val ids = selectedAnimal?.let { animal -> db.sightingsOf(animal.id).map { it.id }.toSet() + animal.id + "me" + "zone" }
        markers.filter { ids == null || it.id in ids }.map { GeoPoint(it.latitude, it.longitude) }
    }
    val distanceFrom = current ?: manualArea
    val distanceReference = if (current != null) "tu ubicación" else "tu zona aproximada"

    Column(Modifier.fillMaxSize().background(OgtColors.canvas)) {
        OgtTopBar(title = "Perdidos y encontrados", onBack = onBack)
        ScreenColumn {
            OgtCaption(copy.petsMapHint)
            OgtCaption("Las distancias son aproximadas y en línea recta.")
            Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                listOf(com.onlygoodthings.shared.domain.PetListingFilter.ALL to "Todos",com.onlygoodthings.shared.domain.PetListingFilter.LOST to "Perdidos",com.onlygoodthings.shared.domain.PetListingFilter.FOUND to "Encontrados").forEach { (value,label) ->
                    androidx.compose.foundation.layout.Box(Modifier.clickable { filter=value;selectedId=null }) {
                        OgtPill(label,if(filter==value)OgtColors.secondary else OgtColors.sand,if(filter==value)androidx.compose.ui.graphics.Color.White else OgtColors.ink)
                    }
                }
            }
            if (sightingsError) OgtCaption("No pudimos actualizar todos los avistajes. Se conserva la última información disponible.")
            com.onlygoodthings.app.ui.components.DiscoveryPlacePrompt()
        }
        if (center != null) {
            Box(Modifier.padding(horizontal = 16.dp)) {
              OgtBarrioMap(
                center = center,
                radiusMeters = 2000,
                markers = markers,
                selectedId = selectedId ?: markers.firstOrNull { it.kind == "ORIGIN" }?.id,
                onSelect = { selectedId = it },
                height = 360.dp,
                fitPoints = fitPoints,
                fitRequest = fitVersion,
              )
              if (current != null) CircleIconButton(Res.drawable.qs_gps, "Ver mi ubicación y las mascotas",
                  onClick = { selectedId = null; fitVersion++ },
                  modifier = Modifier.align(Alignment.TopEnd).padding(10.dp), filled = true)
            }
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            ScreenColumn {
                OgtSectionTitle("Perdidos y encontrados")
                lost.forEach { animal ->
                    OgtCard {
                        OgtPill(if(animal.kind=="FOUND")"Encontrado · busca a su familia" else "Perdido")
                        OgtPill(animal.urgency)
                        Text(animal.title, fontWeight = FontWeight.Bold)
                        OgtCaption(animal.description)
                        OgtCaption(animal.place)
                        petMapDistance(distanceFrom, AdoptionProximity.point(animal.latitude, animal.longitude))?.let {
                            OgtCaption("A $it de $distanceReference")
                        }
                        db.sightingsOf(animal.id).forEach { sighting ->
                            petMapDistance(distanceFrom, GeoPoint(sighting.latitude, sighting.longitude))?.let {
                                OgtCaption("Avistamiento · a $it de $distanceReference")
                            }
                        }
                        db.posts.firstOrNull { it.listingId==animal.id }?.let { post ->
                            com.onlygoodthings.app.ui.components.OgtPrimaryButton("Ver aviso") { onOpenPost(post.id) }
                        }
                    }
                }
                pins.forEach { pin ->
                    OgtCard {
                        OgtPill("ANIMAL", OgtColors.sunset, OgtColors.sunsetText)
                        Text(pin.title, fontWeight = FontWeight.SemiBold)
                        OgtCaption(pin.subtitle)
                    }
                }
                com.onlygoodthings.app.ui.components.OgtPrimaryButton("Reportar animal") { onReport() }
                Spacer(Modifier.height(80.dp))
            }
        }
    }
}
