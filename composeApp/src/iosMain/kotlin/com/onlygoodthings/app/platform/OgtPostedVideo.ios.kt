package com.onlygoodthings.app.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.UIKitView
import androidx.compose.ui.viewinterop.UIKitInteropProperties
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.readValue
import platform.AVFoundation.*
import platform.AVFAudio.*
import platform.Foundation.NSURL
import platform.Foundation.NSNotificationCenter
import platform.Foundation.NSOperationQueue
import platform.QuartzCore.CATransaction
import platform.UIKit.UIView

@OptIn(ExperimentalForeignApi::class,androidx.compose.ui.ExperimentalComposeUiApi::class)
@Composable
actual fun OgtPostedVideo(url: String, modifier: Modifier, playing: Boolean, muted: Boolean, onEnded: () -> Unit) {
    val player = remember(url) {
        val remote = when {
            url.startsWith("file://") -> NSURL.fileURLWithPath(url.removePrefix("file://"))
            url.startsWith("/") -> NSURL.fileURLWithPath(url)
            else -> NSURL.URLWithString(url)
        }
        requireNotNull(remote) { "URL de video inválida" }
        AVPlayer(uRL = remote)
    }
    val ended = rememberUpdatedState(onEnded)
    LaunchedEffect(player,muted) { player.muted = muted }
    LaunchedEffect(player,playing) {
        if(playing) {
            AVAudioSession.sharedInstance().setCategory(AVAudioSessionCategoryPlayback,withOptions=AVAudioSessionCategoryOptionMixWithOthers,error=null)
            AVAudioSession.sharedInstance().setActive(true,error=null)
            player.play()
        } else player.pause()
    }
    DisposableEffect(player) {
        val observer=NSNotificationCenter.defaultCenter.addObserverForName(AVPlayerItemDidPlayToEndTimeNotification,player.currentItem,NSOperationQueue.mainQueue) { ended.value() }
        onDispose { player.pause();player.replaceCurrentItemWithPlayerItem(null);NSNotificationCenter.defaultCenter.removeObserver(observer) }
    }
    UIKitView(
        factory = { PostedVideoView(player) },
        modifier = modifier,
        properties=UIKitInteropProperties(interactionMode=null),
        update = { view -> view.videoLayer.player=player;view.setNeedsLayout() },
    )
}

@OptIn(ExperimentalForeignApi::class)
private class PostedVideoView(player:AVPlayer):UIView(frame=platform.CoreGraphics.CGRectZero.readValue()) {
    val videoLayer=AVPlayerLayer.playerLayerWithPlayer(player).apply { videoGravity=AVLayerVideoGravityResizeAspect }
    init { clipsToBounds=true;backgroundColor=platform.UIKit.UIColor.blackColor;layer.addSublayer(videoLayer) }
    override fun layoutSubviews() {
        super.layoutSubviews()
        CATransaction.begin();CATransaction.setDisableActions(true)
        videoLayer.frame=bounds
        CATransaction.commit()
    }
}
