package com.onlygoodthings.app.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import kotlin.math.PI
import kotlin.math.atan2
import com.onlygoodthings.app.platform.OgtPickedMedia
import com.onlygoodthings.app.platform.exportFramedJpeg
import com.onlygoodthings.app.platform.loadFramePreview
import com.onlygoodthings.shared.domain.PostFrame
import com.onlygoodthings.app.theme.OgtColors
import com.onlygoodthings.app.theme.OgtMotion
import kotlinx.coroutines.launch

private class FrameGesture {
    var zoom = 1f
    var panX = 0f
    var panY = 0f
    var rotation = 0f
}

/**
 * El encuadre se dibuja en la misma ventana que el feed.
 * Un diálogo aparte en iOS recibe el toque, pero no el arrastre.
 */
class OgtOverlayHost {
    var content by mutableStateOf<(@Composable () -> Unit)?>(null)
}

val LocalOgtOverlay = staticCompositionLocalOf<OgtOverlayHost?> { null }

/**
 * Encuadre de una foto de post al 16:9 del feed, con la regla de tercios.
 * Confirmar exporta el archivo original. Cancelar no lo sube.
 */
@Composable
fun OgtPostFrameSheet(
    source: OgtPickedMedia,
    onCancel: () -> Unit,
    onConfirm: (framedPath: String) -> Unit,
) {
    val scope = rememberCoroutineScope()
    var bitmap by remember(source.path) { mutableStateOf<ImageBitmap?>(null) }
    var failed by remember(source.path) { mutableStateOf(false) }
    val gesture = remember(source.path) { FrameGesture() }
    var zoom by remember(source.path) { mutableFloatStateOf(1f) }
    var panX by remember(source.path) { mutableFloatStateOf(0f) }
    var panY by remember(source.path) { mutableFloatStateOf(0f) }
    var rotation by remember(source.path) { mutableFloatStateOf(0f) }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(source.path) {
        val loaded = runCatching { loadFramePreview(source.path) }.getOrNull()
        bitmap = loaded
        failed = loaded == null
    }
    val host = LocalOgtOverlay.current
    val panel: @Composable () -> Unit = {
        Box(Modifier.fillMaxSize().zIndex(8f)) {
        AnimatedVisibility(visible = true, enter = OgtMotion.enterUp, exit = OgtMotion.exitDown) {
            Column(
                Modifier
                    .fillMaxSize()
                    .background(OgtColors.canvas)
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .padding(horizontal = 20.dp, vertical = 12.dp),
            ) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    FrameTextButton("Cancelar", enabled = !saving, onClick = onCancel)
                    Text("Encuadre", fontWeight = FontWeight.SemiBold, fontSize = 16.sp, color = OgtColors.ink)
                    FrameTextButton(
                        label = "Listo",
                        enabled = !saving && bitmap != null,
                        emphasis = true,
                    ) {
                        if (bitmap == null) return@FrameTextButton
                        saving = true
                        error = null
                        scope.launch {
                            val framed = runCatching {
                                exportFramedJpeg(source.path, zoom, panX, panY, rotation)
                            }.getOrNull()
                            if (framed == null) {
                                saving = false
                                error = "No se pudo encuadrar la foto"
                                return@launch
                            }
                            onConfirm(framed)
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))
                val shot = bitmap
                Box(
                    Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center,
                ) {
                    if (shot == null) {
                        Text(
                            if (failed) "No se pudo leer la foto" else "Preparando la foto…",
                            color = if (failed) OgtColors.error else OgtColors.muted,
                            fontSize = 14.sp,
                        )
                    } else {
                        FrameViewport(
                            bitmap = shot,
                            gesture = gesture,
                            zoom = zoom,
                            panX = panX,
                            panY = panY,
                            rotation = rotation,
                            enabled = !saving,
                            onChange = { nextZoom, nextPanX, nextPanY, nextRotation ->
                                zoom = nextZoom
                                panX = nextPanX
                                panY = nextPanY
                                rotation = nextRotation
                            },
                        )
                    }
                }
                Text(
                    "Arrastrá para mover. Pellizcá para acercar y rotá con dos dedos.",
                    color = OgtColors.muted,
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(top = 14.dp),
                )
                if (!error.isNullOrBlank()) {
                    Text(
                        error.orEmpty(),
                        color = OgtColors.error,
                        fontSize = 13.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    )
                }
            }
        }
        }
    }
    if (host != null) {
        DisposableEffect(host, source.path) {
            host.content = panel
            onDispose {
                if (host.content === panel) host.content = null
            }
        }
    }
}

