package com.onlygoodthings.app.platform

import com.onlygoodthings.app.OgtApplication
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.widget.VideoView
import android.widget.FrameLayout
import android.content.Context
import android.view.View
import com.onlygoodthings.shared.domain.StoryFrame
import com.onlygoodthings.shared.domain.storyFrameLayout
import kotlin.math.roundToInt
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.onlygoodthings.shared.domain.MediaKind
import com.onlygoodthings.shared.domain.StoryRules
import kotlinx.coroutines.*

actual suspend fun prepareStoryMedia(item: OgtPickedMedia): OgtPickedMedia {
    if(item.kind!=MediaKind.VIDEO) return item
    val metadata=withContext(Dispatchers.IO) {
        val reader=MediaMetadataRetriever()
        try {
            reader.setDataSource(item.path.removePrefix("file://"))
            val duration=reader.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: error("Video inválido")
            require(duration in 1..StoryRules.MAX_VIDEO_MS) { "Recortá el video hasta 30 segundos" }
            duration to (reader.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 720)
        } finally { reader.release() }
    }
    return withContext(Dispatchers.Main) {
        exportAndroidVideo(OgtApplication.instance,item,VideoTrimRange(0,metadata.first),metadata.second)
    }
}

@Composable
actual fun OgtStoryVideo(url: String, playing: Boolean, modifier: Modifier, onReady: () -> Unit, onProgress: (Long) -> Unit, onError: (String) -> Unit, frame: StoryFrame?) {
    var displaySize by remember(url) { mutableStateOf<Pair<Int,Int>?>(null) }
    var view by remember(url) { mutableStateOf<VideoView?>(null) }
    val active by rememberUpdatedState(playing)
    val ready by rememberUpdatedState(onReady)
    val progress by rememberUpdatedState(onProgress)
    val failure by rememberUpdatedState(onError)
    LaunchedEffect(url,frame!=null) {
        if(frame!=null) try {
            displaySize=withContext(Dispatchers.IO) {
                val reader=MediaMetadataRetriever()
                try {
                    if(url.startsWith("http")) reader.setDataSource(url,emptyMap()) else reader.setDataSource(url.removePrefix("file://"))
                    val w=reader.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 0
                    val h=reader.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 0
                    val rotation=reader.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull() ?: 0
                    if(w>0 && h>0) (if(rotation%180!=0) h to w else w to h) else null
                } finally { reader.release() }
            }
        } catch(cancel:CancellationException) { throw cancel } catch(_:Exception) { /* Keep the player's dimensions on metadata failure. */ }
    }
    AndroidView(factory={ context -> FramedStoryVideo(context).apply {
        val container=this
        val video=video
        video.apply {
        view=this;setVideoURI(Uri.parse(url))
        setOnPreparedListener { player -> player.isLooping=false;if(!container.explicitDimensions) {container.mediaWidth=player.videoWidth;container.mediaHeight=player.videoHeight;};container.requestLayout();player.setOnVideoSizeChangedListener { _,w,h->if(!container.explicitDimensions) {container.mediaWidth=w;container.mediaHeight=h;container.requestLayout()} };ready();if(active) start() }
        setOnCompletionListener { progress(duration.toLong()) }
        setOnErrorListener { _,_,_->failure("No se pudo reproducir el video. Volvé a intentarlo.");true }
        }
    } },modifier=modifier,update={ it.frame=frame;displaySize?.let { size->it.explicitDimensions=true;it.mediaWidth=size.first;it.mediaHeight=size.second };it.requestLayout();if(playing) it.video.start() else it.video.pause() },onRelease={ it.video.stopPlayback();view=null })
    LaunchedEffect(view) { while(isActive) { view?.let { if(it.isPlaying) progress(it.currentPosition.toLong()) };delay(50) } }
}

private class FramedStoryVideo(context: Context): FrameLayout(context) {
    val video=object:VideoView(context) {
        override fun onMeasure(w: Int,h: Int) { setMeasuredDimension(View.MeasureSpec.getSize(w),View.MeasureSpec.getSize(h)) }
    }
    var frame: StoryFrame?=null
    var explicitDimensions=false
    var mediaWidth=1
    var mediaHeight=1
    init { clipChildren=true;setBackgroundColor(android.graphics.Color.BLACK);addView(video) }
    override fun onLayout(changed:Boolean,left:Int,top:Int,right:Int,bottom:Int) {
        if(width<=0 || height<=0 || mediaWidth<=0 || mediaHeight<=0) return
        val g=storyFrameLayout(width.toDouble(),height.toDouble(),mediaWidth.toDouble(),mediaHeight.toDouble(),frame)
        val w=g.width.roundToInt().coerceAtLeast(1);val h=g.height.roundToInt().coerceAtLeast(1)
        video.measure(View.MeasureSpec.makeMeasureSpec(w,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(h,View.MeasureSpec.EXACTLY))
        val x=g.left.roundToInt();val y=g.top.roundToInt();video.layout(x,y,x+w,y+h)
    }
}
