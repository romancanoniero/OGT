package com.onlygoodthings.app.ui.screens

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.onlygoodthings.app.i18n.LocalOgtCopy
import com.onlygoodthings.app.resources.Res
import com.onlygoodthings.app.resources.parking_action_yield
import com.onlygoodthings.app.theme.OgtColors
import com.onlygoodthings.app.theme.OgtDimens
import com.onlygoodthings.app.ui.components.GhostLink
import com.onlygoodthings.app.ui.components.OgtCaption
import com.onlygoodthings.app.ui.components.OgtLoader
import org.jetbrains.compose.resources.painterResource

/** Pedido en curso: el buscador espera que el conductor que se va confirme la cesión. */
data class ClaimWaitUi(
    val spotId: String,
    val address: String,
    val vehicle: String?,
    val ownerName: String,
    val confirmed: Boolean = false,
)

@Composable
fun ClaimWaitingDialog(
    wait: ClaimWaitUi,
    onCancel: () -> Unit,
) {
    val copy = LocalOgtCopy.current
    val motion = rememberInfiniteTransition(label = "claim-wait")
    val pulse by motion.animateFloat(
        initialValue = 0.92f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
        label = "pulse",
    )
    val halo by motion.animateFloat(
        initialValue = 0.22f,
        targetValue = 0.55f,
        animationSpec = infiniteRepeatable(tween(1400), RepeatMode.Reverse),
        label = "halo",
    )
    Dialog(
        onDismissRequest = { if (!wait.confirmed) onCancel() },
        properties = DialogProperties(
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false,
        ),
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 28.dp)
                .shadow(18.dp, RoundedCornerShape(OgtDimens.cardRadius), ambientColor = OgtColors.ink.copy(alpha = 0.08f))
                .clip(RoundedCornerShape(OgtDimens.cardRadius))
                .background(OgtColors.surface)
                .border(1.dp, OgtColors.hairline, RoundedCornerShape(OgtDimens.cardRadius))
                .padding(horizontal = 22.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Box(
                    Modifier
                        .size(118.dp)
                        .graphicsLayer { alpha = halo }
                        .background(OgtColors.sand, CircleShape),
                )
                Image(
                    painter = painterResource(Res.drawable.parking_action_yield),
                    contentDescription = copy.yieldMySpot,
                    modifier = Modifier
                        .size(76.dp)
                        .graphicsLayer {
                            scaleX = if (wait.confirmed) 1f else pulse
                            scaleY = if (wait.confirmed) 1f else pulse
                        },
                    contentScale = ContentScale.Fit,
                )
            }
            if (!wait.confirmed) {
                OgtLoader(size = 28.dp)
            }
            Text(
                if (wait.confirmed) copy.claimConfirmed else copy.claimWaitingTitle,
                color = OgtColors.ink,
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp,
                textAlign = TextAlign.Center,
            )
            OgtCaption(copy.claimWaitingBody(wait.ownerName, wait.address))
            if (!wait.vehicle.isNullOrBlank()) {
                OgtCaption("${copy.lookForThisCar}: ${wait.vehicle}")
            }
            if (!wait.confirmed) {
                OgtCaption(copy.claimWaitingHint)
                GhostLink(copy.claimWaitingCancel, onCancel)
            }
        }
    }
}
