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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.onlygoodthings.app.media.OgtMediaCache
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
    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(Modifier.fillMaxSize().background(OgtColors.ink).windowInsetsPadding(WindowInsets.statusBars)) {
            com.onlygoodthings.app.ui.components.PostedMediaCarousel(media,Res.drawable.feed_story_donacion,tag,
                modifier=Modifier.fillMaxSize(),initialIndex=startIndex,autoPlay=true)
            CircleIconButton(
                art = Res.drawable.qs_close,
                label = "Cerrar",
                onClick = onClose,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(end = 12.dp, top = 8.dp),
            )
        }
    }
}
