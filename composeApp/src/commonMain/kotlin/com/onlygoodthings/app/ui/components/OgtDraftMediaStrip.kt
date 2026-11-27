package com.onlygoodthings.app.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.FilterChip
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.onlygoodthings.app.platform.OgtPickedMedia
import com.onlygoodthings.app.platform.MediaDropSlot
import com.onlygoodthings.app.platform.mediaDropIndex
import com.onlygoodthings.app.platform.movePickedMedia
import com.onlygoodthings.app.platform.rememberOgtMediaPicker
import com.onlygoodthings.app.resources.Res
import com.onlygoodthings.app.resources.publish_action_add
import com.onlygoodthings.app.resources.publish_photo
import com.onlygoodthings.app.resources.qs_close
import com.onlygoodthings.app.resources.qs_plus
import com.onlygoodthings.app.theme.OgtColors
import com.onlygoodthings.app.theme.OgtMotion
import com.onlygoodthings.app.ui.screens.OgtPostImage
import com.onlygoodthings.app.ui.screens.ogtBleedHorizontal
import com.onlygoodthings.shared.domain.MediaKind
import com.onlygoodthings.shared.domain.PostMediaRules
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.isActive
import org.jetbrains.compose.resources.painterResource
import kotlin.math.roundToInt

private val HeroShape = RoundedCornerShape(16.dp)
private val ThumbShape = RoundedCornerShape(12.dp)

private enum class ThumbWait { UP, SCROLL }

/**
 * La zona grande es la portada. El + va primero en la tira.
 * Un toque corto elige la portada. Mantener pulsado permite ordenar la tira
 * o llevar una pieza al héroe; los bordes desplazan la tira durante el arrastre.
 */
