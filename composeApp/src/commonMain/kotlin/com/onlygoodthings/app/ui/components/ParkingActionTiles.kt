package com.onlygoodthings.app.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import androidx.compose.ui.unit.sp
import com.onlygoodthings.app.resources.Res
import com.onlygoodthings.app.resources.parking_action_find
import com.onlygoodthings.app.resources.parking_action_parked
import com.onlygoodthings.app.resources.parking_action_search
import com.onlygoodthings.app.resources.parking_action_yield
import com.onlygoodthings.app.theme.OgtColors
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource

private val TileRadius = 16.dp

enum class ParkingActionKind { PARKED, FIND, YIELD, SEARCH }

/** Cómo marcar el lugar del auto: GPS, dirección o mapa. */
enum class PlacePickKind { HERE, ADDRESS, MAP }

@Composable
fun ParkingActionRow(
    parkedLabel: String,
    yieldLabel: String,
    searchLabel: String,
    parkedEnabled: Boolean = true,
    yieldEnabled: Boolean = true,
    searchEnabled: Boolean = true,
    findParked: Boolean = false,
    yieldSelected: Boolean = false,
    showSearch: Boolean = true,
    showYield: Boolean = true,
    progress: Float = 0f,
    onParked: () -> Unit,
    onYield: () -> Unit,
    onSearch: () -> Unit,
) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        ParkingActionTile(
            if (findParked) ParkingActionKind.FIND else ParkingActionKind.PARKED,
            parkedLabel,
            parkedEnabled,
            progress,
            Modifier.weight(1f),
            onParked,
            selected = findParked && !yieldSelected,
        )
        if (showYield) {
            ParkingActionTile(
                ParkingActionKind.YIELD,
                yieldLabel,
                yieldEnabled,
                progress,
                Modifier.weight(1f),
                onYield,
                selected = yieldSelected,
            )
        }
        if (showSearch) {
            ParkingActionTile(ParkingActionKind.SEARCH, searchLabel, searchEnabled, progress, Modifier.weight(1f), onSearch)
        }
    }
}

@Composable
fun PlacePickRow(
    hereLabel: String,
    addressLabel: String,
    mapLabel: String,
    selected: PlacePickKind?,
    enabled: Boolean = true,
    onHere: () -> Unit,
    onAddress: () -> Unit,
    onMap: () -> Unit,
) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OgtActionTile(
            label = hereLabel,
            art = Res.drawable.parking_action_search,
            caption = "Marcar con la ubicación actual del GPS",
            enabled = enabled,
            selected = selected == PlacePickKind.HERE,
            progress = 0f,
            modifier = Modifier.weight(1f),
            onClick = onHere,
        )
        OgtActionTile(
            label = addressLabel,
            art = Res.drawable.parking_action_find,
            caption = "Buscar y normalizar una dirección",
            enabled = enabled,
            selected = selected == PlacePickKind.ADDRESS,
            progress = 0f,
            modifier = Modifier.weight(1f),
            onClick = onAddress,
        )
        OgtActionTile(
            label = mapLabel,
            art = Res.drawable.parking_action_parked,
            caption = "Ajustar el pin en el mapa",
            enabled = enabled,
            selected = selected == PlacePickKind.MAP,
            progress = 0f,
            modifier = Modifier.weight(1f),
            onClick = onMap,
        )
    }
}

@Composable
private fun ParkingActionTile(
    kind: ParkingActionKind,
    label: String,
    enabled: Boolean,
    progress: Float,
    modifier: Modifier,
    onClick: () -> Unit,
    selected: Boolean = false,
) {
    val art: DrawableResource = when (kind) {
        ParkingActionKind.PARKED -> Res.drawable.parking_action_parked
        ParkingActionKind.FIND -> Res.drawable.parking_action_find
        ParkingActionKind.YIELD -> Res.drawable.parking_action_yield
        ParkingActionKind.SEARCH -> Res.drawable.parking_action_search
    }
    val caption = when (kind) {
        ParkingActionKind.PARKED -> "Auto estacionado junto al pin"
        ParkingActionKind.FIND -> "Auto con lupa: dónde lo dejé"
        ParkingActionKind.YIELD -> "Ceder el lugar: llaves y dos autos"
        ParkingActionKind.SEARCH -> "Radar de la comunidad buscando un lugar"
    }
    OgtActionTile(label, art, caption, enabled, selected, progress, modifier, onClick)
}

@Composable
private fun OgtActionTile(
    label: String,
    art: DrawableResource,
    caption: String,
    enabled: Boolean,
    selected: Boolean,
    progress: Float,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    val fill = when {
        !enabled -> OgtColors.disabledFill
        selected -> OgtColors.stone
        else -> OgtColors.sand
    }
    val ink = if (enabled) OgtColors.ink else OgtColors.disabledInk
    val t = progress.coerceIn(0f, 1f)
    val labelAlpha = (1f - t * 1.35f).coerceIn(0f, 1f)
    val grayArt = ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(0f) })
    Column(
        modifier
            .height(lerp(132.dp, 72.dp, t))
            .clip(RoundedCornerShape(TileRadius))
            .background(fill)
            .border(1.dp, if (enabled) OgtColors.hairline else OgtColors.disabledLine, RoundedCornerShape(TileRadius))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(lerp(10.dp, 6.dp, t)),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(lerp(6.dp, 2.dp, t), Alignment.CenterVertically),
    ) {
        Image(
            painter = painterResource(art),
            contentDescription = caption,
            modifier = Modifier
                .size(lerp(72.dp, 40.dp, t))
                .graphicsLayer { alpha = if (enabled) 1f else 0.55f },
            contentScale = ContentScale.Fit,
            colorFilter = if (enabled) null else grayArt,
        )
        Text(
            label,
            modifier = Modifier
                .clipToBounds()
                .height(lerp(32.dp, 0.dp, t))
                .graphicsLayer { alpha = labelAlpha },
            color = ink,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
            lineHeight = 15.sp,
            maxLines = 2,
        )
    }
}
