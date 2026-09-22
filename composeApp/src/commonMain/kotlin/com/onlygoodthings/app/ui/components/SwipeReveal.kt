package com.onlygoodthings.app.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.onlygoodthings.app.theme.OgtMotion
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

/**
 * Deslizar a la izquierda revela [actions] detrás de [content].
 * El gesto es de producto; el look lo pone quien lo llama.
 */
@Composable
fun SwipeReveal(
    modifier: Modifier = Modifier,
    revealWidth: Dp = 92.dp,
    openFraction: Float = 0.45f,
    actions: @Composable BoxScope.() -> Unit,
    content: @Composable () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val offset = remember { Animatable(0f) }
    val revealPx = with(LocalDensity.current) { revealWidth.toPx() }
    Box(modifier.fillMaxWidth()) {
        Box(Modifier.matchParentSize(), content = actions)
        Box(
            Modifier
                .offset { IntOffset(offset.value.roundToInt(), 0) }
                .pointerInput(revealPx, openFraction) {
                    detectHorizontalDragGestures(
                        onDragEnd = {
                            scope.launch {
                                val open = offset.value < -revealPx * openFraction
                                offset.animateTo(
                                    if (open) -revealPx else 0f,
                                    OgtMotion.layout,
                                )
                            }
                        },
                    ) { change, drag ->
                        change.consume()
                        scope.launch {
                            offset.snapTo((offset.value + drag).coerceIn(-revealPx, 0f))
                        }
                    }
                },
        ) {
            content()
        }
    }
}
