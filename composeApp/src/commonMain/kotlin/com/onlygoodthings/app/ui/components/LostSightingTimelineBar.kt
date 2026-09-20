package com.onlygoodthings.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.onlygoodthings.app.theme.OgtColors
import com.onlygoodthings.app.theme.OgtDimens
import com.onlygoodthings.shared.domain.LostTimelineStop
import kotlin.math.abs
import kotlinx.coroutines.flow.distinctUntilChanged

/**
 * Rueda horizontal estilo iOS: las fechas giran bajo un triángulo fijo al centro.
 * Escala, fade y un poco de perspectiva; el snap elige el día.
 */
@Composable
fun LostSightingTimelineBar(
    stops: List<LostTimelineStop>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (stops.isEmpty()) return
    val safeIndex = selectedIndex.coerceIn(stops.indices)
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = safeIndex)
    val snap = rememberSnapFlingBehavior(lazyListState = listState)
    val density = LocalDensity.current
    val itemWidth = 80.dp
    var viewportPx by remember { mutableStateOf(0f) }

    LaunchedEffect(listState) {
        snapshotFlow {
            val info = listState.layoutInfo
            val center = (info.viewportStartOffset + info.viewportEndOffset) / 2
            info.visibleItemsInfo.minByOrNull { item ->
                abs((item.offset + item.size / 2) - center)
            }?.index
        }.distinctUntilChanged().collect { index ->
            if (index != null && index != selectedIndex) onSelect(index)
        }
    }
    LaunchedEffect(safeIndex, viewportPx) {
        if (listState.isScrollInProgress) return@LaunchedEffect
        val info = listState.layoutInfo
        val center = (info.viewportStartOffset + info.viewportEndOffset) / 2
        val current = info.visibleItemsInfo.minByOrNull { abs((it.offset + it.size / 2) - center) }?.index
        if (current != safeIndex) listState.animateScrollToItem(safeIndex)
    }

    val sidePad = with(density) {
        (((viewportPx - itemWidth.toPx()) / 2f).coerceAtLeast(0f)).toDp()
    }

    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(OgtDimens.cardRadius))
            .background(OgtColors.surface)
            .border(1.dp, OgtColors.hairline, RoundedCornerShape(OgtDimens.cardRadius))
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            "Línea de tiempo",
            color = OgtColors.ink,
            fontWeight = FontWeight.SemiBold,
            fontSize = 13.sp,
        )
        OgtCaption("Hasta ${stops[safeIndex].label} · girá la rueda")
        Box(
            Modifier
                .fillMaxWidth()
                .height(78.dp)
                .onSizeChanged { viewportPx = it.width.toFloat() },
        ) {
            LazyRow(
                state = listState,
                flingBehavior = snap,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = sidePad),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                items(stops.size, key = { stops[it].dayStartEpochMs }) { index ->
                    val info = listState.layoutInfo
                    val center = (info.viewportStartOffset + info.viewportEndOffset) / 2f
                    val item = info.visibleItemsInfo.find { it.index == index }
                    val mid = item?.let { it.offset + it.size / 2f } ?: center
                    val half = (info.viewportEndOffset - info.viewportStartOffset).coerceAtLeast(1) / 2f
                    val t = ((mid - center) / half).coerceIn(-1f, 1f)
                    val depth = abs(t)
                    Column(
                        Modifier
                            .width(itemWidth)
                            .fillMaxHeight()
                            .graphicsLayer {
                                rotationY = t * 42f
                                scaleX = 1f - 0.16f * depth
                                scaleY = 1f - 0.28f * depth
                                alpha = 1f - 0.48f * depth
                                cameraDistance = 9.5f * density.density
                            },
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterVertically),
                    ) {
                        Canvas(Modifier.width(2.dp).height(28.dp)) {
                            drawLine(
                                color = if (index == safeIndex) OgtColors.secondary else OgtColors.ink.copy(alpha = 0.45f),
                                start = Offset(size.width / 2f, 0f),
                                end = Offset(size.width / 2f, size.height),
                                strokeWidth = if (index == safeIndex) 2.5.dp.toPx() else 1.5.dp.toPx(),
                                cap = StrokeCap.Round,
                            )
                        }
                        Text(
                            stops[index].label,
                            color = if (index == safeIndex) OgtColors.secondary else OgtColors.muted,
                            fontSize = if (index == safeIndex) 13.sp else 11.sp,
                            fontWeight = if (index == safeIndex) FontWeight.SemiBold else FontWeight.Medium,
                            textAlign = TextAlign.Center,
                            maxLines = 1,
                        )
                    }
                }
            }
            Canvas(Modifier.fillMaxSize()) {
                val cx = size.width / 2f
                val tipY = 18.dp.toPx()
                val topY = 6.dp.toPx()
                val half = 8.dp.toPx()
                drawLine(
                    color = OgtColors.secondary.copy(alpha = 0.18f),
                    start = Offset(cx, tipY),
                    end = Offset(cx, size.height - 8.dp.toPx()),
                    strokeWidth = 1.5.dp.toPx(),
                    cap = StrokeCap.Round,
                )
                val head = Path().apply {
                    moveTo(cx, tipY)
                    lineTo(cx - half, topY)
                    lineTo(cx + half, topY)
                    close()
                }
                drawPath(head, OgtColors.secondary)
            }
        }
    }
}
