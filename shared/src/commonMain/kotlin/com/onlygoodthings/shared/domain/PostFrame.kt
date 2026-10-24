package com.onlygoodthings.shared.domain

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Encuadre de la foto de un post.
 * La proporción es la del feed (16:9). Con zoom 1 la foto cubre el marco.
 * [panX] y [panY] son fracción del ancho y del alto del marco.
 */
object PostFrame {
    const val ASPECT = 16.0 / 9.0
    const val MIN_ZOOM = 1.0
    const val MAX_ZOOM = 4.0
    /** Lado largo al guardar. En el teléfono y en el escritorio no se distingue de más. */
    const val MAX_EDGE = 2048
    /** JPEG que conserva el detalle. Por debajo de esto se notan los bordes y la piel. */
    const val JPEG_QUALITY = 92

    fun coverScale(
        imageW: Double,
        imageH: Double,
        rotationDeg: Double,
        viewW: Double,
        viewH: Double,
    ): Double {
        val rad = rotationDeg * PI / 180.0
        val c = abs(cos(rad))
        val s = abs(sin(rad))
        val projW = imageW * c + imageH * s
        val projH = imageW * s + imageH * c
        if (projW <= 1.0 || projH <= 1.0 || viewW <= 1.0 || viewH <= 1.0) return 1.0
        return max(viewW / projW, viewH / projH)
    }

    fun clampPan(
        panX: Double,
        panY: Double,
        imageW: Double,
        imageH: Double,
        rotationDeg: Double,
        zoom: Double,
        viewW: Double,
        viewH: Double,
    ): Pair<Double, Double> {
        val scale = coverScale(imageW, imageH, rotationDeg, viewW, viewH) * zoom.coerceIn(MIN_ZOOM, MAX_ZOOM)
        val rad = rotationDeg * PI / 180.0
        val c = abs(cos(rad))
        val s = abs(sin(rad))
        val drawnW = (imageW * c + imageH * s) * scale
        val drawnH = (imageW * s + imageH * c) * scale
        val maxX = if (viewW <= 1.0) 0.0 else max(0.0, (drawnW - viewW) / 2.0) / viewW
        val maxY = if (viewH <= 1.0) 0.0 else max(0.0, (drawnH - viewH) / 2.0) / viewH
        return panX.coerceIn(-maxX, maxX) to panY.coerceIn(-maxY, maxY)
    }

    /** Píxeles del JPEG de salida. No agranda una foto chica. */
    fun outputSize(imageW: Int, imageH: Int, rotationDeg: Double, zoom: Double): Pair<Int, Int> {
        val viewW = 1000.0
        val viewH = viewW / ASPECT
        val scale = coverScale(imageW.toDouble(), imageH.toDouble(), rotationDeg, viewW, viewH) *
            zoom.coerceIn(MIN_ZOOM, MAX_ZOOM)
        val sampleW = if (scale <= 0.0) 16 else (viewW / scale).roundToInt().coerceAtLeast(16)
        var outW = min(MAX_EDGE, sampleW)
        if (outW % 2 != 0) outW -= 1
        if (outW < 16) outW = 16
        val outH = (outW / ASPECT).roundToInt().let { if (it % 2 == 0) it else it - 1 }.coerceAtLeast(8)
        return outW to outH
    }
}
