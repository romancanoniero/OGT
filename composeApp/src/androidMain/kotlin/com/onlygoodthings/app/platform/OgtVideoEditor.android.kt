@file:OptIn(androidx.media3.common.util.UnstableApi::class)
package com.onlygoodthings.app.platform

import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.widget.VideoView
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.effect.Presentation
import androidx.media3.transformer.*
import androidx.media3.transformer.Composition
import com.onlygoodthings.app.theme.OgtColors
import kotlinx.coroutines.*
import java.io.File
import java.util.UUID
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

private class TrimRequest(val item:OgtPickedMedia,val limit:Long?,val done:CancellableContinuation<OgtPickedMedia?>)
private fun OgtPickedMedia.originalPath():String = sourcePath?.takeIf { File(it).exists() } ?: path.removePrefix("file://")

@Composable
actual fun rememberOgtVideoEditor():OgtVideoEditor {
    var request by remember { mutableStateOf<TrimRequest?>(null) }
    val scope=rememberCoroutineScope()
    val editor=remember { OgtVideoEditor { item,limit ->
        withContext(Dispatchers.Main) {
            check(request==null) { "Terminá de editar el video anterior" }
            suspendCancellableCoroutine { done ->
                val next=TrimRequest(item,limit,done)
                request=next
                done.invokeOnCancellation { scope.launch { if(request===next) request=null } }
            }
        }
    } }
    request?.let { pending ->
        VideoTrimDialog(pending) { result ->
            request=null
            if(pending.done.isActive) pending.done.resume(result)
        }
    }
    return editor
}

private data class ClipInfo(val durationMs:Long,val displayWidth:Int,val displayHeight:Int,val frames:List<Bitmap>)

