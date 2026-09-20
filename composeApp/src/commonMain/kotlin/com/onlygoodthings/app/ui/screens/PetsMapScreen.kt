package com.onlygoodthings.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.onlygoodthings.app.data.LocalOgtDb
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

/** Mapa solo de mascotas perdidas. No mezcla estacionamiento. */
@Composable
fun PetsMapScreen(onBack: () -> Unit, onReport: () -> Unit) {
    val db = LocalOgtDb.current
    val session = LocalOgtSession.current
    val copy = LocalOgtCopy.current
    val here = session.here()
    var selectedId by remember { mutableStateOf<String?>(null) }
    val lost = db.animals.filter { it.kind == "LOST" && !it.resolved }
    val pins = db.mapPins.filter { it.kind == "ANIMAL" }
    val markers = buildList {
        if (here != null) add(BarrioMarker("me", "ME", copy.mePin, here.latitude, here.longitude))
        pins.forEach { pin ->
            add(BarrioMarker(pin.id, "ANIMAL", pin.title, pin.latitude, pin.longitude))
        }
        lost.forEach { animal ->
            val lat = animal.latitude ?: db.userOrNull(animal.reporterUserId)?.latitude ?: return@forEach
            val lng = animal.longitude ?: db.userOrNull(animal.reporterUserId)?.longitude ?: return@forEach
            if (none { it.id == animal.id }) {
                add(
                    BarrioMarker(
                        animal.id,
                        "ORIGIN",
                        animal.petName.ifBlank { animal.title },
                        lat,
                        lng,
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
                    ),
                )
            }
        }
    }
    val focus = markers.firstOrNull { it.id == selectedId && it.kind in setOf("ANIMAL", "ORIGIN", "SIGHT") }
        ?: markers.firstOrNull { it.kind in setOf("ORIGIN", "ANIMAL") }
        ?: markers.firstOrNull()
    val center = focus?.let { com.onlygoodthings.shared.domain.GeoPoint(it.latitude, it.longitude) } ?: here

    Column(Modifier.fillMaxSize().background(OgtColors.canvas)) {
        OgtTopBar(title = copy.petsMapTitle, onBack = onBack)
        ScreenColumn {
            OgtCaption(copy.petsMapHint)
            if (here == null) OgtGpsBanner()
        }
        if (center != null) {
            OgtBarrioMap(
                center = center,
                radiusMeters = 2000,
                markers = markers,
                selectedId = selectedId ?: markers.firstOrNull { it.kind == "ORIGIN" }?.id,
                onSelect = { selectedId = it },
                modifier = Modifier.padding(horizontal = 16.dp),
                height = 360.dp,
            )
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            ScreenColumn {
                OgtSectionTitle(copy.petsMapTitle)
                lost.forEach { animal ->
                    OgtCard {
                        OgtPill(animal.urgency)
                        Text(animal.title, fontWeight = FontWeight.Bold)
                        OgtCaption(animal.description)
                        OgtCaption(animal.place)
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
