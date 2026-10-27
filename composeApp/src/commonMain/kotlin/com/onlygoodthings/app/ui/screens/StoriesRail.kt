package com.onlygoodthings.app.ui.screens

import androidx.compose.foundation.*
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.TransformOrigin
import com.onlygoodthings.app.data.storyBookProgress
import com.onlygoodthings.app.data.storyPageDuration
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.onlygoodthings.app.ui.components.StoryViewport
import com.onlygoodthings.app.ui.components.StoryFrameImage
import com.onlygoodthings.app.ui.components.StoryFrameEditor
import com.onlygoodthings.app.ui.components.StoryInteractivePreview
import com.onlygoodthings.app.data.LocalAuth
import com.onlygoodthings.app.data.LocalOgtSession
import com.onlygoodthings.app.data.StoriesController
import com.onlygoodthings.app.map.decodeTileBitmap
import com.onlygoodthings.app.platform.*
import com.onlygoodthings.app.resources.Res
import com.onlygoodthings.app.resources.feed_logo
import com.onlygoodthings.app.theme.OgtColors
import com.onlygoodthings.app.ui.components.OgtLoader
import com.onlygoodthings.shared.data.createPlatformHttpClient
import com.onlygoodthings.shared.domain.*
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsBytes
import io.ktor.http.isSuccess
import kotlinx.coroutines.*
import org.jetbrains.compose.resources.painterResource

@Composable
fun StoriesRail() {
    val controller=LocalAuth.current.stories
    val me=LocalOgtSession.current.me()
    val lifecycle by LocalLifecycleOwner.current.lifecycle.currentStateFlow.collectAsState()
    val foreground=lifecycle.isAtLeast(Lifecycle.State.RESUMED)
    LaunchedEffect(foreground) {
        if(foreground) controller.refresh()
        else try { controller.flushDraft() }
        catch(cancel:CancellationException) { throw cancel }
        catch(_:Exception) { /* The store exposes the save failure to the editor. */ }
    }
    val scope=rememberCoroutineScope()
    var editor by remember(me.id) { mutableStateOf(false) }
    var source by remember(me.id) { mutableStateOf(false) }
    var pick by remember { mutableStateOf<String?>(null) }
    var viewing by remember(me.id) { mutableStateOf<String?>(null) }
    val picker=rememberOgtMediaPicker(1,StoryRules.MAX_VIDEO_MS) { selected -> scope.launch { controller.choose(selected);editor=true } }
    LaunchedEffect(pick,source) {
        val action=pick ?: return@LaunchedEffect
        if(source) return@LaunchedEffect
        withFrameNanos { }
        pick=null
        when(action) { "camera"->picker.takeCamera();"record"->picker.recordVideo();"video"->picker.pickVideo();else->picker.pickPhoto() }
    }
    val rows=controller.readable
    val grouped=rows.groupBy { it.authorId }
    fun add() {
        if(picker.isProcessing.value) return
        // A new story and resuming the saved draft are separate actions.
        if(controller.publishing) editor=true else source=true
    }
    Column {
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal=16.dp,vertical=8.dp),horizontalArrangement=Arrangement.spacedBy(12.dp)) {
            val own=grouped[me.id].orEmpty().sortedBy { it.createdAtEpochMs }
            Column(Modifier.width(68.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(6.dp)) {
                Box(Modifier.size(64.dp)) {
                    OgtSessionAvatar(contentDescription="Tu historia",modifier=Modifier.fillMaxSize().clip(CircleShape)
                        .border(2.dp,if(own.any { !it.seen }) OgtColors.ink else OgtColors.hairline,CircleShape)
                        .clickable { if(own.isEmpty()) add() else viewing=own.first().id })
                    StoryAddButton(Modifier.align(Alignment.BottomEnd).offset(2.dp,2.dp), ::add)
                }
                Text(if(controller.publishing) "Publicando…" else "Tu historia",fontSize=11.sp,maxLines=1,overflow=TextOverflow.Ellipsis)
            }
            grouped.filterKeys { it!=me.id }.entries.sortedByDescending { (_,items)->items.any { !it.seen } }.forEach { (_,items)->
                val first=items.minBy { it.createdAtEpochMs }
                Column(Modifier.width(68.dp).clickable { viewing=items.sortedBy { it.createdAtEpochMs }.firstOrNull { !it.seen }?.id ?: first.id },horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(6.dp)) {
                    OgtNeighborAvatar(first.authorId,first.authorPhotoUrl,first.authorName,Modifier.size(64.dp).clip(CircleShape)
                        .border(2.dp,if(items.any { !it.seen }) OgtColors.ink else OgtColors.hairline,CircleShape))
                    Text(first.authorName,fontSize=11.sp,maxLines=1,overflow=TextOverflow.Ellipsis)
                }
            }
        }
        if(controller.loading && rows.isEmpty()) Box(Modifier.fillMaxWidth().height(28.dp),contentAlignment=Alignment.Center) { OgtLoader(size=20.dp) }
        if(picker.isProcessing.value) com.onlygoodthings.app.ui.components.OgtMediaImportProgress(Modifier.padding(horizontal=16.dp))
        picker.errorMessage.value?.let { message -> Text(message,color=OgtColors.error,fontSize=12.sp) }
        controller.error?.let { message -> TextButton(onClick={ controller.refresh() }) { Text("$message · Reintentar",color=OgtColors.muted,fontSize=12.sp) } }
        if(controller.draft!=null && !editor) TextButton(onClick={ editor=true }) { Text(if(controller.publishing) "Publicando historia…" else "Continuar borrador") }
    }
    if(source) AlertDialog(onDismissRequest={source=false},title={Text("Agregar historia")},text={
        Column {
            Text("Se muestra durante 24 horas. Fotos o videos de hasta 30 segundos y 8 MB.")
            if(controller.draft!=null) {
                Text("Al elegir un archivo nuevo, se reemplazará el borrador pendiente. Si cancelás, lo conservamos.")
                TextButton(onClick={source=false;editor=true}) { Text("Continuar borrador") }
            }
            TextButton(onClick={source=false;pick="camera"}) { Text("Abrir cámara") }
            TextButton(onClick={source=false;pick="record"}) { Text("Grabar video") }
            TextButton(onClick={source=false;pick="photo"}) { Text("Elegir foto") }
            TextButton(onClick={source=false;pick="video"}) { Text("Elegir video") }
        }
    },confirmButton={TextButton(onClick={source=false}) { Text("Cancelar") }})
    if(editor) StoryEditor(controller,preparing=picker.isProcessing.value,onClose={editor=false},onReplace={editor=false;source=true})
    LaunchedEffect(controller.publishedId) { if(controller.publishedId!=null) editor=false }
    val selected=rows.firstOrNull { it.id==viewing }
    if(viewing!=null && selected==null) LaunchedEffect(viewing,rows) { viewing=null }
    if(selected!=null) StoryPlayer(controller,selected,foreground,me.id,onClose={viewing=null},onNavigate={viewing=it})
}

