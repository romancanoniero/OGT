package com.onlygoodthings.app.ui.components

import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.*
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.onlygoodthings.app.map.decodeTileBitmap
import com.onlygoodthings.app.platform.*
import com.onlygoodthings.shared.domain.*
import com.onlygoodthings.shared.data.createPlatformHttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsBytes
import io.ktor.http.isSuccess
import kotlinx.coroutines.*
import kotlin.math.*

/** A canonical portrait stage preserves the selected crop across web and phones. */
@Composable
fun StoryViewport(modifier:Modifier=Modifier,content:@Composable BoxScope.()->Unit) {
    BoxWithConstraints(modifier.background(Color.Black),contentAlignment=Alignment.Center) {
        val width=minOf(maxWidth,maxHeight*9f/16f)
        Box(Modifier.width(width).aspectRatio(9f/16f).clipToBounds(),content=content)
    }
}

/** Story media remains private in composition memory, including editor previews. */
@Composable
fun StoryFrameImage(url:String,frame:StoryFrame?,modifier:Modifier=Modifier,onReady:()->Unit={},onError:(String)->Unit={},onDimensions:(Int,Int)->Unit={_,_->}) {
    var bitmap by remember(url) { mutableStateOf<ImageBitmap?>(null) }
    val ready by rememberUpdatedState(onReady);val failure by rememberUpdatedState(onError);val dimensions by rememberUpdatedState(onDimensions)
    LaunchedEffect(url) {
        val client=createPlatformHttpClient()
        try {
            val bytes=if(url.startsWith("file://") || url.startsWith("/")) withContext(Dispatchers.Default) { readLocalFileBytes(url.removePrefix("file://")) ?: error("El archivo ya no está disponible") }
            else { val response=client.get(url);check(response.status.isSuccess()) { "Esta historia ya no está disponible" };response.bodyAsBytes() }
            bitmap=withContext(Dispatchers.Default) { decodeStoryBitmap(bytes) } ?: error("No se pudo leer la foto")
            bitmap?.let { dimensions(it.width,it.height) };ready()
        } catch(cancel:CancellationException) { throw cancel }
        catch(error:Exception) { failure(error.message ?: "No se pudo cargar la foto") }
        finally { client.close() }
    }
    Canvas(modifier.clipToBounds().semantics { contentDescription="Imagen de la historia" }) {
        bitmap?.let {
            if(size.width>0 && size.height>0) {
                val g=storyFrameLayout(size.width.toDouble(),size.height.toDouble(),it.width.toDouble(),it.height.toDouble(),frame)
                drawImage(it,dstOffset=IntOffset(g.left.roundToInt(),g.top.roundToInt()),dstSize=IntSize(g.width.roundToInt().coerceAtLeast(1),g.height.roundToInt().coerceAtLeast(1)),filterQuality=FilterQuality.High)
            }
        }
    }
}

/** Inline canvas: gesture frames stay local; only a completed gesture writes the draft. */
@Composable
fun StoryInteractivePreview(draft:StoryDraft,modifier:Modifier=Modifier,enabled:Boolean=true,onGestureActive:(Boolean)->Unit={},onFrameChange:(StoryFrame)->Unit) {
    var frame by remember(draft.id) { mutableStateOf(draft.frame ?: StoryFrame(StoryFrameMode.FIT)) }
    var active by remember(draft.id) { mutableStateOf(false) }
    var viewport by remember { mutableStateOf(IntSize.Zero) }
    val preview=if(draft.kind==MediaKind.IMAGE) draft.path else draft.posterPath
    var media by remember(preview) { mutableStateOf(IntSize.Zero) }
    var error by remember(preview) { mutableStateOf<String?>(null) }
    val current by rememberUpdatedState(frame)
    val save by rememberUpdatedState(onFrameChange)
    val busy by rememberUpdatedState(onGestureActive)
    var before by remember(draft.id) { mutableStateOf(frame) }
    LaunchedEffect(draft.frame,active) { if(!active) frame=draft.frame ?: StoryFrame(StoryFrameMode.FIT) }
    DisposableEffect(draft.id) { onDispose { busy(false) } }
    Box(modifier.aspectRatio(9f/16f).clipToBounds().background(Color.Black)) {
        if(preview!=null) StoryFrameImage(preview,frame,Modifier.fillMaxSize(),onError={error=it},onDimensions={w,h->media=IntSize(w,h)})
        error?.let { Text(it,color=Color.White,modifier=Modifier.align(Alignment.Center).padding(16.dp)) }
        Box(Modifier.fillMaxSize().onSizeChanged { viewport=it }.storyFrameGestures(
            key=Triple(draft.id,viewport,media),enabled=enabled && media.width>0,
            onActive={value->if(value) before=frame;active=value;busy(value)},
            onTransform={centroid,pan,zoom->frame=transformStoryFrame(frame,viewport,media,centroid,pan,zoom)},
            onEnd={if(frame!=before) save(frame)}))
    }
}

