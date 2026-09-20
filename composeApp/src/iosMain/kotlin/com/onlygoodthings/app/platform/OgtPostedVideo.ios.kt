package com.onlygoodthings.app.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.UIKitView
import kotlinx.cinterop.ExperimentalForeignApi
import platform.AVFoundation.AVPlayer
import platform.AVFoundation.AVPlayerLayer
import platform.AVFoundation.pause
import platform.AVFoundation.play
import platform.Foundation.NSURL
import platform.UIKit.UIView

@OptIn(ExperimentalForeignApi::class)
@Composable
actual fun OgtPostedVideo(url: String, modifier: Modifier) {
    val player = remember(url) {
        val remote = when {
            url.startsWith("file://") -> NSURL.fileURLWithPath(url.removePrefix("file://"))
            url.startsWith("/") -> NSURL.fileURLWithPath(url)
            else -> NSURL.URLWithString(url)
        }
        requireNotNull(remote) { "URL de video inválida" }
        AVPlayer(uRL = remote)
    }
    DisposableEffect(player) {
        player.play()
        onDispose { player.pause() }
    }
    UIKitView(
        factory = {
            val view = UIView()
            val layer = AVPlayerLayer.playerLayerWithPlayer(player)
            view.layer.addSublayer(layer)
            view
        },
        modifier = modifier,
        update = { view ->
            val layer = view.layer.sublayers?.firstOrNull() as? AVPlayerLayer
            layer?.frame = view.bounds
        },
    )
}
