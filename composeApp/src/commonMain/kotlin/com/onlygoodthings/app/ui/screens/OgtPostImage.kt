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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import com.onlygoodthings.app.map.decodeTileBitmap
import com.onlygoodthings.app.media.OgtMediaCache
import com.onlygoodthings.app.media.isRemoteMediaUrl
import com.onlygoodthings.app.platform.localFilePath
import com.onlygoodthings.app.theme.OgtColors
import com.onlygoodthings.app.ui.components.OgtLoader
import com.onlygoodthings.shared.data.local.LocalPostMedia
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource

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
) {
    val fetchUrl = url.takeIf { canFetch(it) } ?: posterUrl.orEmpty().takeIf { canFetch(it) }
    if (fetchUrl == null) {
        Image(
            painterResource(if (assetKey.isBlank()) fallback else seedDrawable(assetKey)),
            contentDescription = contentDescription,
            modifier = modifier,
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
        Box(modifier.background(OgtColors.sand), contentAlignment = Alignment.Center) {
            val ready = bitmap
            if (ready != null) {
                Image(
                    bitmap = ready,
                    contentDescription = contentDescription,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = contentScale,
                )
            } else if (!loading) {
                Image(
                    painterResource(if (assetKey.isBlank()) fallback else seedDrawable(assetKey)),
                    contentDescription = contentDescription,
                    modifier = Modifier.fillMaxSize(),
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
) {
    OgtPostImage(
        url = item.url,
        assetKey = item.assetKey.ifBlank { posterAssetKey(item) },
        fallback = fallback,
        contentDescription = contentDescription,
        modifier = modifier,
        contentScale = contentScale,
        posterUrl = item.posterUrl,
    )
}

internal fun posterAssetKey(item: LocalPostMedia): String {
    val poster = item.posterUrl.orEmpty()
    return if (poster.startsWith("asset://")) poster.removePrefix("asset://") else item.assetKey
}

private fun canFetch(url: String): Boolean =
    url.isNotBlank() && (isRemoteMediaUrl(url) || localFilePath(url) != null)
