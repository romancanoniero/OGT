package com.onlygoodthings.app.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import com.onlygoodthings.app.map.decodeTileBitmap
import com.onlygoodthings.app.media.OgtMediaCache
import com.onlygoodthings.app.media.isRemoteMediaUrl
import com.onlygoodthings.app.platform.localFilePath
import com.onlygoodthings.app.resources.Res
import com.onlygoodthings.app.resources.media_viewholder
import com.onlygoodthings.app.theme.OgtColors
import com.onlygoodthings.app.ui.components.OgtLoader
import com.onlygoodthings.shared.data.local.LocalPostMedia
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource

/** Foto de seed, archivo local o URL remota. En iOS el hueco es el indicador anterior; en Android, el marco 3D. */
@Composable
fun OgtPostImage(
    url: String,
    assetKey: String,
    fallback: DrawableResource?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
    posterUrl: String? = null,
) {
    val local = localPostArt(assetKey, fallback)
    val fetchUrl = url.takeIf { canFetch(it) } ?: posterUrl.orEmpty().takeIf { canFetch(it) }
    if (fetchUrl == null) {
        PostArt(local, contentDescription, modifier, contentScale)
        return
    }
    key(fetchUrl) {
        val legacy = legacyMediaViewHolder()
        var bitmap by remember(fetchUrl) {
            mutableStateOf(OgtMediaCache.peek(fetchUrl)?.let(::decodeTileBitmap))
        }
        var loading by remember(fetchUrl) { mutableStateOf(legacy && bitmap == null) }
        LaunchedEffect(fetchUrl) {
            if (bitmap != null) {
                loading = false
                return@LaunchedEffect
            }
            if (legacy) loading = true
            try {
                val bytes = OgtMediaCache.load(fetchUrl)
                    ?: posterUrl?.takeIf { it != fetchUrl && canFetch(it) }?.let { OgtMediaCache.load(it) }
                val decoded = bytes?.let(::decodeTileBitmap)
                if (decoded != null) bitmap = decoded
            } catch (cancel: CancellationException) {
                throw cancel
            } catch (_: Exception) {
                // Bytes que no son imagen: queda el hueco.
            } finally {
                loading = false
            }
        }
        Box(modifier.background(OgtColors.sand), contentAlignment = Alignment.Center) {
            val ready = bitmap
            // Una foto remota no se reemplaza por el dibujo empaquetado: o está en caché, o queda el hueco.
            val remoteStill = isRemoteMediaUrl(fetchUrl) && !isPlayableVideoUrl(fetchUrl)
            when {
                ready != null -> RemoteArt(ready, contentDescription, contentScale)
                legacy && loading -> OgtLoader(size = 28.dp)
                remoteStill -> if (legacy) {
                    Box(Modifier.fillMaxSize())
                } else {
                    MediaViewHolder(contentDescription, Modifier.fillMaxSize())
                }
                local != null -> PostArt(local, contentDescription, Modifier.fillMaxSize(), contentScale)
                legacy -> Box(Modifier.fillMaxSize())
                else -> MediaViewHolder(contentDescription, Modifier.fillMaxSize())
            }
        }
    }
}

@Composable
fun OgtPostImage(
    item: LocalPostMedia,
    fallback: DrawableResource?,
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

private fun isPlayableVideoUrl(url: String): Boolean {
    val path = url.substringBefore('?').lowercase()
    return path.endsWith(".mp4") || path.endsWith(".m4v") || path.endsWith(".mov") || path.endsWith(".webm")
}

/** Foto empaquetada. Si la clave no existe, no se inventa otra imagen. */
private fun localPostArt(assetKey: String, fallback: DrawableResource?): DrawableResource? {
    if (assetKey.isNotBlank()) seedDrawable(assetKey)?.let { return it }
    return if (assetKey.isBlank()) fallback else null
}

@Composable
private fun PostArt(
    art: DrawableResource?,
    contentDescription: String?,
    modifier: Modifier,
    contentScale: ContentScale,
) {
    if (art == null) {
        MediaViewHolder(contentDescription, modifier)
        return
    }
    Image(
        painterResource(art),
        contentDescription = contentDescription,
        modifier = modifier,
        contentScale = contentScale,
    )
}

@Composable
private fun MediaViewHolder(contentDescription: String?, modifier: Modifier = Modifier) {
    Box(modifier.background(OgtColors.sand), contentAlignment = Alignment.Center) {
        Image(
            painterResource(Res.drawable.media_viewholder),
            contentDescription = contentDescription,
            modifier = Modifier.fillMaxHeight(0.78f).padding(vertical = 8.dp),
            contentScale = ContentScale.Fit,
        )
    }
}

@Composable
private fun RemoteArt(
    bitmap: ImageBitmap,
    contentDescription: String?,
    contentScale: ContentScale,
) {
    Image(
        bitmap = bitmap,
        contentDescription = contentDescription,
        modifier = Modifier.fillMaxSize(),
        contentScale = contentScale,
    )
}
