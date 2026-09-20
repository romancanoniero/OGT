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
import androidx.compose.runtime.LaunchedEffect
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
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.painterResource

/** Aviso corto al cedente: la vacante ya se publicó y la comunidad está enterándose. */
@Composable
fun YieldNotifyingDialog(
    onDismiss: () -> Unit,
) {
    val copy = LocalOgtCopy.current
    val motion = rememberInfiniteTransition(label = "yield-notify")
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
    LaunchedEffect(Unit) {
        delay(2_800)
        onDismiss()
    }
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            dismissOnClickOutside = true,
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
                            scaleX = pulse
                            scaleY = pulse
                        },
                    contentScale = ContentScale.Fit,
                )
            }
            OgtLoader(size = 28.dp)
            Text(
                copy.yieldNotifyingTitle,
                color = OgtColors.ink,
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp,
                textAlign = TextAlign.Center,
            )
            OgtCaption(copy.notifyingDrivers)
            OgtCaption(copy.yieldNotifyingHint)
            GhostLink(copy.yieldNotifyingGotIt, onDismiss)
        }
    }
}
