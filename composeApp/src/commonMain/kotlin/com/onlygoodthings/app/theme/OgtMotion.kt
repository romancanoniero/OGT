package com.onlygoodthings.app.theme

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize

/**
 * Motion Quiet Studio: preciso, aireado, sin prisa.
 * Mismo carácter que el parking (spring 0.86 / 210). Nada de rebote de red social.
 */
object OgtMotion {
    val chrome = spring<Float>(dampingRatio = 0.92f, stiffness = 380f)
    val layout = spring<Float>(dampingRatio = 0.86f, stiffness = 210f)
    val size = spring<IntSize>(dampingRatio = 0.86f, stiffness = 210f)
    val press = spring<Float>(dampingRatio = 0.78f, stiffness = 520f)
    val settle = spring<Float>(dampingRatio = 0.88f, stiffness = 280f)
    val fade = tween<Float>(durationMillis = 240, easing = FastOutSlowInEasing)
    val color = tween<Color>(durationMillis = 220, easing = FastOutSlowInEasing)
    val slide = tween<IntOffset>(durationMillis = 280, easing = FastOutSlowInEasing)

    val enterUp = fadeIn(fade) + slideInVertically(slide) { it / 14 }
    val exitDown = fadeOut(fade) + slideOutVertically(slide) { it / 16 }
    val enterDown = fadeIn(fade) + slideInVertically(slide) { -it / 18 }
    val exitUp = fadeOut(fade) + slideOutVertically(slide) { -it / 16 }

    const val pressScale = 0.96f
    const val clapPeak = 1.08f
    /** Mantener pulsado este tiempo arma el arrastre. */
    const val pressArmMs = 220
}
