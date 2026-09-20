package com.onlygoodthings.app.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import com.onlygoodthings.app.resources.Res
import com.onlygoodthings.app.resources.parking_maneuver_allegory
import com.onlygoodthings.app.theme.OgtColors
import com.onlygoodthings.app.ui.components.GhostLink
import com.onlygoodthings.app.ui.components.OgtCaption
import com.onlygoodthings.app.ui.components.OgtPill
import com.onlygoodthings.app.ui.components.OgtPrimaryButton
import com.onlygoodthings.app.ui.components.OgtSecondaryButton
import com.onlygoodthings.app.ui.components.OgtSectionTitle
import com.onlygoodthings.shared.domain.ParkingManeuver
import org.jetbrains.compose.resources.painterResource

/** Diálogo al detectar una maniobra de estacionamiento. Ilustración Stitch. */
@Composable
fun ParkingManeuverDialog(
    maneuver: ParkingManeuver,
    onMemorize: () -> Unit,
    onYield: () -> Unit,
    onDismiss: () -> Unit,
) {
    val kind = when (maneuver) {
        ParkingManeuver.LATERAL -> "estacionamiento lateral"
        ParkingManeuver.HEAD_IN -> "entrada de frente"
        ParkingManeuver.REVERSE -> "marcha atrás a cochera"
    }
    Column(
        Modifier
            .fillMaxSize()
            .background(OgtColors.canvas)
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        OgtPill("¿Vas con tu auto?")
        Image(
            painter = painterResource(Res.drawable.parking_maneuver_allegory),
            contentDescription = "Auto acomodándose y alguien acercándose al lugar libre",
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp)
                .clip(RoundedCornerShape(28.dp)),
            contentScale = ContentScale.Crop,
        )
        OgtSectionTitle("Detectamos un $kind")
        OgtCaption("Si llegaste en auto, podemos memorizar este lugar para que lo encuentres después. Y si te vas, cederlo es una buena acción: otra persona gana tiempo y vos sumás puntos.")
        OgtPill("+${com.onlygoodthings.shared.domain.ParkingRules.DEFAULT_REWARD_POINTS} pts si cedés el lugar", OgtColors.sunset, OgtColors.sunsetText)
        OgtPrimaryButton("Memorizar este lugar") { onMemorize() }
        OgtSecondaryButton("Ceder el lugar y ganar puntos") { onYield() }
        GhostLink("No era yo") { onDismiss() }
        Spacer(Modifier.height(16.dp))
    }
}