@Composable
private fun VideoTrimDialog(request:TrimRequest,onDone:(OgtPickedMedia?)->Unit) {
    val context=LocalContext.current
    val scope=rememberCoroutineScope()
    val lifecycle=androidx.lifecycle.compose.LocalLifecycleOwner.current.lifecycle
    var info by remember(request) { mutableStateOf<ClipInfo?>(null) }
    var range by remember(request) { mutableStateOf(VideoTrimRange(0,0)) }
    var player by remember { mutableStateOf<VideoView?>(null) }
    var busy by remember { mutableStateOf(false) }
    var failure by remember { mutableStateOf<String?>(null) }
    var exporting by remember { mutableStateOf<Job?>(null) }
    var exportGeneration by remember { mutableStateOf(0) }
    var preparationStage by remember { mutableStateOf("Leyendo información del video…") }
    var preparationFraction by remember { mutableStateOf<Float?>(null) }
    var preparationStarted by remember { mutableStateOf(0L) }
    var elapsedMs by remember { mutableStateOf(0L) }
    val selected by rememberUpdatedState(range)
    fun cancel() { exportGeneration++;exporting?.cancel();onDone(null) }
    LaunchedEffect(busy) {
        if(!busy) return@LaunchedEffect
        val monitor=VideoPreparationWatchdog(preparationStarted)
        while(isActive && busy) {
            val now=android.os.SystemClock.elapsedRealtime()
            monitor.setPaused(!lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED),now)
            monitor.observe(preparationStage,preparationFraction?.let { (it*1000).toInt() } ?: -1,now)
            elapsedMs=monitor.elapsedMs(now)
            val message=monitor.timeoutMessage(now)
            if(message!=null) {
                exportGeneration++;exporting?.cancel();busy=false;failure=message
                break
            }
            delay(1000)
        }
    }
    LaunchedEffect(request) {
        try {
            val metadata=withContext(Dispatchers.IO) {
                val retriever=MediaMetadataRetriever()
                val frames=mutableListOf<Bitmap>()
                try {
                    retriever.setDataSource(request.item.originalPath())
                    val duration=retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: error("Video inválido")
                    require(duration>0)
                    val width=retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 1280
                    val height=retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 720
                    val rotation=retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull() ?: 0
                    repeat(8) { index ->
                        currentCoroutineContext().ensureActive()
                        val original=retriever.getFrameAtTime(index*duration*1000/8,MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                        if(original!=null) {
                            val scale=144f/maxOf(original.width,original.height)
                            val scaled=Bitmap.createScaledBitmap(original,(original.width*scale).toInt().coerceAtLeast(1),(original.height*scale).toInt().coerceAtLeast(1),true)
                            if(scaled!==original) original.recycle()
                            frames+=scaled
                        }
                    }
                    ClipInfo(duration,if(rotation%180==0) width else height,if(rotation%180==0) height else width,frames)
                } catch(error:Exception) { frames.forEach(Bitmap::recycle);throw error }
                finally { retriever.release() }
            }
            info=metadata
            range=VideoTrimRange(0,minOf(metadata.durationMs,request.limit ?: metadata.durationMs))
        } catch(cancel:CancellationException) { throw cancel }
        catch(_:Exception) { failure="No se pudo abrir este video. Elegí otro archivo." }
    }
    DisposableEffect(info) { val frames=info?.frames.orEmpty();onDispose { frames.forEach { it.recycle() } } }
    LaunchedEffect(player) {
        while(isActive) {
            player?.let { if(it.isPlaying && (it.currentPosition>=selected.endMs || it.currentPosition<selected.startMs)) it.seekTo(selected.startMs.toInt()) }
            delay(40)
        }
    }
    Dialog(onDismissRequest=::cancel,properties=DialogProperties(usePlatformDefaultWidth=false)) {
        Column(Modifier.fillMaxSize().background(OgtColors.canvas).safeDrawingPadding().verticalScroll(rememberScrollState()).padding(20.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
                TextButton(onClick=::cancel) { Text("Cancelar") }
                Text("Recortar video",style=MaterialTheme.typography.titleLarge)
            }
            info?.let { clip ->
                AndroidView(factory={ ctx -> VideoView(ctx).apply {
                    player=this
                    setVideoURI(Uri.fromFile(File(request.item.originalPath())))
                    setOnPreparedListener { seekTo(range.startMs.toInt()) }
                    setOnErrorListener { _,_,_->failure="No se pudo reproducir la vista previa.";true }
                } },modifier=Modifier.fillMaxWidth().height(320.dp).background(Color.Black),onRelease={it.stopPlayback();player=null})
                Text("${clock(range.startMs)} – ${clock(range.endMs)} · ${clock(range.durationMs)}"+(request.limit?.let { " / ${clock(it)}" } ?: ""))
                Box {
                    Row(Modifier.fillMaxWidth().height(56.dp)) { clip.frames.forEach { Image(it.asImageBitmap(),null,Modifier.weight(1f).fillMaxHeight(),contentScale=ContentScale.Crop) } }
                    RangeSlider(value=range.startMs.toFloat()..range.endMs.toFloat(),onValueChange={ next ->
                        range=constrainVideoTrim(range,next.start.toLong(),next.endInclusive.toLong(),clip.durationMs,request.limit)
                        player?.pause();player?.seekTo(range.startMs.toInt())
                    },valueRange=0f..clip.durationMs.toFloat(),enabled=!busy,modifier=Modifier.fillMaxWidth().height(56.dp),colors=SliderDefaults.colors(thumbColor=OgtColors.secondary,activeTrackColor=OgtColors.secondary))
                }
                TextButton(onClick={player?.let { if(it.isPlaying) it.pause() else { it.seekTo(range.startMs.toInt());it.start() } }},enabled=!busy) { Text("Reproducir recorte") }
                Text(request.limit?.let { "Elegí hasta ${it/1000} segundos. El original se conserva." } ?: "Elegí el segmento que querés compartir. El original se conserva.")
                Button(onClick={
                    player?.pause();busy=true;failure=null
                    preparationStage="Leyendo información del video…";preparationFraction=null
                    preparationStarted=android.os.SystemClock.elapsedRealtime();elapsedMs=0
                    val ticket=++exportGeneration
                    exporting=scope.launch {
                        try {
                            val output=exportAndroidVideo(context,request.item,range,minOf(clip.displayHeight,if(clip.displayHeight>clip.displayWidth) 1280 else 720), maxDurationMs=request.limit,isForeground={lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED)},onProgress={stage,fraction->
                                if(ticket==exportGeneration) { preparationStage=stage;preparationFraction=fraction }
                            })
                            if(ticket==exportGeneration) onDone(output)
                        } catch(cancel:CancellationException) { throw cancel }
                        catch(error:Exception) { if(ticket==exportGeneration) failure=if(error is VideoPreparationTimeoutException) error.message else "No se pudo preparar el recorte dentro de 8 MB. Probá con un segmento más corto." }
                        finally { if(ticket==exportGeneration) busy=false }
                    }
                },enabled=!busy && range.valid(clip.durationMs,request.limit),modifier=Modifier.fillMaxWidth()) { Text(if(busy) "Preparando video…" else "Usar recorte") }
            } ?: if(failure==null) CircularProgressIndicator() else Unit
            if(busy) Column(Modifier.fillMaxWidth(),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                Text(preparationStage)
                preparationFraction?.let { fraction ->
                    LinearProgressIndicator(progress={fraction},modifier=Modifier.fillMaxWidth())
                    Text("${(fraction*100).toInt()} % · ${clock(elapsedMs)} transcurridos")
                } ?: run {
                    LinearProgressIndicator(modifier=Modifier.fillMaxWidth())
                    Text("Tiempo transcurrido: ${clock(elapsedMs)}")
                }
                Text("El video original se conserva.",style=MaterialTheme.typography.bodySmall)
            }
            failure?.let { Text(it,color=OgtColors.error) }
        }
    }
}