@Composable
fun OgtDraftMediaStrip(
    items: MutableList<OgtPickedMedia>,
    missing: Boolean,
    heroDescription: String,
    onBusyChange: (Boolean) -> Unit = {},
    reorderEnabled: Boolean = true,
) {
    val remaining = (PostMediaRules.MAX_ITEMS - items.size).coerceAtLeast(0)
    val picker = rememberOgtMediaPicker(remaining) { picked ->
        if (items.size >= PostMediaRules.MAX_ITEMS) return@rememberOgtMediaPicker
        items += picked
    }
    var editingVideo by remember { mutableStateOf(false) }
    LaunchedEffect(picker.isProcessing.value, editingVideo) { onBusyChange(picker.isProcessing.value || editingVideo) }
    var sourceOpen by remember { mutableStateOf(false) }
    var heroBounds by remember { mutableStateOf(Rect.Zero) }
    var rootBounds by remember { mutableStateOf(Rect.Zero) }
    var draggingId by remember { mutableStateOf<String?>(null) }
    var pressedId by remember { mutableStateOf<String?>(null) }
    var dragWindow by remember { mutableStateOf(Offset.Zero) }
    var overHero by remember { mutableStateOf(false) }
    val density = LocalDensity.current
    val currentItems by rememberUpdatedState(items)
    val canReorder by rememberUpdatedState(reorderEnabled)
    val listState = rememberLazyListState()
    val thumbBounds = remember { mutableMapOf<String, Rect>() }
    var rowBounds by remember { mutableStateOf(Rect.Zero) }
    var dropIndex by remember { mutableStateOf<Int?>(null) }
    val cover = items.firstOrNull()
    var toolsVideoId by remember { mutableStateOf<String?>(null) }
    val videos = items.filter { it.kind == MediaKind.VIDEO && it.fromDevice }
    val toolsVideo = videos.firstOrNull { it.id == toolsVideoId }
        ?: cover?.takeIf { it.kind == MediaKind.VIDEO && it.fromDevice } ?: videos.firstOrNull()
    LaunchedEffect(cover?.id) { if (cover?.kind == MediaKind.VIDEO) toolsVideoId = cover.id }
    LaunchedEffect(items.size) {
        thumbBounds.keys.retainAll(items.map { it.id }.toSet())
        if (items.isNotEmpty()) sourceOpen = false
    }
    fun pickAndClose(action: () -> Unit) {
        sourceOpen = false
        action()
    }
    fun promoteToCover(index: Int) {
        currentItems.getOrNull(index)?.let { movePickedMedia(currentItems, it.id, 0) }
    }
    fun updateDropTarget() {
        val id = draggingId ?: return
        overHero = heroBounds.contains(dragWindow)
        val visible = listState.layoutInfo.visibleItemsInfo.map { it.key }.toSet()
        dropIndex = when {
            overHero -> 0
            dragWindow.y < rowBounds.top || dragWindow.y > rowBounds.bottom -> null
            else -> mediaDropIndex(currentItems.map { it.id }, id, dragWindow.x,
                thumbBounds.filterKeys { it in visible }.map { (key, rect) -> MediaDropSlot(key, rect.center.x) })
        }
    }
    LaunchedEffect(draggingId) {
        if (draggingId == null) return@LaunchedEffect
        val edge = with(density) { 28.dp.toPx() }
        val pixelsPerSecond = with(density) { 600.dp.toPx() }
        var previousFrame = withFrameNanos { it }
        while (isActive) {
            val frame = withFrameNanos { it }
            val seconds = ((frame - previousFrame) / 1_000_000_000f).coerceIn(0f, 0.05f)
            previousFrame = frame
            if (dragWindow.y in rowBounds.top..rowBounds.bottom && rowBounds.width > 0f) {
                val speed = when {
                    dragWindow.x < rowBounds.left + edge -> -((rowBounds.left + edge - dragWindow.x) / edge).coerceIn(0f, 1f)
                    dragWindow.x > rowBounds.right - edge -> ((dragWindow.x - rowBounds.right + edge) / edge).coerceIn(0f, 1f)
                    else -> 0f
                }
                if (speed != 0f) listState.scrollBy(speed * pixelsPerSecond * seconds)
            }
            updateDropTarget()
        }
    }
    val wellLine = when {
        missing -> OgtColors.error
        overHero -> OgtColors.secondary
        else -> OgtColors.hairline
    }
    val dragging = items.firstOrNull { it.id == draggingId }
    Box(Modifier.onGloballyPositioned { rootBounds = it.boundsInWindow() }.pointerInput(Unit) {
        awaitEachGesture {
            val down = awaitFirstDown()
            if (!canReorder) return@awaitEachGesture
            val point = rootBounds.topLeft + down.position
            if (!rowBounds.contains(point)) return@awaitEachGesture
            val visible = listState.layoutInfo.visibleItemsInfo.map { it.key }.toSet()
            val id = currentItems.firstOrNull { it.id in visible && thumbBounds[it.id]?.contains(point) == true }?.id
                ?: return@awaitEachGesture
            pressedId = id
            try {
                val wait = withTimeoutOrNull(OgtMotion.pressArmMs.toLong()) {
                    while (true) {
                        val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id }
                        if (change == null || !change.pressed) return@withTimeoutOrNull ThumbWait.UP
                        if (change.isConsumed || (change.position - down.position).getDistance() > viewConfiguration.touchSlop) return@withTimeoutOrNull ThumbWait.SCROLL
                    }
                    @Suppress("UNREACHABLE_CODE")
                    ThumbWait.UP
                }
                if (wait == ThumbWait.UP) {
                    promoteToCover(currentItems.indexOfFirst { it.id == id })
                } else if (wait != ThumbWait.SCROLL) {
                    draggingId = id
                    dragWindow = point
                    updateDropTarget()
                    val completed = drag(down.id) { change ->
                        dragWindow += change.positionChange()
                        updateDropTarget()
                        if (change.positionChange() != Offset.Zero) change.consume()
                    }
                    if (completed && canReorder) dropIndex?.let { movePickedMedia(currentItems, id, it) }
                }
            } finally {
                draggingId = null
                pressedId = null
                dropIndex = null
                overHero = false
            }
        }
    }) {
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(OgtColors.sand)
                .animateContentSize(OgtMotion.size)
                .padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (picker.isProcessing.value) OgtMediaImportProgress()
            Box(
                Modifier
                    .ogtBleedHorizontal(10.dp)
                    .height(176.dp)
                    .onGloballyPositioned { heroBounds = it.boundsInWindow() }
                    .clip(HeroShape)
                    .background(if (missing) OgtColors.errorContainer else OgtColors.stone)
                    .border(1.dp, wellLine, HeroShape),
            ) {
                if (cover != null) {
                    DraftStill(cover, heroDescription, Modifier.fillMaxSize())
                } else {
                    Image(
                        painter = painterResource(Res.drawable.publish_action_add),
                        contentDescription = null,
                        modifier = Modifier.size(104.dp).align(Alignment.Center),
                        contentScale = ContentScale.Fit,
                    )
                }
                Box(
                    Modifier.align(Alignment.TopStart).padding(8.dp)
                        .clip(CircleShape).background(OgtColors.canvas.copy(alpha = 0.92f))
                        .padding(horizontal = 10.dp, vertical = 5.dp),
                ) {
                    Text(
                        "${items.size} de ${PostMediaRules.MAX_ITEMS}",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        color = OgtColors.ink,
                    )
                }
            }
            LazyRow(
                state = listState,
                modifier = Modifier.onGloballyPositioned { rowBounds = it.boundsInWindow() },
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(end = 4.dp),
                userScrollEnabled = draggingId == null,
            ) {
                if (remaining > 0) {
                    item(key = "add") {
                        SourceToggle(open = sourceOpen, onToggle = { sourceOpen = !sourceOpen })
                    }
                }
                itemsIndexed(items, key = { _, item -> item.id }) { index, item ->
                    val draggingThis = item.id == draggingId
                    val pressing = item.id == pressedId || draggingThis
                    val scale by animateFloatAsState(
                        targetValue = if (pressing && !draggingThis) OgtMotion.pressScale else 1f,
                        animationSpec = OgtMotion.press,
                        label = "thumb-press",
                    )
                    Box(
                        Modifier
                            .size(72.dp)
                            .scale(scale)
                            .graphicsLayer { alpha = if (draggingThis) 0.35f else 1f }
                            .onGloballyPositioned { thumbBounds[item.id] = it.boundsInWindow() }
                            .clip(ThumbShape)
                            .border(1.dp, OgtColors.hairline, ThumbShape)
                            .semantics(mergeDescendants = true) {
                                contentDescription = "${if (item.kind == MediaKind.VIDEO) "Video" else "Foto"} ${index + 1} de ${items.size}"
                                role = Role.Button
                                onClick("Usar como portada") { if (reorderEnabled) promoteToCover(index); reorderEnabled }
                                customActions = if (!reorderEnabled) emptyList() else buildList {
                                    if (index > 0) add(CustomAccessibilityAction("Mover a la posición anterior") { movePickedMedia(items, item.id, index - 1) })
                                    if (index < items.lastIndex) add(CustomAccessibilityAction("Mover a la posición siguiente") { movePickedMedia(items, item.id, index + 1) })
                                }
                            },
                    ) {
                        DraftStill(item, null, Modifier.fillMaxSize())
                        val from = items.indexOfFirst { it.id == draggingId }
                        val destination = dropIndex
                        if (destination != null && destination != from && index == destination) {
                            Box(Modifier.align(if (destination < from) Alignment.CenterStart else Alignment.CenterEnd)
                                .fillMaxHeight().width(3.dp).background(OgtColors.secondary))
                        }
                        Box(Modifier.align(Alignment.BottomEnd).padding(4.dp).clip(CircleShape)
                            .background(OgtColors.ink.copy(alpha = 0.72f)).padding(horizontal = 5.dp, vertical = 1.dp)
                            .clearAndSetSemantics { }) {
                            Text("${index + 1}", color = OgtColors.canvas, fontSize = 10.sp)
                        }
                        if (item.kind == MediaKind.VIDEO) {
                            Box(
                                Modifier.align(Alignment.BottomStart).padding(4.dp)
                                    .clip(CircleShape).background(OgtColors.ink.copy(alpha = 0.72f))
                                    .padding(horizontal = 6.dp, vertical = 2.dp),
                            ) {
                                Text("Video", color = OgtColors.canvas, fontSize = 9.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }
                        Box(
                            Modifier.align(Alignment.TopEnd).padding(3.dp).size(22.dp).clip(CircleShape)
                                .background(OgtColors.ink.copy(alpha = 0.72f))
                                .clickable { items.removeAll { it.id == item.id } },
                            contentAlignment = Alignment.Center,
                        ) {
                            OgtStitchIcon(Res.drawable.qs_close, "Quitar", size = 12.dp, tint = OgtColors.canvas)
                        }
                    }
                }
            }
            if(videos.size > 1) LazyRow(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                itemsIndexed(videos,key={_,video->video.id}) { index,video ->
                    FilterChip(selected=video.id==toolsVideo?.id,onClick={toolsVideoId=video.id},enabled=!editingVideo,
                        label={Text("Video ${index+1}")})
                }
            }
            if(toolsVideo!=null) OgtVideoEditAction(toolsVideo, onBusyChange = { editingVideo = it }) { edited ->
                val index=items.indexOfFirst { it.id==edited.id }
                if(index>=0) items[index]=edited
            }
            AnimatedVisibility(
                visible = sourceOpen,
                enter = OgtMotion.enterUp,
                exit = OgtMotion.exitDown,
            ) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(OgtColors.canvas)
                        .padding(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    MediaAction("Galería", Modifier.weight(1f)) { pickAndClose(picker.pickLibrary) }
                    MediaAction("Video", Modifier.weight(1f)) { pickAndClose(picker.pickVideo) }
                    MediaAction("Cámara", Modifier.weight(1f)) { pickAndClose(picker.takeCamera) }
                }
            }
        }
        if (dragging != null) {
            val local = dragWindow - rootBounds.topLeft
            val half = with(density) { 36.dp.toPx() }
            Box(
                Modifier
                    .zIndex(4f)
                    .offset { IntOffset((local.x - half).roundToInt(), (local.y - half).roundToInt()) }
                    .size(72.dp)
                    .scale(OgtMotion.pressScale)
                    .clip(ThumbShape)
                    .border(1.dp, OgtColors.secondary, ThumbShape),
            ) {
                DraftStill(dragging, "Arrastrando", Modifier.fillMaxSize())
            }
        }
    }
}