@Composable
private fun FrameViewport(
    bitmap: ImageBitmap,
    gesture: FrameGesture,
    zoom: Float,
    panX: Float,
    panY: Float,
    rotation: Float,
    enabled: Boolean,
    onChange: (zoom: Float, panX: Float, panY: Float, rotation: Float) -> Unit,
) {
    val shape = RoundedCornerShape(16.dp)
    Canvas(
        Modifier
            .fillMaxWidth()
            .aspectRatio(PostFrame.ASPECT.toFloat())
            .clip(shape)
            .background(OgtColors.sand)
            .border(1.dp, OgtColors.hairline, shape)
            .pointerInput(bitmap, enabled) {
                if (!enabled) return@pointerInput
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val points = mutableMapOf(down.id to down.position)
                    var past = false
                    var total = Offset.Zero
                    var pinchDist = 0f
                    var pinchAngle = 0f
                    val slop = viewConfiguration.touchSlop
                    while (true) {
                        val event = awaitPointerEvent()
                        event.changes.forEach { change ->
                            if (change.pressed) points[change.id] = change.position
                            else points.remove(change.id)
                        }
                        val pressed = event.changes.filter { it.pressed }
                        if (pressed.isEmpty()) break
                        val viewW = size.width.toFloat().coerceAtLeast(1f)
                        val viewH = size.height.toFloat().coerceAtLeast(1f)
                        val pts = points.values.toList()
                        var pan = Offset.Zero
                        var zoomFactor = 1f
                        var turn = 0f
                        if (pts.size >= 2) {
                            val a = pts[0]
                            val b = pts[1]
                            val dist = (b - a).getDistance().coerceAtLeast(1f)
                            var angle = atan2(b.y - a.y, b.x - a.x) * (180f / PI.toFloat())
                            if (pinchDist > 1f) {
                                zoomFactor = dist / pinchDist
                                turn = angle - pinchAngle
                                if (turn > 180f) turn -= 360f
                                if (turn < -180f) turn += 360f
                                past = true
                            }
                            pinchDist = dist
                            pinchAngle = angle
                        } else {
                            pinchDist = 0f
                            pan = pressed.first().positionChange()
                            total += pan
                            if (!past && total.getDistance() > slop) past = true
                        }
                        if (past && (pan != Offset.Zero || zoomFactor != 1f || turn != 0f)) {
                            val oldZoom = gesture.zoom.coerceIn(PostFrame.MIN_ZOOM.toFloat(), PostFrame.MAX_ZOOM.toFloat())
                            val nextZoom = (oldZoom * zoomFactor).coerceIn(
                                PostFrame.MIN_ZOOM.toFloat(),
                                PostFrame.MAX_ZOOM.toFloat(),
                            )
                            val nextRotation = gesture.rotation + turn
                            val (clampedX, clampedY) = PostFrame.clampPan(
                                gesture.panX + (pan.x / viewW).toDouble(),
                                gesture.panY + (pan.y / viewH).toDouble(),
                                bitmap.width.toDouble(),
                                bitmap.height.toDouble(),
                                nextRotation.toDouble(),
                                nextZoom.toDouble(),
                                viewW.toDouble(),
                                viewH.toDouble(),
                            )
                            gesture.zoom = nextZoom
                            gesture.panX = clampedX.toFloat()
                            gesture.panY = clampedY.toFloat()
                            gesture.rotation = nextRotation
                            onChange(gesture.zoom, gesture.panX, gesture.panY, gesture.rotation)
                            pressed.forEach { it.consume() }
                        }
                    }
                }
            },
    ) {
        val viewW = size.width
        val viewH = size.height
        val scale = PostFrame.coverScale(
            bitmap.width.toDouble(),
            bitmap.height.toDouble(),
            rotation.toDouble(),
            viewW.toDouble(),
            viewH.toDouble(),
        ) * zoom.toDouble().coerceIn(PostFrame.MIN_ZOOM, PostFrame.MAX_ZOOM)
        withTransform({
            translate(viewW / 2f + panX * viewW, viewH / 2f + panY * viewH)
            rotate(rotation, Offset.Zero)
            scale(scale.toFloat(), scale.toFloat(), Offset.Zero)
        }) {
            drawImage(bitmap, topLeft = Offset(-bitmap.width / 2f, -bitmap.height / 2f))
        }
        val grid = OgtColors.canvas.copy(alpha = 0.88f)
        val shade = OgtColors.ink.copy(alpha = 0.28f)
        for (step in 1..2) {
            val x = viewW * step / 3f
            val y = viewH * step / 3f
            drawLine(shade, Offset(x + 1f, 0f), Offset(x + 1f, viewH), strokeWidth = 1f)
            drawLine(shade, Offset(0f, y + 1f), Offset(viewW, y + 1f), strokeWidth = 1f)
            drawLine(grid, Offset(x, 0f), Offset(x, viewH), strokeWidth = 1f)
            drawLine(grid, Offset(0f, y), Offset(viewW, y), strokeWidth = 1f)
        }
        drawRect(OgtColors.hairline, style = Stroke(width = 1f))
    }
}

@Composable
private fun FrameTextButton(
    label: String,
    enabled: Boolean,
    emphasis: Boolean = false,
    onClick: () -> Unit,
) {
    val press = remember { MutableInteractionSource() }
    val pressed by press.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed && enabled) OgtMotion.pressScale else 1f,
        animationSpec = OgtMotion.press,
        label = "frame-btn",
    )
    val fill = when {
        !enabled -> OgtColors.disabledFill
        emphasis -> OgtColors.secondary
        else -> OgtColors.sand
    }
    val ink = when {
        !enabled -> OgtColors.disabledInk
        emphasis -> OgtColors.onPrimary
        else -> OgtColors.ink
    }
    Box(
        Modifier
            .scale(scale)
            .clip(CircleShape)
            .background(fill)
            .clickable(interactionSource = press, indication = null, enabled = enabled, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = ink, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
    }
}
