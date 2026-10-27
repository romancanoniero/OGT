package com.onlygoodthings.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.onlygoodthings.app.platform.*
import com.onlygoodthings.shared.domain.StoryFrame
import kotlinx.coroutines.*

@Composable
fun OgtVideoEditAction(item:OgtPickedMedia,maxDurationMs:Long?=null,enabled:Boolean=true,frame:StoryFrame?=null,onBusyChange:(Boolean)->Unit={},onEdited:(OgtPickedMedia)->Unit) {
    val editor=rememberOgtVideoEditor()
    val scope=rememberCoroutineScope()
    val callback by rememberUpdatedState(onEdited)
    val busyCallback by rememberUpdatedState(onBusyChange)
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var info by remember(item.path) { mutableStateOf<PickedVideoInfo?>(null) }
    var infoError by remember(item.path) { mutableStateOf<String?>(null) }
    var preview by remember { mutableStateOf(false) }
    LaunchedEffect(busy) { busyCallback(busy) }
    DisposableEffect(Unit) { onDispose { busyCallback(false) } }
    LaunchedEffect(item.path) {
        try { info=inspectPickedVideo(item.path) }
        catch(_:TimeoutCancellationException) { infoError="No pudimos leer la duración. Volvé a editar el video." }
        catch(cancel:CancellationException) { throw cancel }
        catch(_:Exception) { infoError="No pudimos leer este video. Volvé a editarlo o elegí otro." }
    }
    fun perform(operation:suspend ()->OgtPickedMedia?) {
        if(!enabled || busy) return
        busy=true
        scope.launch {
            try { operation()?.let { callback(it) } }
            catch(_:TimeoutCancellationException) { error="El cambio tardó demasiado. Volvé a intentarlo; el original se conserva." }
            catch(cancel:CancellationException) { throw cancel }
            catch(failure:Exception) { error=failure.message ?: "No se pudo preparar el video" }
            finally { busy=false }
        }
    }
    Column(Modifier.fillMaxWidth(),verticalArrangement=Arrangement.spacedBy(6.dp)) {
        Text(info?.let { "Duración final: ${videoDurationLabel(it.durationMs)}" } ?: infoError ?: "Leyendo duración…")
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick={preview=true},enabled=enabled && !busy && info!=null,modifier=Modifier.weight(1f)) { Text("Vista previa") }
            OutlinedButton(onClick={perform {
                val edited=editor.edit(item.copy(path=item.unmutedPath ?: item.path,audioMuted=false,unmutedPath=null),maxDurationMs)
                if(edited!=null && item.audioMuted) changeVideoSound(edited,true) else edited
            }},enabled=enabled && !busy,modifier=Modifier.weight(1f)) { Text("Editar video") }
        }
        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
            Text(if(info?.hasAudio==false && !item.audioMuted) "Este video no tiene sonido" else "Quitar sonido",Modifier.weight(1f))
            Switch(checked=item.audioMuted,onCheckedChange={muted->perform { changeVideoSound(item,muted) }},
                modifier=Modifier.semantics { contentDescription="Quitar sonido del video" },
                enabled=enabled && !busy && item.preparedForUpload && (info?.hasAudio==true || item.audioMuted))
        }
        if(busy) Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(10.dp)) {
            OgtLoader(size=20.dp);Text("Preparando los cambios…")
        }
    }
    if(preview && info!=null) VideoPreview(item,info!!,frame,onClose={preview=false})
    if(error!=null) AlertDialog(onDismissRequest={error=null},title={Text("No se pudo preparar el video")},text={Text(error.orEmpty())},confirmButton={TextButton(onClick={error=null}) { Text("Aceptar") }})
}

@Composable
private fun VideoPreview(item:OgtPickedMedia,info:PickedVideoInfo,frame:StoryFrame?,onClose:()->Unit) {
    val lifecycle by LocalLifecycleOwner.current.lifecycle.currentStateFlow.collectAsState()
    val foreground=lifecycle.isAtLeast(Lifecycle.State.RESUMED)
    var playing by remember { mutableStateOf(true) }
    var position by remember { mutableStateOf(0L) }
    var epoch by remember { mutableStateOf(0) }
    var failure by remember { mutableStateOf<String?>(null) }
    val player: @Composable ()->Unit = {
        key(item.path,epoch) {
            OgtStoryVideo(if(item.path.startsWith("file:")) item.path else "file://${item.path}",playing && foreground,Modifier.fillMaxSize(),
                onReady={},onProgress={position=it;if(it>=info.durationMs) playing=false},onError={failure=it;playing=false},frame=frame)
        }
    }
    val heading: @Composable ()->Unit = {
        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
            Text("Vista previa",Modifier.weight(1f),color=Color.White)
            TextButton(onClick=onClose) { Text("Cerrar",color=Color.White) }
        }
    }
    val controls: @Composable ()->Unit = {
        Column(verticalArrangement=Arrangement.spacedBy(12.dp)) {
            failure?.let { Text(it,color=Color.White) }
            Text("${videoDurationLabel(position)} / ${videoDurationLabel(info.durationMs)} · ${if(item.audioMuted || !info.hasAudio) "Sin sonido" else "Con sonido"}",color=Color.White)
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                Button(onClick={
                    if(!playing && position>=info.durationMs-100) { epoch++;position=0 }
                    playing=!playing
                },modifier=Modifier.weight(1f)) { Text(if(playing) "Pausar" else "Reproducir") }
                Button(onClick={epoch++;position=0;failure=null;playing=true},modifier=Modifier.weight(1f)) { Text("Repetir") }
            }
        }
    }
    Dialog(onDismissRequest=onClose,properties=DialogProperties(usePlatformDefaultWidth=false)) {
        if(frame!=null) Box(Modifier.fillMaxSize().background(Color.Black).safeDrawingPadding()) {
            StoryViewport(Modifier.fillMaxSize()) { player() }
            Box(Modifier.align(Alignment.TopCenter).fillMaxWidth()
                .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha=.75f),Color.Transparent))).padding(16.dp)) { heading() }
            Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                .background(Brush.verticalGradient(listOf(Color.Transparent,Color.Black.copy(alpha=.8f)))).padding(16.dp)) { controls() }
        } else BoxWithConstraints(Modifier.fillMaxSize().background(Color.Black).safeDrawingPadding().padding(16.dp)) {
            val videoHeight=(maxHeight-160.dp).coerceAtLeast(120.dp)
            Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(12.dp)) {
                heading()
                Box(Modifier.height(videoHeight).fillMaxWidth(),contentAlignment=Alignment.Center) { player() }
                controls()
            }
        }
    }
}
