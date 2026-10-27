package com.onlygoodthings.app.platform

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import kotlinx.coroutines.*
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.onlygoodthings.app.AndroidAuthHost
import com.onlygoodthings.app.OgtApplication
import com.onlygoodthings.shared.domain.MediaKind
import com.onlygoodthings.shared.domain.PostMediaRules
import java.io.File
import java.util.UUID

@Composable
actual fun rememberPlatformOgtMediaPicker(remaining: Int, onPicked: (OgtPickedMedia) -> Unit): OgtMediaPicker {
    val context = pickerContext()
    val pickerError = remember { mutableStateOf<String?>(null) }
    val scope=rememberCoroutineScope()
    val processing=remember { mutableStateOf(false) }
    fun load(uris:List<Uri>,kind:MediaKind?) {
        processing.value = true
        scope.launch {
            try { for(uri in uris.take(remaining.coerceAtLeast(0))) {
                val media=withContext(Dispatchers.IO) { copyPicked(context,uri,kind ?: mediaKindOf(context,uri)) }
                if(media!=null) onPicked(media) else pickerError.value="No se pudo copiar el archivo. Elegilo otra vez."
            } } finally { processing.value = false }
        }
    }
    var recording by remember { mutableStateOf(false) }
    var pendingCamera by remember { mutableStateOf<File?>(null) }
    val photo = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(PostMediaRules.MAX_ITEMS),
    ) { uris ->
        load(uris,MediaKind.IMAGE)
    }
    val video = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(PostMediaRules.MAX_ITEMS),
    ) { uris ->
        load(uris,MediaKind.VIDEO)
    }
    val library = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(PostMediaRules.MAX_ITEMS),
    ) { uris ->
        load(uris,null)
    }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val file = pendingCamera
        pendingCamera = null
        if (ok && remaining > 0 && file != null && file.exists()) {
            onPicked(
                OgtPickedMedia(
                    id = file.absolutePath,
                    kind = MediaKind.IMAGE,
                    path = file.absolutePath,
                    fromDevice = true,
                ),
            )
        }
    }
    val record = rememberLauncherForActivityResult(ActivityResultContracts.CaptureVideo()) { ok ->
        val file=pendingCamera;pendingCamera=null
        if(ok && file!=null && file.exists()) {
            processing.value = true
            scope.launch {
                try {
                    val poster=withContext(Dispatchers.IO) { videoPoster(context,file.absolutePath) }
                    onPicked(OgtPickedMedia(file.absolutePath,MediaKind.VIDEO,file.absolutePath,true,poster))
                } finally { processing.value = false }
            }
        }
    }
    val askCamera = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (!granted) { pickerError.value="Habilitá el permiso de cámara en Ajustes o elegí un archivo de la galería.";return@rememberLauncherForActivityResult }
        if(remaining<=0) return@rememberLauncherForActivityResult
        if(recording) {
            val file=newCacheFile(context,"mp4");pendingCamera=file;record.launch(fileProviderUri(context,file));return@rememberLauncherForActivityResult
        }
        val file = newCacheFile(context, "jpg")
        pendingCamera = file
        camera.launch(fileProviderUri(context, file))
    }
    return remember(photo, video, library, camera, askCamera, remaining) {
        OgtMediaPicker(
            pickPhoto = {
                if (remaining > 0) {
                    photo.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                }
            },
            pickVideo = {
                if (remaining > 0) {
                    video.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly))
                }
            },
            pickLibrary = {
                if (remaining > 0) {
                    library.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo))
                }
            },
            errorMessage = pickerError,
            isProcessing = processing,
            recordVideo = {
                recording=true
                if(cameraGranted(context)) { val file=newCacheFile(context,"mp4");pendingCamera=file;record.launch(fileProviderUri(context,file)) }
                else askCamera.launch(Manifest.permission.CAMERA)
            },
            takeCamera = {
                recording=false
                if (remaining > 0) {
                    if (cameraGranted(context)) {
                        val file = newCacheFile(context, "jpg")
                        pendingCamera = file
                        camera.launch(fileProviderUri(context, file))
                    } else {
                        askCamera.launch(Manifest.permission.CAMERA)
                    }
                }
            },
        )
    }
}

private fun mediaKindOf(context: Context, uri: Uri): MediaKind {
    val type = context.contentResolver.getType(uri).orEmpty()
    return if (type.startsWith("video/")) MediaKind.VIDEO else MediaKind.IMAGE
}

private fun copyPicked(context:Context,uri:Uri,kind:MediaKind):OgtPickedMedia? {
    val dest=copyUri(context,uri,if(kind==MediaKind.VIDEO) "mp4" else "jpg") ?: return null
    return OgtPickedMedia(dest.absolutePath,kind,dest.absolutePath,true,if(kind==MediaKind.VIDEO) videoPoster(context,dest.absolutePath) else null)
}

private fun copyUri(context: Context, uri: Uri, ext: String): File? = runCatching {
    val dest = newCacheFile(context, ext)
    context.contentResolver.openInputStream(uri)?.use { input ->
        dest.outputStream().use { input.copyTo(it) }
    } ?: return null
    dest.takeIf { it.length() > 0 }
}.getOrNull()

private fun videoPoster(context: Context, path: String): String? = runCatching {
    val retriever=MediaMetadataRetriever()
    try {
        retriever.setDataSource(path)
        val original=retriever.frameAtTime ?: return null
        val scale=minOf(1f,720f/maxOf(original.width,original.height))
        val frame=Bitmap.createScaledBitmap(original,maxOf(1,(original.width*scale).toInt()),maxOf(1,(original.height*scale).toInt()),true)
        if(frame!==original) original.recycle()
        val dest=newCacheFile(context,"jpg")
        try { dest.outputStream().use { frame.compress(Bitmap.CompressFormat.JPEG,82,it) } } finally { frame.recycle() }
        dest.absolutePath
    } finally { retriever.release() }
}.getOrNull()

private fun newCacheFile(context: Context, ext: String): File {
    val dir = File(context.cacheDir, "ogt-media").apply { mkdirs() }
    return File(dir, "${UUID.randomUUID()}.$ext")
}

private fun fileProviderUri(context: Context, file: File): Uri =
    FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)

private fun cameraGranted(context: Context): Boolean =
    ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
        PackageManager.PERMISSION_GRANTED

private fun pickerContext(): Context =
    runCatching { AndroidAuthHost.requireActivity() as Context }.getOrElse { OgtApplication.instance }
