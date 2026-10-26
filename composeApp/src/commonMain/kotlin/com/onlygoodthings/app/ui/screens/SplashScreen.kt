package com.onlygoodthings.app.ui.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.onlygoodthings.app.resources.Res
import com.onlygoodthings.app.resources.ogt_logo
import com.onlygoodthings.app.theme.OgtColors
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.coroutineScope
import org.jetbrains.compose.resources.painterResource

/**
 * Splash nativa breve: cada ala entra por separado y el punto aparece al final.
 * El mismo PNG oficial se reutiliza sin modificar su forma. Sin carrusel, botones
 * ni red: la restauración de sesión es responsabilidad del arranque de App.
 */
@Composable
fun SplashScreen(onContinue: () -> Unit) {
    val left = remember { Animatable(0f) }
    val right = remember { Animatable(0f) }
    val center = remember { Animatable(0f) }
    val caption = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        coroutineScope {
            launch {
                left.animateTo(1f, spring(dampingRatio = 0.74f, stiffness = Spring.StiffnessLow))
            }
            launch {
                delay(150)
                right.animateTo(1f, spring(dampingRatio = 0.76f, stiffness = Spring.StiffnessLow))
            }
        }
        center.animateTo(1f, spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessMedium))
        caption.animateTo(1f, tween(240, easing = FastOutSlowInEasing))
        delay(170)
        onContinue()
    }

    Column(
        Modifier.fillMaxSize().background(OgtColors.canvas),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.weight(1f))
        val painter = painterResource(Res.drawable.ogt_logo)
        Box(Modifier.size(220.dp), contentAlignment = Alignment.Center) {
            Image(
                painter = painter,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize()
                    .drawWithContent {
                        clipRect(right = size.width * 0.48f) { drawContent() }
                    }.graphicsLayer {
                        translationX = -size.width * (1f - left.value) * 0.22f
                        rotationZ = -13f * (1f - left.value)
                        alpha = left.value
                    },
            )
            Image(
                painter = painter,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize()
                    .drawWithContent {
                        clipRect(left = size.width * 0.52f) { drawContent() }
                    }.graphicsLayer {
                        translationX = size.width * (1f - right.value) * 0.22f
                        rotationZ = 13f * (1f - right.value)
                        alpha = right.value
                    },
            )
            Box(
                Modifier.size(13.dp).scale(center.value.coerceAtLeast(0f))
                    .clip(CircleShape).background(OgtColors.muted),
            )
        }
        Spacer(Modifier.height(20.dp))
        Text(
            "Only Good Things",
            modifier = Modifier.graphicsLayer { alpha = caption.value },
            color = OgtColors.ink,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.weight(1f))
    }
}
