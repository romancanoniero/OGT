@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class, androidx.compose.ui.ExperimentalComposeUiApi::class)
package com.onlygoodthings.app.platform

import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.UIKitView
import androidx.compose.ui.viewinterop.UIKitInteropProperties
import kotlinx.cinterop.readValue
import kotlinx.cinterop.useContents
import platform.CoreGraphics.CGRectMake
import com.onlygoodthings.shared.domain.StoryFrame
import com.onlygoodthings.shared.domain.storyFrameLayout
import com.onlygoodthings.shared.domain.MediaKind
import com.onlygoodthings.shared.domain.StoryRules
import kotlinx.coroutines.*
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import platform.AVFoundation.*
import platform.CoreMedia.CMTimeGetSeconds
import platform.Foundation.*
import platform.UIKit.UIView
import platform.QuartzCore.CATransaction

actual suspend fun prepareStoryMedia(item: OgtPickedMedia): OgtPickedMedia = withContext(Dispatchers.Default) {
    if(item.kind!=MediaKind.VIDEO) return@withContext item
    val asset=AVURLAsset.URLAssetWithURL(NSURL.fileURLWithPath(item.path.removePrefix("file://")),null)
    val seconds=CMTimeGetSeconds(asset.duration)
    require(seconds.isFinite() && seconds>0 && seconds*1000<=StoryRules.MAX_VIDEO_MS) { "El video debe durar hasta 30 segundos" }
    val output="${NSTemporaryDirectory()}story-${NSUUID().UUIDString}.mp4"
    NSFileManager.defaultManager.removeItemAtPath(output,null)
    val exporter=AVAssetExportSession(asset,AVAssetExportPreset640x480) ?: error("No se pudo preparar este video")
    exporter.outputURL=NSURL.fileURLWithPath(output)
    exporter.outputFileType=AVFileTypeMPEG4
    exporter.shouldOptimizeForNetworkUse=true
    suspendCancellableCoroutine<Unit> { continuation ->
        continuation.invokeOnCancellation { exporter.cancelExport() }
        exporter.exportAsynchronouslyWithCompletionHandler {
            if(continuation.isActive) {
                if(exporter.status==AVAssetExportSessionStatusCompleted) continuation.resume(Unit)
                else continuation.resumeWithException(IllegalArgumentException("No se pudo preparar el video. Probá con otro archivo."))
            }
        }
    }
    item.copy(path=output)
}

@Composable
actual fun OgtStoryVideo(url: String, playing: Boolean, modifier: Modifier, onReady: () -> Unit, onProgress: (Long) -> Unit, onError: (String) -> Unit, frame: StoryFrame?) {
    val ready by rememberUpdatedState(onReady)
    val progress by rememberUpdatedState(onProgress)
    val failed by rememberUpdatedState(onError)
    var dimensions by remember(url) { mutableStateOf(1.0 to 1.0) }
    val player=remember(url) { AVPlayer(uRL=requireNotNull(NSURL.URLWithString(url))) }
    DisposableEffect(player) { onDispose { player.pause(); player.replaceCurrentItemWithPlayerItem(null) } }
    LaunchedEffect(player,playing) { if(playing) player.play() else player.pause() }
    LaunchedEffect(player) {
        var notified=false
        while(isActive) {
            val item=player.currentItem
            if(item?.status==AVPlayerItemStatusFailed) { failed("No se pudo reproducir el video. Volvé a intentarlo.");break }
            if(item?.status==AVPlayerItemStatusReadyToPlay) {
                item.presentationSize.useContents { if(width>0 && height>0 && dimensions!=(width to height)) dimensions=width to height }
                if(!notified) { notified=true;ready() }
                val seconds=CMTimeGetSeconds(player.currentTime())
                if(seconds.isFinite()) progress((seconds*1000).toLong())
            }
            delay(50)
        }
    }
    UIKitView(factory={ StoryVideoView(player) },modifier=modifier,properties=UIKitInteropProperties(interactionMode=null),update={ it.framing=frame;it.dimensions=dimensions;it.setNeedsLayout() })
}

private class StoryVideoView(player: AVPlayer): UIView(frame=platform.CoreGraphics.CGRectZero.readValue()) {
    private val videoLayer=AVPlayerLayer.playerLayerWithPlayer(player).apply { videoGravity=AVLayerVideoGravityResizeAspect }
    var framing: StoryFrame? = null
    var dimensions=1.0 to 1.0
    init { clipsToBounds=true;backgroundColor=platform.UIKit.UIColor.blackColor;layer.addSublayer(videoLayer) }
    override fun layoutSubviews() {
        super.layoutSubviews()
        bounds.useContents {
            if(size.width>0 && size.height>0) {
                val g=storyFrameLayout(size.width,size.height,dimensions.first,dimensions.second,framing)
                CATransaction.begin();CATransaction.setDisableActions(true)
                videoLayer.frame=CGRectMake(g.left,g.top,g.width,g.height)
                CATransaction.commit()
            }
        }
    }
}