/** Geometric centering avoids the font baseline; the halo radiates equally on every side. */
@Composable
private fun StoryAddButton(modifier:Modifier=Modifier,onClick:()->Unit) {
    Canvas(modifier.size(22.dp).semantics { contentDescription="Agregar historia" }
        .clickable(role=Role.Button,onClick=onClick)) {
        val radius=17.dp.toPx()
        drawCircle(Brush.radialGradient(
            0f to OgtColors.ink.copy(alpha=.24f),
            .64f to OgtColors.ink.copy(alpha=.18f),
            .82f to OgtColors.ink.copy(alpha=.06f),
            1f to Color.Transparent,
            center=center,radius=radius,
        ),radius=radius,center=center)
        drawCircle(Color.White,radius=size.minDimension/2f,center=center)
        val arm=5.dp.toPx()
        val stroke=2.2.dp.toPx()
        drawLine(OgtColors.ink,Offset(center.x-arm,center.y),Offset(center.x+arm,center.y),strokeWidth=stroke,cap=StrokeCap.Round)
        drawLine(OgtColors.ink,Offset(center.x,center.y-arm),Offset(center.x,center.y+arm),strokeWidth=stroke,cap=StrokeCap.Round)
    }
}

@Composable
private fun StoryEditor(controller: StoriesController,preparing:Boolean=false,onClose:()->Unit,onReplace:()->Unit) {
    val draft=controller.draft
    if(draft==null) { LaunchedEffect(Unit) { onClose() };return }
    var discard by remember { mutableStateOf(false) }
    var editingVideo by remember { mutableStateOf(false) }
    var framing by remember(draft.id) { mutableStateOf(false) }
    var gestureActive by remember(draft.id) { mutableStateOf(false) }
    var previewHeight by remember { mutableIntStateOf(0) }
    val scroll=rememberScrollState()
    val scope=rememberCoroutineScope()
    var caption by remember(draft.id) { mutableStateOf(TextFieldValue(draft.caption,TextRange(draft.caption.length))) }
    Dialog(onDismissRequest=onClose,properties=DialogProperties(usePlatformDefaultWidth=false)) {
        Box(Modifier.fillMaxSize().background(OgtColors.canvas).safeDrawingPadding()) {
        Column(Modifier.fillMaxSize()) {
            Row(Modifier.fillMaxWidth().padding(horizontal=20.dp,vertical=8.dp),verticalAlignment=Alignment.CenterVertically) {
                Text("Nueva historia",Modifier.weight(1f),fontWeight=FontWeight.Bold,fontSize=22.sp)
                TextButton(onClick={scope.launch { scroll.animateScrollTo(previewHeight) }},enabled=!gestureActive) { Text("Detalles") }
                TextButton(onClick=onClose) { Text("Cerrar") }
            }
            Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(scroll)) {
                StoryInteractivePreview(draft,Modifier.fillMaxWidth().onSizeChanged { previewHeight=it.height },
                    enabled=!controller.publishing && !preparing && !editingVideo,onGestureActive={gestureActive=it}) { frame ->
                    controller.draft?.takeIf { it.id==draft.id }?.let { controller.updateDraft(it.copy(frame=frame)) }
                }
                Column(Modifier.fillMaxWidth().padding(20.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
                    Text("Pellizcá para ampliar y arrastrá para encuadrar. Se guarda al soltar.",color=OgtColors.muted)
                    OutlinedButton(onClick={framing=true},enabled=!controller.publishing && !preparing && !editingVideo) { Text("Más opciones de encuadre") }
                    Text(if(draft.kind==MediaKind.VIDEO) "Video · hasta 30 segundos" else "Foto · 5 segundos",color=OgtColors.muted)
                    OutlinedTextField(caption,{ next ->
                        if(next.text.length<=StoryRules.MAX_CAPTION) {
                            caption=next
                            if(next.text!=controller.draft?.caption) controller.updateCaption(next.text)
                        }
                    },Modifier.fillMaxWidth(),enabled=!controller.publishing,label={Text("Texto opcional")},maxLines=5,
                        keyboardOptions=KeyboardOptions(capitalization=KeyboardCapitalization.Sentences))
                    if(draft.kind==MediaKind.VIDEO) com.onlygoodthings.app.ui.components.OgtVideoEditAction(
                        OgtPickedMedia(draft.id,draft.kind,draft.path,true,draft.posterPath,draft.prepared,draft.sourcePath,draft.audioMuted,draft.unmutedPath),
                        StoryRules.MAX_VIDEO_MS,enabled=!controller.publishing && !preparing,frame=draft.frame, onBusyChange={editingVideo=it},
                    ) { edited ->
                        controller.draft?.takeIf { it.id==edited.id }?.let { latest ->
                            controller.updateDraft(latest.copy(path=edited.path,posterPath=edited.posterPath,prepared=edited.preparedForUpload,sourcePath=edited.sourcePath,audioMuted=edited.audioMuted,unmutedPath=edited.unmutedPath))
                        }
                    }
                    Text("Quién puede verla",fontWeight=FontWeight.SemiBold)
                    Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                        StoryAudience.entries.forEach { audience ->
                            FilterChip(selected=draft.audience==audience,onClick={controller.updateAudience(audience)},enabled=!controller.publishing,
                                label={Text(if(audience==StoryAudience.COMMUNITY) "Comunidad" else "Seguidores")})
                        }
                    }
                    Text("Desaparece a las 24 horas. Se respetan las preferencias de privacidad de tu perfil.",fontSize=13.sp,color=OgtColors.muted)
                    if(!controller.publishing) {
                        TextButton(onClick=onReplace) { Text("Cambiar foto o video") }
                        TextButton(onClick={discard=true}) { Text("Descartar borrador",color=OgtColors.error) }
                    }
                }
                Spacer(Modifier.height(100.dp))
            }
        }
            Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                .background(Brush.verticalGradient(listOf(Color.Transparent,OgtColors.canvas.copy(alpha=.96f))))
                .padding(horizontal=20.dp,vertical=12.dp),verticalArrangement=Arrangement.spacedBy(6.dp)) {
                controller.publishError?.let { Text(it,color=OgtColors.error) }
                Button(onClick={controller.publish()},enabled=!controller.publishing && !preparing && !editingVideo && !gestureActive,modifier=Modifier.fillMaxWidth()) { Text(if(controller.publishing) "Publicando…" else if(controller.publishError!=null) "Reintentar publicación" else "Publicar historia") }
                if(controller.publishing) Text("Podés cerrar esta pantalla; la publicación continúa mientras la app esté abierta.",fontSize=12.sp)
            }
        }
    }
    if(framing) StoryFrameEditor(draft,onClose={framing=false}) { frame ->
        controller.draft?.takeIf { it.id==draft.id }?.let { controller.updateDraft(it.copy(frame=frame)) }
        framing=false
    }
    if(discard) AlertDialog(onDismissRequest={discard=false},title={Text("¿Descartar este borrador?")},confirmButton={TextButton(onClick={controller.updateDraft(null);discard=false;onClose()}) { Text("Descartar") }},dismissButton={TextButton(onClick={discard=false}) { Text("Conservar") }})
}

