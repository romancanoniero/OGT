package com.onlygoodthings.app.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.onlygoodthings.app.resources.Res
import com.onlygoodthings.app.resources.parking_action_parked
import com.onlygoodthings.app.theme.OgtColors
import org.jetbrains.compose.resources.painterResource

/** Auto 3D Quiet Studio (Stitch). Sin tint: el PNG ya trae harbor/slate. */
@Composable
fun CarSilhouette(
    colorHex: String,
    modifier: Modifier = Modifier,
    width: Dp = 92.dp,
) {
    val paint = vehiclePaint(colorHex)
    val well = RoundedCornerShape(16.dp)
    Box(
        modifier
            .size(width)
            .clip(well)
            .background(OgtColors.sand)
            .border(1.dp, OgtColors.hairline, well),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            painter = painterResource(Res.drawable.parking_action_parked),
            contentDescription = colorHex.ifBlank { "Auto" },
            modifier = Modifier.size(width * 0.82f),
            contentScale = ContentScale.Fit,
        )
        Box(
            Modifier
                .align(Alignment.BottomEnd)
                .padding(6.dp)
                .size(14.dp)
                .clip(CircleShape)
                .background(paint)
                .border(1.dp, OgtColors.hairline, CircleShape),
        )
    }
}

/** Hex del vehículo; si viene vacío o inválido, slate de sistema. */
internal fun vehiclePaint(hex: String): Color {
    val h = hex.trim().removePrefix("#")
    val value = h.toLongOrNull(16) ?: return OgtColors.muted
    return when (h.length) {
        6 -> Color(0xFF000000L or value)
        8 -> Color(value)
        else -> OgtColors.muted
    }
}
