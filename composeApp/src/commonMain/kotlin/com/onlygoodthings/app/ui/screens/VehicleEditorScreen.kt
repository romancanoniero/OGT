package com.onlygoodthings.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.onlygoodthings.app.data.LocalOgtSession
import com.onlygoodthings.app.i18n.LocalOgtCopy
import com.onlygoodthings.app.i18n.OgtLang
import com.onlygoodthings.app.theme.OgtColors
import com.onlygoodthings.app.theme.ogtOutlinedFieldColors
import com.onlygoodthings.app.ui.components.CarSilhouette
import com.onlygoodthings.app.ui.components.GhostLink
import com.onlygoodthings.app.ui.components.OgtCaption
import com.onlygoodthings.app.ui.components.OgtPill
import com.onlygoodthings.app.ui.components.OgtPrimaryButton
import com.onlygoodthings.app.ui.components.OgtTopBar
import com.onlygoodthings.app.ui.components.ScreenColumn
import com.onlygoodthings.shared.domain.VehicleProfile

internal val GarageColors = listOf(
    Triple("Blanco", "White", "#F4F4F5"),
    Triple("Negro", "Black", "#18181B"),
    Triple("Gris", "Gray", "#71717A"),
    Triple("Rojo", "Red", "#DC2626"),
    Triple("Azul", "Blue", "#2563EB"),
    Triple("Verde", "Green", "#059669"),
    Triple("Naranja", "Orange", "#EA580C"),
)

/** Alta o edición de un auto del garage. Borrar también vive acá. */
@Composable
fun VehicleEditorScreen(vehicleId: String?, onBack: () -> Unit) {
    val session = LocalOgtSession.current
    val copy = LocalOgtCopy.current
    val en = copy.lang == OgtLang.EN
    val existing = vehicleId?.let { id -> session.vehicles.firstOrNull { it.id == id } }
    var make by remember { mutableStateOf(existing?.make.orEmpty()) }
    var plate by remember { mutableStateOf(existing?.plate.orEmpty()) }
    var colorName by remember {
        mutableStateOf(existing?.color?.ifBlank { null } ?: if (en) "Gray" else "Gris")
    }
    var colorHex by remember { mutableStateOf(existing?.colorHex ?: "#71717A") }
    val draft = VehicleProfile(
        id = existing?.id.orEmpty(),
        make = make.trim(),
        color = colorName,
        colorHex = colorHex,
        plate = plate.trim(),
    )
    Column(Modifier.fillMaxSize().background(OgtColors.canvas).verticalScroll(rememberScrollState())) {
        OgtTopBar(
            title = if (existing != null) copy.editCarTitle else copy.newCarTitle,
            onBack = onBack,
            hideOnScroll = false,
        )
        ScreenColumn {
            OgtCaption(copy.vehicleWhy)
            CarSilhouette(colorHex)
            OutlinedTextField(
                value = make,
                onValueChange = { make = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text(copy.vehicleMakeHint, color = OgtColors.muted) },
                colors = ogtOutlinedFieldColors(),
                shape = RoundedCornerShape(16.dp),
                singleLine = true,
            )
            OutlinedTextField(
                value = plate,
                onValueChange = { plate = it.uppercase() },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text(copy.vehiclePlateHint, color = OgtColors.muted) },
                colors = ogtOutlinedFieldColors(),
                shape = RoundedCornerShape(16.dp),
                singleLine = true,
            )
            Row(
                Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                GarageColors.forEach { (es, english, hex) ->
                    val label = if (en) english else es
                    val on = colorName == label || colorName == es || colorName == english
                    Box(Modifier.clickable { colorName = label; colorHex = hex }) {
                        OgtPill(
                            label,
                            if (on) OgtColors.primary else OgtColors.sand,
                            if (on) OgtColors.onPrimary else OgtColors.ink,
                        )
                    }
                }
            }
            if (!draft.isReady()) OgtCaption(copy.vehicleNeeded)
            OgtPrimaryButton(copy.saveCar, enabled = draft.isReady()) {
                session.upsertVehicle(draft)
                onBack()
            }
            if (existing != null) {
                GhostLink(copy.deleteCar) {
                    session.removeVehicle(existing.id)
                    onBack()
                }
            }
            Spacer(Modifier.height(80.dp))
        }
    }
}
