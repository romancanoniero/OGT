package com.onlygoodthings.app.platform

import android.net.Uri
import android.widget.VideoView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView

@Composable
actual fun OgtPostedVideo(url: String, modifier: Modifier, playing: Boolean, muted: Boolean, onEnded: () -> Unit) {
    val playState=rememberUpdatedState(playing)
    val muteState=rememberUpdatedState(muted)
    val ended=rememberUpdatedState(onEnded)
    var mediaPlayer=remember(url) { arrayOfNulls<android.media.MediaPlayer>(1) }
    AndroidView(
        factory = { context ->
            VideoView(context).apply {
                tag = url
                setVideoURI(Uri.parse(url))
                setOnPreparedListener { player ->
                    mediaPlayer[0]=player
                    player.isLooping = false
                    player.setVolume(if(muteState.value)0f else 1f,if(muteState.value)0f else 1f)
                    if(playState.value)start()
                }
                setOnCompletionListener { ended.value() }
            }
        },
        modifier = modifier,
        update = { view ->
            mediaPlayer[0]?.setVolume(if(muted)0f else 1f,if(muted)0f else 1f)
            if(playing)view.start() else view.pause()
            if(view.tag!=url) {
                view.tag=url
                view.stopPlayback()
                view.setVideoURI(Uri.parse(url))
            }
        },
        onRelease = { it.stopPlayback() },
    )
}
