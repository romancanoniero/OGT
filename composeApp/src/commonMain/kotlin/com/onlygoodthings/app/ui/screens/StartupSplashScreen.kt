package com.onlygoodthings.app.ui.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.onlygoodthings.app.auth.AuthRestore
import com.onlygoodthings.app.resources.Res
import com.onlygoodthings.app.resources.ogt_logo
import com.onlygoodthings.app.theme.OgtColors
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.painterResource

/** One cold-start sequence. Session restoration runs concurrently with the logo. */
@Composable
fun SplashScreen(restore: suspend () -> AuthRestore, onReady: (AuthRestore) -> Unit) {
    val reducedMotion = remember { com.onlygoodthings.app.platform.reduceStartupMotion() }
    val right = remember { Animatable(if (reducedMotion) 1f else 0f) }
    val left = remember { Animatable(if (reducedMotion) 1f else 0f) }
    val dot = remember { Animatable(if (reducedMotion) 1f else 0f) }
    val bloom = remember { Animatable(0f) }
    var waiting by remember { mutableStateOf(false) }
    val latestReady by rememberUpdatedState(onReady)
    LaunchedEffect(Unit) {
        val restored = async { restore() }
        if (reducedMotion) {
            waiting = true
            latestReady(restored.await())
            return@LaunchedEffect
        }
        val leftMotion = async {
            delay(140)
            left.animateTo(1f, tween(620, easing = FastOutSlowInEasing))
        }
        right.animateTo(1f, tween(620, easing = FastOutSlowInEasing))
        leftMotion.await()
        dot.animateTo(1.15f, tween(180, easing = FastOutSlowInEasing))
        val settle = async { dot.animateTo(1f, tween(140, easing = FastOutSlowInEasing)) }
        bloom.animateTo(1f, tween(260, easing = FastOutSlowInEasing))
        settle.await()
        waiting = !restored.isCompleted
        latestReady(restored.await())
    }
    Column(
        Modifier.fillMaxSize().background(OgtColors.canvas).safeDrawingPadding().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        // Clip the original transparent PNG into three layers: the exact brand artwork is preserved.
        Box(Modifier.size(200.dp), contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxSize()) {
                drawCircle(Brush.radialGradient(listOf(OgtColors.primary.copy(alpha = 0.055f * dot.value.coerceAtMost(1f)), Color.Transparent)))
                if (bloom.value > 0f) drawCircle(
                    color = OgtColors.primary.copy(alpha = (1f - bloom.value) * 0.22f),
                    radius = size.minDimension * (0.06f + 0.46f * bloom.value),
                    style = Stroke(width = 1.5.dp.toPx()),
                )
            }
            listOf(right.value, left.value, dot.value).forEachIndexed { part, progress ->
                Image(
                    painterResource(Res.drawable.ogt_logo),
                    contentDescription = if (part == 2) "Only Good Things" else null,
                    modifier = Modifier.fillMaxSize().graphicsLayer {
                        alpha = progress.coerceIn(0f, 1f)
                        transformOrigin = TransformOrigin(if (part == 0) 0.55f else 0.45f, 0.74f)
                        cameraDistance = 18f * density
                        rotationY = if (part == 2) 0f else (1f - progress) * if (part == 0) -40f else 40f
                        rotationZ = when (part) { 0 -> (1f - progress) * -38f; 1 -> (1f - progress) * 38f; else -> 0f }
                        translationY = (1f - progress) * size.height * if (part == 2) 0.05f else 0.12f
                        scaleX = if (part == 2) progress else 0.82f + 0.18f * progress
                        scaleY = scaleX
                    }.drawWithContent {
                        if (part == 2) {
                            clipRect(left = size.width * 0.44f, top = size.height * 0.44f,
                                right = size.width * 0.56f, bottom = size.height * 0.56f) {
                                this@drawWithContent.drawContent()
                            }
                        } else {
                            clipRect(left = if (part == 0) size.width / 2 else 0f,
                                right = if (part == 0) size.width else size.width / 2) {
                                clipRect(left = size.width * 0.44f, top = size.height * 0.44f,
                                    right = size.width * 0.56f, bottom = size.height * 0.56f,
                                    clipOp = ClipOp.Difference) { this@drawWithContent.drawContent() }
                            }
                        }
                    },
                )
            }
        }
        Spacer(Modifier.height(24.dp))
        Text("Only Good Things", color = OgtColors.ink, fontSize = 28.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text("RED SOLIDARIA DE COMUNIDAD", color = OgtColors.primary, fontSize = 11.sp, letterSpacing = 1.2.sp)
        if (waiting) {
            Spacer(Modifier.height(24.dp))
            androidx.compose.material3.CircularProgressIndicator(Modifier.size(20.dp), color = OgtColors.primary, strokeWidth = 2.dp)
        }
    }
}
