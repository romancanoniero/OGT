package com.onlygoodthings.app.platform

import androidx.compose.runtime.*
import androidx.compose.material3.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import com.onlygoodthings.shared.domain.MediaKind
import com.onlygoodthings.shared.domain.PostMediaRules

/** Foto o video elegido en el dispositivo, ya copiado a caché de la app. */
data class OgtPickedMedia(
    val id: String,
    val kind: MediaKind,
    val path: String,
    val fromDevice: Boolean,
    val posterPath: String? = null,
    val preparedForUpload: Boolean = false,
    val sourcePath: String? = null,
    val audioMuted: Boolean = false,
    val unmutedPath: String? = null,
)

class OgtMediaPicker(
    val pickPhoto: () -> Unit,
    val pickVideo: () -> Unit,
    val pickLibrary: () -> Unit,
    val takeCamera: () -> Unit,
    val recordVideo: () -> Unit = takeCamera,
    val isProcessing: State<Boolean> = mutableStateOf(false),
    val errorMessage: androidx.compose.runtime.MutableState<String?> = androidx.compose.runtime.mutableStateOf(null),
)

/** Galería (una o varias), videos o cámara del sistema. */
@Composable
fun rememberOgtMediaPicker(onPicked: (OgtPickedMedia) -> Unit): OgtMediaPicker =
    rememberOgtMediaPicker(PostMediaRules.MAX_ITEMS, onPicked = onPicked)

/** Serialize selection and editing so multi-select cannot present overlapping editors. */
@Composable
fun rememberOgtMediaPicker(remaining: Int, videoMaxDurationMs: Long? = null, onPicked: (OgtPickedMedia) -> Unit): OgtMediaPicker {
    val callback by rememberUpdatedState(onPicked)
    val limit by rememberUpdatedState(videoMaxDurationMs)
    val queue=remember { Channel<OgtPickedMedia>(Channel.UNLIMITED) }
    val editor=rememberOgtVideoEditor()
    var failure by remember { mutableStateOf<String?>(null) }
    var pending by remember { mutableStateOf(0) }
    val raw=rememberPlatformOgtMediaPicker(remaining) { if(queue.trySend(it).isSuccess) pending++ }
    LaunchedEffect(queue,editor) {
        for(item in queue) {
            try {
                val ready=if(item.kind==MediaKind.VIDEO) editor.edit(item,limit) else item
                if(ready!=null) callback(ready)
            } catch(cancel:CancellationException) { throw cancel }
            catch(error:Exception) { failure=error.message ?: "No se pudo preparar el video. Elegilo otra vez." }
            finally { pending-- }
        }
    }
    DisposableEffect(queue) { onDispose { queue.close() } }
    val message=failure ?: raw.errorMessage.value
    if(message!=null) AlertDialog(onDismissRequest={failure=null;raw.errorMessage.value=null},title={Text("No se pudo preparar el archivo")},text={Text(message.orEmpty())},confirmButton={TextButton(onClick={failure=null;raw.errorMessage.value=null}) { Text("Aceptar") }})
    val processing = remember(raw) { derivedStateOf { pending > 0 || raw.isProcessing.value } }
    return remember(raw, processing) { OgtMediaPicker(raw.pickPhoto, raw.pickVideo, raw.pickLibrary, raw.takeCamera, raw.recordVideo, processing, raw.errorMessage) }
}

@Composable
expect fun rememberPlatformOgtMediaPicker(remaining: Int, onPicked: (OgtPickedMedia) -> Unit): OgtMediaPicker