private fun clock(ms:Long):String = "${ms/60000}:${((ms/1000)%60).toString().padStart(2,'0')}"

internal suspend fun exportAndroidVideo(context:android.content.Context,item:OgtPickedMedia,range:VideoTrimRange,sourceHeight:Int,maxDurationMs:Long?=null,attempt:Int=0,startedAtMs:Long=android.os.SystemClock.elapsedRealtime(),isForeground:()->Boolean={true},onProgress:(String,Float?)->Unit={_,_->},removeAudio:Boolean=false):OgtPickedMedia {
    val monitoringScope=CoroutineScope(currentCoroutineContext())
    val watchdog=VideoPreparationWatchdog(startedAtMs)
    fun progress(stage:String,fraction:Float?) {
        watchdog.observe(stage,fraction?.let { (it*1000).toInt() } ?: -1,android.os.SystemClock.elapsedRealtime())
        onProgress(stage,fraction)
    }
    progress("Leyendo información del video…",null)
    require(maxDurationMs==null || range.durationMs<=maxDurationMs)
    val encoding=withContext(Dispatchers.IO) {
        val reader=MediaMetadataRetriever()
        val extractor=MediaExtractor()
        try {
            reader.setDataSource(item.originalPath());extractor.setDataSource(item.originalPath())
            val sourceDuration=reader.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: error("Video inválido")
            val hadAudio=reader.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_AUDIO)=="yes"
            val totalRate=reader.extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITRATE)?.toLongOrNull() ?: Long.MAX_VALUE
            var compatible=true
            for(index in 0 until extractor.trackCount) {
                val mime=extractor.getTrackFormat(index).getString(MediaFormat.KEY_MIME).orEmpty()
                if(mime.startsWith("video/") && mime!=MimeTypes.VIDEO_H264 || mime.startsWith("audio/") && mime!=MimeTypes.AUDIO_AAC) compatible=false
            }
            val sourceVideoRate=(totalRate-(if(hadAudio) 96_000 else 0)).coerceAtLeast(100_000)
            val requested=minOf(VideoUploadRules.videoBitrate(range.durationMs).toLong(),sourceVideoRate*9/10).toInt()
            val unchanged=compatible && range.startMs==0L && range.endMs==sourceDuration && totalRate<=2_000_000 && File(item.originalPath()).length()<=VideoUploadRules.MAX_BYTES && (reader.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: Int.MAX_VALUE)<=sourceHeight
            Triple(requested,unchanged,sourceDuration)
        } finally { reader.release();extractor.release() }
    }
    require(range.valid(encoding.third,maxDurationMs))
    val output=File(context.cacheDir,"video-${UUID.randomUUID()}.mp4")
    var poster:File?=null
    try {
        val preserveVideo=removeAudio && item.preparedForUpload && attempt==0
        val reuse=!removeAudio && encoding.second && withContext(Dispatchers.IO) {
            val header=ByteArray(12)
            File(item.originalPath()).inputStream().use { it.read(header)==12 } &&
                header.copyOfRange(4,8).decodeToString()=="ftyp" && header.copyOfRange(8,12).decodeToString()!="qt  "
        }
        if(reuse) {
            // Already compressed H.264/AAC MP4: avoid recompression and muxer padding.
            progress("Conservando el video ya optimizado…",null)
            withContext(Dispatchers.IO) { File(item.originalPath()).copyTo(output,overwrite=true) }
        } else {
        val media=MediaItem.Builder().setUri(Uri.fromFile(File(item.originalPath())))
            .setClippingConfiguration(MediaItem.ClippingConfiguration.Builder().setStartPositionMs(range.startMs).setEndPositionMs(range.endMs).build()).build()
        val targetHeight=when(attempt) { 0->sourceHeight;1->sourceHeight*2/3;else->sourceHeight/2 }.coerceAtLeast(2)
        val effects=if(encoding.second || preserveVideo) emptyList() else listOf(Presentation.createForHeight(targetHeight))
        val edited=EditedMediaItem.Builder(media).setRemoveAudio(removeAudio).setEffects(Effects(emptyList(),effects)).build()
        val stage=if(attempt==0) "Comprimiendo video" else "Optimizando tamaño · intento ${attempt+1} de 3"
        progress(stage,0f)
        suspendCancellableCoroutine<Unit> { done ->
            var monitoring:Job?=null
            val factory=DefaultEncoderFactory.Builder(context)
                .setRequestedVideoEncoderSettings(VideoEncoderSettings.Builder().setBitrate((encoding.first * when(attempt) { 0->1.0;1->0.65;else->0.4 }).toInt()).build())
                .setRequestedAudioEncoderSettings(AudioEncoderSettings.Builder().setBitrate(96_000).build()).build()
            val transformer=Transformer.Builder(context).setMuxerFactory(ExactDurationMuxerFactory(range.durationMs*1000)).setVideoMimeType(MimeTypes.VIDEO_H264).setAudioMimeType(MimeTypes.AUDIO_AAC)
                .setEncoderFactory(object:Codec.EncoderFactory by factory { override fun videoNeedsEncoding()=!(encoding.second || preserveVideo); override fun audioNeedsEncoding()=!encoding.second }).addListener(object:Transformer.Listener {
                    override fun onCompleted(composition:Composition,result:ExportResult) { monitoring?.cancel();if(done.isActive) done.resume(Unit) }
                    override fun onError(composition:Composition,result:ExportResult,error:ExportException) { monitoring?.cancel();if(done.isActive) done.resumeWithException(error) }
                }).build()
            done.invokeOnCancellation { Handler(Looper.getMainLooper()).post { monitoring?.cancel();transformer.cancel();output.delete() } }
            transformer.start(edited,output.absolutePath)
            if(done.isActive) monitoring=monitoringScope.launch {
                val holder=ProgressHolder()
                while(isActive && done.isActive) {
                    val available=transformer.getProgress(holder)==Transformer.PROGRESS_STATE_AVAILABLE
                    progress(stage,if(available) holder.progress.coerceIn(0,100)/100f else null)
                    watchdog.setPaused(!isForeground(),android.os.SystemClock.elapsedRealtime())
                    val message=watchdog.timeoutMessage(android.os.SystemClock.elapsedRealtime())
                    if(message!=null) {
                        if(done.isActive) done.resumeWithException(VideoPreparationTimeoutException(message))
                        transformer.cancel()
                        break
                    }
                    delay(250)
                }
            }
        }
        }
        if(output.length()>VideoUploadRules.MAX_BYTES && attempt<2) {
            output.delete()
            return exportAndroidVideo(context,item,range,sourceHeight,maxDurationMs,attempt+1,startedAtMs,isForeground,onProgress,removeAudio)
        }
        progress("Comprobando audio, duración y vista previa…",null)
        return withContext(Dispatchers.IO) {
            require(output.length() in 1..VideoUploadRules.MAX_BYTES)
            val metadata=MediaMetadataRetriever()
            val original=MediaMetadataRetriever()
            try {
                original.setDataSource(item.originalPath())
                val hadAudio=original.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_AUDIO)=="yes"
                metadata.setDataSource(output.absolutePath)
                val hasAudio=metadata.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_AUDIO)=="yes"
                require(if(removeAudio) !hasAudio else !hadAudio || hasAudio) { "No se pudo preparar el audio" }
                val duration=metadata.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: error("Video inválido")
                require(maxDurationMs==null || duration<=maxDurationMs) { "El recorte supera la duración máxima" }
                require(kotlin.math.abs(duration-range.durationMs)<=100) { "El recorte no se exportó completo" }
                val bitmap=metadata.getFrameAtTime(0) ?: error("No se pudo crear la portada")
                poster=File(context.cacheDir,"video-${UUID.randomUUID()}.jpg")
                try { poster!!.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG,82,it) } } finally { bitmap.recycle() }
                item.copy(path=output.absolutePath,posterPath=poster!!.absolutePath,preparedForUpload=true,sourcePath=item.originalPath())
            } finally { metadata.release();original.release() }
        }
    } catch(error:Exception) { output.delete();poster?.delete();throw error }
}

