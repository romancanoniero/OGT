package com.onlygoodthings.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.onlygoodthings.app.platform.OgtPostedVideo
import com.onlygoodthings.app.resources.Res
import com.onlygoodthings.app.resources.feed_story_donacion
import com.onlygoodthings.app.resources.qs_close
import com.onlygoodthings.app.theme.OgtColors
import com.onlygoodthings.app.ui.components.CircleIconButton
import com.onlygoodthings.app.ui.components.MediaPagerIndicator
import com.onlygoodthings.shared.data.local.LocalPostMedia
import com.onlygoodthings.shared.domain.MediaKind

/** Visor a pantalla completa de la media posteada en la ficha. */
@Composable
fun PostMediaViewer(
    media: List<LocalPostMedia>,
    startIndex: Int,
    tag: String,
    onClose: () -> Unit,
) {
    if (media.isEmpty()) return
    val pager = rememberPagerState(initialPage = startIndex.coerceIn(0, media.lastIndex)) { media.size }
    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(Modifier.fillMaxSize().background(OgtColors.ink)) {
            HorizontalPager(state = pager, modifier = Modifier.fillMaxSize(), key = { media[it].id }) { page ->
                val item = media[page]
                val playable = item.kind == MediaKind.VIDEO && (
                    item.url.startsWith("http") || item.url.startsWith("file") || item.url.startsWith("/")
                )
                if (playable) {
                    OgtPostedVideo(item.url, Modifier.fillMaxSize())
                } else {
                    OgtPostImage(
                        item = item,
                        fallback = Res.drawable.feed_story_donacion,
                        contentDescription = item.altText ?: tag,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Fit,
                    )
                }
            }
            MediaPagerIndicator(pageIndex = pager.currentPage, pageCount = media.size)
            CircleIconButton(
                art = Res.drawable.qs_close,
                label = "Cerrar",
                onClick = onClose,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .padding(start = 12.dp, top = 8.dp),
            )
        }
    }
}
