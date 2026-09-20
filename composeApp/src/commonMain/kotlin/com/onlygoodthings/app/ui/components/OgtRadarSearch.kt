package com.onlygoodthings.app.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.onlygoodthings.app.theme.OgtColors
import kotlin.math.min

/**
 * Barrido tipo Lottie: anillos que crecen y un haz que gira.
 * Compose puro, el mismo en Android e iOS.
 */
@Composable
fun OgtRadarSearch(
    label: String,
    modifier: Modifier = Modifier,
) {
    val motion = rememberInfiniteTransition(label = "radar")
    val sweep by motion.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(2200, easing = LinearEasing), RepeatMode.Restart),
        label = "sweep",
    )
    val pulse by motion.animateFloat(
        initialValue = 0.18f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1600, easing = LinearEasing), RepeatMode.Restart),
        label = "pulse",
    )
    Box(modifier.background(OgtColors.ink.copy(alpha = 0.28f))) {
        Canvas(Modifier.fillMaxSize()) {
            val cx = size.width / 2f
            val cy = size.height / 2f
            val maxR = min(cx, cy) * 0.92f
            val navy = OgtColors.secondary
            drawCircle(navy.copy(alpha = 0.12f), radius = maxR, center = Offset(cx, cy))
            repeat(3) { ring ->
                val t = ((pulse + ring * 0.28f) % 1f)
                drawCircle(
                    color = navy.copy(alpha = (1f - t) * 0.45f),
                    radius = maxR * (0.22f + t * 0.78f),
                    center = Offset(cx, cy),
                    style = Stroke(width = 3.5f),
                )
            }
            rotate(sweep, Offset(cx, cy)) {
                drawArc(
                    brush = Brush.sweepGradient(
                        0f to Color.Transparent,
                        0.72f to Color.Transparent,
                        1f to navy.copy(alpha = 0.55f),
                    ),
                    startAngle = 0f,
                    sweepAngle = 70f,
                    useCenter = true,
                    topLeft = Offset(cx - maxR, cy - maxR),
                    size = Size(maxR * 2, maxR * 2),
                )
            }
            drawCircle(OgtColors.primary, radius = 10f, center = Offset(cx, cy))
            drawCircle(Color.White, radius = 3.5f, center = Offset(cx, cy))
        }
        Text(
            label,
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 14.dp),
            color = Color.White,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}