private data class StoryPageVisual(val story:Story,val url:String)

@Composable
private fun StoryPlayer(controller: StoriesController,story: Story,foreground:Boolean,owner:String,onClose:()->Unit,onNavigate:(String?)->Unit) {
    val queue=controller.readable.groupBy { it.authorId }.values.flatMap { it.sortedBy { row->row.createdAtEpochMs } }
    val scope=rememberCoroutineScope()
    val index=queue.indexOfFirst { it.id==story.id }
    val book=storyBookProgress(queue,story,0)
    val duration=storyPageDuration(story)
    var mediaUrl by remember(story.id) { mutableStateOf<String?>(null) }
    var visual by remember { mutableStateOf<StoryPageVisual?>(null) }
    var outgoing by remember { mutableStateOf<StoryPageVisual?>(null) }
    var backwards by remember { mutableStateOf(false) }
    val turn=remember { Animatable(1f) }
    var hold by remember(story.id) { mutableStateOf(false) }
    var ready by remember(story.id) { mutableStateOf(false) }
    var elapsed by remember(story.id) { mutableStateOf(0L) }
    var failure by remember(story.id) { mutableStateOf<String?>(null) }
    var retry by remember(story.id) { mutableStateOf(0) }
    var remove by remember(story.id) { mutableStateOf(false) }
    val playing=foreground && !hold && !remove && failure==null && outgoing==null
    LaunchedEffect(story.id,retry) {
        try {
            val previous=visual
            if(previous!=null && previous.story.id!=story.id) {
                backwards=previous.story.createdAtEpochMs>story.createdAtEpochMs && previous.story.authorId==story.authorId
                outgoing=previous;turn.snapTo(0f)
            }
            mediaUrl=controller.mediaSource(story)
            val poster=if(story.kind==MediaKind.VIDEO) try { controller.mediaSource(story,poster=true) }
                catch(cancel:CancellationException) { throw cancel } catch(_:Exception) { null } else mediaUrl
            visual=poster?.let { StoryPageVisual(story,it) }
        } catch(cancel:CancellationException) { throw cancel }
        catch(error:Exception) { outgoing=null;failure=error.message ?: "No se pudo cargar la historia" }
    }
    LaunchedEffect(story.id,ready) {
        if(ready && outgoing!=null) { turn.animateTo(1f,tween(520));outgoing=null }
    }
    // Warm only the following page, never the whole feed. Downloads share the bounded disk cache.
    LaunchedEffect(story.id,ready) {
        if(ready) queue.getOrNull(index+1)?.let { upcoming ->
            try { controller.mediaSource(upcoming) }
            catch(cancel:CancellationException) { throw cancel } catch(_:Exception) { /* Opening the page offers retry. */ }
        }
    }
    fun next() { onNavigate(queue.getOrNull(index+1)?.id) }
    LaunchedEffect(story.id,ready,playing) {
        if(!ready || !playing || story.kind==MediaKind.VIDEO) return@LaunchedEffect
        var previous=com.onlygoodthings.shared.realtime.currentEpochMs()
        while(isActive) {
            delay(40)
            val now=com.onlygoodthings.shared.realtime.currentEpochMs()
            elapsed+=(now-previous).coerceIn(0,100);previous=now
            if(elapsed>=duration) { next();break }
        }
    }
    LaunchedEffect(ready,foreground,story.id) { if(ready && foreground) controller.seen(story.id) }
    LaunchedEffect(story.id,elapsed) { if(story.kind==MediaKind.VIDEO && elapsed>=duration-40 && ready && outgoing==null) next() }
    Dialog(onDismissRequest=onClose,properties=DialogProperties(usePlatformDefaultWidth=false)) {
        Box(Modifier.fillMaxSize().background(Color.Black).safeDrawingPadding().clipToBounds()) {
            key(story.id,retry) {
                val source=mediaUrl
                if(source!=null) {
                if(story.frame==null) {
                    if(story.kind==MediaKind.VIDEO) OgtStoryVideo(source,playing,Modifier.fillMaxSize().padding(top=110.dp,bottom=150.dp),{ready=true},{elapsed=it},{failure=it})
                    else StoryFrameImage(source,null,Modifier.fillMaxSize(),{ready=true},{failure=it})
                } else StoryViewport(Modifier.fillMaxSize()) {
                    if(story.kind==MediaKind.VIDEO) OgtStoryVideo(source,playing,Modifier.fillMaxSize(),{ready=true},{elapsed=it},{failure=it},story.frame)
                    else StoryFrameImage(source,story.frame,Modifier.fillMaxSize(),{ready=true},{failure=it})
                }
            }
                }
            outgoing?.let { page ->
                val angle=(if(backwards) 88f else -88f)*turn.value
                Box(Modifier.fillMaxSize().graphicsLayer {
                    rotationY=angle
                    transformOrigin=TransformOrigin(if(backwards)1f else 0f,.5f)
                    cameraDistance=18*density
                    shadowElevation=24*density*turn.value
                }.background(Color.Black)) {
                    if(page.story.frame==null) StoryFrameImage(page.url,null,Modifier.fillMaxSize())
                    else StoryViewport(Modifier.fillMaxSize()) { StoryFrameImage(page.url,page.story.frame,Modifier.fillMaxSize()) }
                    Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(listOf(
                        Color.Transparent,Color.Black.copy(alpha=(.55f*turn.value).coerceIn(0f,1f)),Color.White.copy(alpha=.12f*turn.value)))))
                }
            }
            if(!ready && failure==null) OgtLoader(size=32.dp,modifier=Modifier.align(Alignment.Center))
            Box(Modifier.fillMaxSize().padding(top=110.dp,bottom=150.dp).pointerInput(story.id) {
                detectTapGestures(onLongPress={},onPress={hold=true;try { tryAwaitRelease() } finally { hold=false } },onTap={position->
                    if(outgoing!=null) return@detectTapGestures
                    if(position.x<size.width/3) onNavigate(queue.getOrNull(index-1)?.id ?: story.id) else next()
                })
            })
            Column(Modifier.align(Alignment.TopCenter).fillMaxWidth().background(Brush.verticalGradient(listOf(Color.Black.copy(alpha=.75f),Color.Transparent))).padding(12.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
                LinearProgressIndicator(progress={ storyBookProgress(queue,story,elapsed).fraction },modifier=Modifier.fillMaxWidth().semantics { contentDescription="Progreso total de las historias de ${story.authorName}" },color=Color.White,trackColor=Color.White.copy(alpha=.25f))
                Row(verticalAlignment=Alignment.CenterVertically) {
                    OgtNeighborAvatar(story.authorId,story.authorPhotoUrl,story.authorName,Modifier.size(38.dp).clip(CircleShape))
                    Column(Modifier.weight(1f).padding(start=8.dp)) {
                        Text(story.authorName,color=Color.White,fontWeight=FontWeight.Bold)
                        Text("Página ${book.page} de ${book.pages}",color=Color.White,fontSize=12.sp)
                        val minutes=((controller.now-story.createdAtEpochMs)/60000).coerceAtLeast(0)
                        Text(if(minutes<1) "Ahora" else if(minutes<60) "Hace $minutes min" else "Hace ${minutes/60} h",color=Color.White,fontSize=12.sp)
                    }
                    TextButton(onClick=onClose) { Text("Cerrar",color=Color.White) }
                }
            }
            Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().background(Color.Black.copy(alpha=.45f)).padding(16.dp)) {
                Text(story.caption,color=Color.White)
                if(story.authorId==owner) {
                    Text("${story.viewCount} vistas",color=Color.White,fontSize=12.sp)
                    TextButton(onClick={remove=true}) { Text("Eliminar historia",color=Color.White) }
                }
                failure?.let { message ->
                    Text(message,color=Color.White)
                    TextButton(onClick={scope.launch { controller.invalidateStoryMedia(story);controller.refresh();failure=null;ready=false;mediaUrl=null;retry++ }}) { Text("Reintentar",color=Color.White) }
                }
            }
        }
    }
    if(remove) AlertDialog(onDismissRequest={remove=false},title={Text("¿Eliminar esta historia?")},text={Text("Dejará de estar disponible para todas las personas.")},confirmButton={TextButton(onClick={controller.remove(story.id);remove=false}) { Text("Eliminar") }},dismissButton={TextButton(onClick={remove=false}) { Text("Cancelar") }})
}
