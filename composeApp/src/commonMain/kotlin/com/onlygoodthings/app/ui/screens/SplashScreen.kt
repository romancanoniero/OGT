package com.onlygoodthings.app.ui.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

/**
 * Splash de marca: derecha, izquierda, y al final el punto central.
 * Solo se reproduce una vez por arranque; no hace red ni simula progreso.
 * La restauración de Firebase ocurre en App después de la coreografía.
 */
@Composable
fun SplashScreen(onContinue: () -> Unit) {
    val right = remember { Animatable(0f) }
    val left = remember { Animatable(0f) }
    val dot = remember { Animatable(0f) }
    val title = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        // Entrada escalonada (sin bucles que ralenticen la apertura).
        right.animateTo(1f, tween(510, easing = FastOutSlowInEasing))
        left.animateTo(1f, tween(510, easing = FastOutSlowInEasing))
        dot.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow))
        title.animateTo(1f, tween(230, easing = LinearOutSlowInEasing))
        delay(140)
        onContinue()
    }

    val leftShape = remember {
        Path().apply {
            moveTo(101f, 17f)
            cubicTo(54f, 18f, 24f, 49f, 24f, 96f)
            cubicTo(24f, 143f, 59f, 170f, 101f, 165f)
            cubicTo(77f, 149f, 66f, 131f, 66f, 107f)
            cubicTo(66f, 94f, 71f, 83f, 79f, 74f)
            cubicTo(76f, 94f, 80f, 112f, 91f, 126f)
            cubicTo(82f, 91f, 86f, 48f, 101f, 17f)
            close()
        }
    }
    val rightShape = remember {
        Path().apply {
            moveTo(119f, 17f)
            cubicTo(166f, 18f, 196f, 49f, 196f, 96f)
            cubicTo(196f, 143f, 161f, 170f, 119f, 165f)
            cubicTo(143f, 149f, 154f, 131f, 154f, 107f)
            cubicTo(154f, 94f, 149f, 83f, 141f, 74f)
            cubicTo(144f, 94f, 140f, 112f, 129f, 126f)
            cubicTo(138f, 91f, 134f, 48f, 119f, 17f)
            close()
        }
    }
    Box(
        Modifier.fillMaxSize().background(Color.White),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Canvas(Modifier.size(width = 242.dp, height = 212.dp)) {
                val factor = size.width / 220f
                val top = (size.height - 180f * factor) / 2
                translate(top = top) {
                    scale(factor, factor, Offset.Zero) {
                        // El ala derecha se despliega y ocupa su posición definitiva.
                        translate(left = (1f - right.value) * 105f) {
                            rotate(28f * (1f - right.value), pivot = Offset(110f, 97f)) {
                                drawPath(
                                    rightShape,
                                    brush = Brush.linearGradient(
                                        listOf(Color(0xFF174E72), Color(0xFF062D4D)),
                                        start = Offset(122f, 15f), end = Offset(190f, 162f),
                                    ),
                                    alpha = right.value.coerceIn(0f, 1f),
                                )
                            }
                        }
                        // El ala izquierda responde con una apertura espejada.
                        translate(left = -(1f - left.value) * 105f) {
                            rotate(-28f * (1f - left.value), pivot = Offset(110f, 97f)) {
                                drawPath(
                                    leftShape,
                                    brush = Brush.linearGradient(
                                        listOf(Color(0xFF252936), Color(0xFF101820)),
                                        start = Offset(60f, 35f), end = Offset(105f, 160f),
                                    ),
                                    alpha = left.value.coerceIn(0f, 1f),
                                )
                            }
                        }
                        if (dot.value > 0f) {
                            val radius = 11f * dot.value.coerceAtMost(1.16f)
                            drawCircle(
                                color = Color(0xFF5D6976),
                                radius = radius,
                                center = Offset(110f, 96f),
                                alpha = dot.value.coerceIn(0f, 1f),
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.size(14.dp))
            Text(
                "Only Good Things",
                fontSize = 28.sp,
                fontWeight = FontWeight.ExtraBold,
                color = Color(0xFF161B21).copy(alpha = title.value),
                textAlign = TextAlign.Center,
            )
            Text(
                "UNA COMUNIDAD QUE SUMA",
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 2.sp,
                color = Color(0xFF174E72).copy(alpha = title.value),
            )
        }
    }
}
