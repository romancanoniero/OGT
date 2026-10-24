package com.onlygoodthings.app.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.onlygoodthings.shared.domain.placePostFrame
import com.onlygoodthings.app.map.decodeTileBitmap
import com.onlygoodthings.app.media.OgtMediaCache
import com.onlygoodthings.app.media.isRemoteMediaUrl
import com.onlygoodthings.app.platform.localFilePath
import com.onlygoodthings.app.theme.OgtColors
import com.onlygoodthings.app.ui.components.OgtLoader
import com.onlygoodthings.app.ui.components.neighborAvatarArt
import com.onlygoodthings.shared.data.local.LocalPostMedia
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource

/**
 * Saca la foto del padding lateral del padre y la deja al ancho de la tarjeta.
 * El layout que reporta sigue siendo el del contenido, así el alto no se corre.
 */
fun Modifier.ogtBleedHorizontal(bleed: Dp): Modifier = layout { measurable, constraints ->
    val extra = bleed.roundToPx()
    val width = (constraints.maxWidth + extra * 2).coerceAtLeast(0)
    val placeable = measurable.measure(
        constraints.copy(minWidth = width, maxWidth = width),
    )
    layout(constraints.maxWidth.coerceAtLeast(0), placeable.height) {
        placeable.place(-extra, 0)
    }
}

/** Foto de seed, archivo local o URL remota. Cache + loader en el holder. */
@Composable
fun OgtPostImage(
    url: String,
    assetKey: String,
    fallback: DrawableResource,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
    posterUrl: String? = null,
    onAspect: ((Float) -> Unit)? = null,
    frameX: Float? = null,
    frameY: Float? = null,
    frameZoom: Float? = null,
) {
    val fetchUrl = url.takeIf { canFetch(it) } ?: posterUrl.orEmpty().takeIf { canFetch(it) }
    if (fetchUrl == null) {
        val painter = painterResource(if (assetKey.isBlank()) fallback else seedDrawable(assetKey))
        val intrinsic = painter.intrinsicSize
        LaunchedEffect(intrinsic) {
            val widthPx = intrinsic.width
            val heightPx = intrinsic.height
            if (widthPx.isFinite() && heightPx.isFinite() && heightPx > 0f) {
                onAspect?.invoke(widthPx / heightPx)
            }
        }
        Image(
            painter,
            contentDescription = contentDescription,
            modifier = modifier,
            alignment = Alignment.Center,
            contentScale = contentScale,
        )
        return
    }
    key(fetchUrl) {
        var bitmap by remember(fetchUrl) {
            mutableStateOf(OgtMediaCache.peek(fetchUrl)?.let(::decodeTileBitmap))
        }
        var loading by remember(fetchUrl) { mutableStateOf(bitmap == null) }
        LaunchedEffect(fetchUrl) {
            if (bitmap == null) loading = true
            val bytes = OgtMediaCache.load(fetchUrl)
                ?: posterUrl?.takeIf { it != fetchUrl && canFetch(it) }?.let { OgtMediaCache.load(it) }
            bitmap = bytes?.let(::decodeTileBitmap)
            loading = false
        }
        val ready = bitmap
        val aspect = ready?.let { frame ->
            val heightPx = frame.height
            if (heightPx > 0) frame.width.toFloat() / heightPx.toFloat() else null
        }
        LaunchedEffect(aspect) {
            if (aspect != null && aspect.isFinite() && aspect > 0f) onAspect?.invoke(aspect)
        }
        Box(modifier.clipToBounds().background(OgtColors.sand), contentAlignment = Alignment.Center) {
            if (ready != null) {
                val framed = contentScale == ContentScale.Crop && ready.width > 0 && ready.height > 0
                Image(
                    bitmap = ready,
                    contentDescription = contentDescription,
                    modifier = if (framed) {
                        Modifier.feedFrame(ready.width, ready.height, frameX, frameY, frameZoom)
                    } else {
                        Modifier.fillMaxSize()
                    },
                    alignment = Alignment.Center,
                    contentScale = if (framed) ContentScale.FillBounds else contentScale,
                )
            } else if (!loading) {
                val painter = painterResource(if (assetKey.isBlank()) fallback else seedDrawable(assetKey))
                val intrinsic = painter.intrinsicSize
                LaunchedEffect(intrinsic) {
                    val widthPx = intrinsic.width
                    val heightPx = intrinsic.height
                    if (widthPx.isFinite() && heightPx.isFinite() && heightPx > 0f) {
                        onAspect?.invoke(widthPx / heightPx)
                    }
                }
                Image(
                    painter,
                    contentDescription = contentDescription,
                    modifier = Modifier.fillMaxSize(),
                    alignment = Alignment.Center,
                    contentScale = contentScale,
                )
            }
            if (loading) OgtLoader(size = 28.dp)
        }
    }
}