@Composable
fun StoryFrameEditor(draft:StoryDraft,onClose:()->Unit,onSave:(StoryFrame)->Unit) {
    var frame by remember(draft.id) { mutableStateOf(draft.frame ?: StoryFrame(StoryFrameMode.FIT)) }
    var viewport by remember { mutableStateOf(IntSize.Zero) }
    var media by remember { mutableStateOf(IntSize.Zero) }
    var playing by remember { mutableStateOf(false) }
    var duration by remember(draft.path) { mutableStateOf(StoryRules.MAX_VIDEO_MS) }
    LaunchedEffect(draft.path) { if(draft.kind==MediaKind.VIDEO) runCatching { inspectPickedVideo(draft.path).durationMs }.getOrNull()?.let { duration=it } }
    var error by remember { mutableStateOf<String?>(null) }
    val scroll=rememberScrollState()
    val scope=rememberCoroutineScope()
    val lifecycle by androidx.lifecycle.compose.LocalLifecycleOwner.current.lifecycle.currentStateFlow.collectAsState()
    val foreground=lifecycle.isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED)
    val preview=if(draft.kind==MediaKind.IMAGE) draft.path else draft.posterPath
    fun move(x:Double,y:Double) { frame=frame.copy(x=(frame.x+x).coerceIn(-1.0,1.0),y=(frame.y+y).coerceIn(-1.0,1.0)) }
    Dialog(onDismissRequest=onClose,properties=DialogProperties(usePlatformDefaultWidth=false)) {
        BoxWithConstraints(Modifier.fillMaxSize().background(Color.Black).safeDrawingPadding().padding(12.dp)) {
            Column(Modifier.fillMaxWidth().verticalScroll(scroll),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                    Text("Encuadre",Modifier.weight(1f),color=Color.White)
                    TextButton(onClick={scope.launch { scroll.animateScrollTo(viewport.height) }}) { Text("Controles",color=Color.White) }
                    TextButton(onClick=onClose) { Text("Cancelar",color=Color.White) }
                }
                Box(Modifier.fillMaxWidth().aspectRatio(9f/16f).clipToBounds().background(Color.Black)) {
                    if(playing && draft.kind==MediaKind.VIDEO) OgtStoryVideo("file://${draft.path.removePrefix("file://")}",foreground,Modifier.fillMaxSize(),{}, { if(it>=duration-40) playing=false },{error=it;playing=false},frame)
                    else if(preview!=null) StoryFrameImage(preview,frame,Modifier.fillMaxSize(),onError={error=it},onDimensions={w,h->media=IntSize(w,h)})
                    Box(Modifier.fillMaxSize().onSizeChanged { viewport=it }.storyFrameGestures(
                        key=Triple(draft.id,viewport,media),enabled=media.width>0,onActive={},
                        onTransform={centroid,pan,zoom->frame=transformStoryFrame(frame,viewport,media,centroid,pan,zoom)},onEnd={}))
                }
                Text("Separá los dedos para ampliar y arrastrá para encuadrar. El original se conserva.",color=Color.White)
                Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected=frame.mode==StoryFrameMode.FILL,onClick={frame=StoryFrame()},label={Text("Llenar pantalla")},colors=FilterChipDefaults.filterChipColors(containerColor=Color.DarkGray,labelColor=Color.White,selectedContainerColor=Color(0xFF0F3E4A),selectedLabelColor=Color.White))
                    FilterChip(selected=frame.mode==StoryFrameMode.FIT,onClick={frame=StoryFrame(StoryFrameMode.FIT)},label={Text("Mostrar completa")},colors=FilterChipDefaults.filterChipColors(containerColor=Color.DarkGray,labelColor=Color.White,selectedContainerColor=Color(0xFF0F3E4A),selectedLabelColor=Color.White))
                }
                Text("Zoom ${((frame.zoom*10).roundToInt()/10.0).toString().replace('.',',')}×",color=Color.White)
                Slider(value=frame.zoom.toFloat(),onValueChange={frame=frame.copy(zoom=it.toDouble())},valueRange=1f..6f,modifier=Modifier.semantics { contentDescription="Zoom del encuadre" })
                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceEvenly) {
                    listOf(Triple("←",-0.15,0.0),Triple("→",0.15,0.0),Triple("↑",0.0,-0.15),Triple("↓",0.0,0.15)).forEach { (label,x,y)->
                        TextButton(onClick={move(x,y)},modifier=Modifier.semantics { contentDescription=when(label) { "←"->"Mover imagen a la izquierda";"→"->"Mover imagen a la derecha";"↑"->"Mover imagen hacia arriba";else->"Mover imagen hacia abajo" } }) { Text(label,color=Color.White) }
                    }
                }
                if(draft.kind==MediaKind.VIDEO) TextButton(onClick={playing=!playing}) { Text(if(playing) "Pausar video" else "Reproducir video",color=Color.White) }
                error?.let { Text(it,color=Color.White) }
                Button(onClick={onSave(frame)},enabled=media.width>0,modifier=Modifier.fillMaxWidth()) { Text("Usar encuadre") }
            }
        }
    }
}
