package com.onlygoodthings.app.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.onlygoodthings.app.i18n.LocalOgtCopy
import com.onlygoodthings.app.theme.OgtColors

/**
 * Indicador de trabajo asíncrono (GPS, geocoder, REST).
 * No es icono de producto: anillo tinta Quiet Studio, el mismo en Android e iOS.
 */
@Composable
fun OgtLoader(
    modifier: Modifier = Modifier,
    label: String? = null,
    size: Dp = 36.dp,
) {
    val motion = rememberInfiniteTransition(label = "ogt-loader")
    val spin by motion.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(900, easing = LinearEasing), RepeatMode.Restart),
        label = "spin",
    )
    Column(
        modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Canvas(Modifier.size(size)) {
            val stroke = Stroke(width = size.toPx() * 0.12f, cap = StrokeCap.Round)
            val pad = stroke.width
            val arcSize = Size(this.size.width - pad * 2, this.size.height - pad * 2)
            val topLeft = Offset(pad, pad)
            drawArc(
                color = OgtColors.primary.copy(alpha = 0.18f),
                startAngle = 0f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = stroke,
            )
            rotate(spin) {
                drawArc(
                    color = OgtColors.primary,
                    startAngle = -90f,
                    sweepAngle = 110f,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = stroke,
                )
            }
        }
        if (!label.isNullOrBlank()) {
            Text(
                label,
                color = OgtColors.ink,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
            )
        }
    }
}

/** Cubre el área padre mientras corre una tarea suspendida. */
@Composable
fun OgtLoaderOverlay(
    visible: Boolean,
    modifier: Modifier = Modifier,
    label: String? = null,
) {
    if (!visible) return
    val copy = LocalOgtCopy.current
    Box(
        modifier
            .fillMaxSize()
            .background(Color(0xCCFAFAFA)),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier
                .background(OgtColors.surface, RoundedCornerShape(20.dp))
                .padding(horizontal = 22.dp, vertical = 18.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            OgtLoader(label = label ?: copy.loading, size = 40.dp)
        }
    }
}

/** Caja con contenido y overlay de carga. */
@Composable
fun OgtBusyBox(
    busy: Boolean,
    modifier: Modifier = Modifier,
    label: String? = null,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(modifier) {
        content()
        OgtLoaderOverlay(visible = busy, label = label)
    }
}