@Composable
private fun DraftStill(item: OgtPickedMedia, description: String?, modifier: Modifier) {
    val remote = !item.fromDevice && (item.path.startsWith("https://") || item.path.startsWith("http://"))
    val source = if (remote) item.path else if (item.fromDevice) "file://${item.path}" else "asset://${item.path}"
    val poster = item.posterPath?.let { if (remote) it else "file://$it" }
    OgtPostImage(
        url = com.onlygoodthings.app.media.postPreviewUrl(item.kind, source, poster),
        assetKey = if (remote || item.fromDevice || item.kind == MediaKind.VIDEO) "" else item.path,
        fallback = Res.drawable.publish_photo,
        contentDescription = description,
        modifier = modifier,
        contentScale = ContentScale.Crop,
        posterUrl = poster,
    )
}

@Composable
private fun SourceToggle(open: Boolean, onToggle: () -> Unit) {
    val press = remember { MutableInteractionSource() }
    val pressed by press.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) OgtMotion.pressScale else 1f,
        animationSpec = OgtMotion.press,
        label = "source-toggle-press",
    )
    val turn by animateFloatAsState(
        targetValue = if (open) 45f else 0f,
        animationSpec = OgtMotion.layout,
        label = "source-toggle-turn",
    )
    val fill by animateColorAsState(
        targetValue = if (open) OgtColors.secondary.copy(alpha = 0.14f) else OgtColors.stone,
        animationSpec = OgtMotion.color,
        label = "source-toggle-fill",
    )
    val line by animateColorAsState(
        targetValue = if (open) OgtColors.secondary else OgtColors.hairline,
        animationSpec = OgtMotion.color,
        label = "source-toggle-line",
    )
    val ink by animateColorAsState(
        targetValue = if (open) OgtColors.secondary else OgtColors.ink,
        animationSpec = OgtMotion.color,
        label = "source-toggle-ink",
    )
    Box(
        Modifier
            .size(72.dp)
            .scale(scale)
            .clip(ThumbShape)
            .background(fill)
            .border(1.dp, line, ThumbShape)
            .clickable(interactionSource = press, indication = null, onClick = onToggle),
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.graphicsLayer { rotationZ = turn }) {
            OgtStitchIcon(
                Res.drawable.qs_plus,
                if (open) "Cerrar" else "Agregar",
                tint = ink,
            )
        }
    }
}

@Composable
private fun MediaAction(label: String, modifier: Modifier, onClick: () -> Unit) {
    val press = remember { MutableInteractionSource() }
    val pressed by press.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) OgtMotion.pressScale else 1f,
        animationSpec = OgtMotion.press,
        label = "source-action-press",
    )
    Box(
        modifier
            .height(44.dp)
            .scale(scale)
            .clip(CircleShape)
            .background(OgtColors.sand)
            .border(1.dp, OgtColors.hairline, CircleShape)
            .clickable(interactionSource = press, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = OgtColors.ink)
    }
}
