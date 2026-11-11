package com.onlygoodthings.app.platform

import android.net.Uri
import android.widget.VideoView
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView

@Composable
actual fun OgtPostedVideo(url: String, modifier: Modifier) {
    AndroidView(
        factory = { context ->
            VideoView(context).apply {
                tag = url
                setVideoURI(Uri.parse(url))
                setOnPreparedListener { player ->
                    player.isLooping = true
                    start()
                }
            }
        },
        modifier = modifier,
        update = { view ->
            if(view.tag!=url) {
                view.tag=url
                view.stopPlayback()
                view.setVideoURI(Uri.parse(url))
            }
        },
        onRelease = { it.stopPlayback() },
    )
}