/** AAC encoders add padding. Bound both tracks at the selected endpoint so a
 * 30-second story never becomes 30.04 seconds and fails server validation.
 * This changes the container end marker, without another encoding pass. */
private class ExactDurationMuxerFactory(private val durationUs:Long,private val factory:androidx.media3.muxer.Muxer.Factory=DefaultMuxer.Factory()):androidx.media3.muxer.Muxer.Factory by factory {
    override fun create(path:String):androidx.media3.muxer.Muxer {
        val muxer=factory.create(path)
        val tracks=mutableSetOf<Int>()
        return object:androidx.media3.muxer.Muxer by muxer {
            override fun addTrack(format:androidx.media3.common.Format):Int = muxer.addTrack(format).also { tracks+=it }
            override fun writeSampleData(trackId:Int,bytes:java.nio.ByteBuffer,info:androidx.media3.muxer.BufferInfo) {
                if(info.size>0 && info.presentationTimeUs<durationUs) {
                    muxer.writeSampleData(trackId,bytes,androidx.media3.muxer.BufferInfo(info.presentationTimeUs,info.size,info.flags and androidx.media3.common.C.BUFFER_FLAG_END_OF_STREAM.inv()))
                }
            }
            override fun close() {
                tracks.forEach { muxer.writeSampleData(it,java.nio.ByteBuffer.allocateDirect(0),androidx.media3.muxer.BufferInfo(durationUs,0,androidx.media3.common.C.BUFFER_FLAG_END_OF_STREAM)) }
                muxer.close()
            }
        }
    }
}

private class VideoPreparationTimeoutException(message:String):IllegalStateException(message)