@Composable
fun OgtPostImage(
    item: LocalPostMedia,
    fallback: DrawableResource,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
    onAspect: ((Float) -> Unit)? = null,
) {
    OgtPostImage(
        url = item.url,
        assetKey = item.assetKey.ifBlank { posterAssetKey(item) },
        fallback = fallback,
        contentDescription = contentDescription,
        modifier = modifier,
        contentScale = contentScale,
        posterUrl = item.posterUrl,
        onAspect = onAspect,
        frameX = item.frameX?.toFloat(),
        frameY = item.frameY?.toFloat(),
        frameZoom = item.frameZoom?.toFloat(),
    )
}

/** Misma cuenta que la web y Control: el punto queda al centro del marco. */
private fun Modifier.feedFrame(
    imageWidth: Int,
    imageHeight: Int,
    frameX: Float?,
    frameY: Float?,
    frameZoom: Float?,
): Modifier = layout { measurable, constraints ->
    val frameWidth = constraints.maxWidth.toFloat()
    val frameHeight = constraints.maxHeight.toFloat()
    val placed = placePostFrame(
        frameWidth = frameWidth,
        frameHeight = frameHeight,
        imageWidth = imageWidth.toFloat(),
        imageHeight = imageHeight.toFloat(),
        frameX = frameX ?: 0.5f,
        frameY = frameY ?: 0.5f,
        frameZoom = frameZoom ?: 1f,
    )
    if (placed == null) {
        val fallback = measurable.measure(constraints)
        layout(constraints.maxWidth.coerceAtLeast(0), constraints.maxHeight.coerceAtLeast(0)) {
            fallback.place(0, 0)
        }
    } else {
        val child = measurable.measure(
            Constraints.fixed(
                placed.displayWidth.toInt().coerceAtLeast(1),
                placed.displayHeight.toInt().coerceAtLeast(1),
            ),
        )
        layout(constraints.maxWidth.coerceAtLeast(0), constraints.maxHeight.coerceAtLeast(0)) {
            child.place(placed.left.toInt(), placed.top.toInt())
        }
    }
}

internal fun posterAssetKey(item: LocalPostMedia): String {
    val poster = item.posterUrl.orEmpty()
    return if (poster.startsWith("asset://")) poster.removePrefix("asset://") else item.assetKey
}

/** Foto viva del usuario, o el busto anónimo si todavía no tiene imagen. */
@Composable
fun OgtNeighborAvatar(
    userId: String,
    photoUrl: String?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
) {
    val fallback = neighborAvatarArt(userId)
    val url = photoUrl?.trim().orEmpty()
    if (!canFetch(url)) {
        Image(
            painterResource(fallback),
            contentDescription = contentDescription,
            modifier = modifier,
            contentScale = ContentScale.Crop,
        )
        return
    }
    OgtPostImage(
        url = url,
        assetKey = "",
        fallback = fallback,
        contentDescription = contentDescription,
        modifier = modifier,
        contentScale = ContentScale.Crop,
    )
}

private fun canFetch(url: String): Boolean =
    url.isNotBlank() && (isRemoteMediaUrl(url) || localFilePath(url) != null)
