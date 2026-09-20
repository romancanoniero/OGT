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
actual fun rememberOgtMediaPicker(remaining: Int, onPicked: (OgtPickedMedia) -> Unit): OgtMediaPicker {
    val context = pickerContext()
    var pendingCamera by remember { mutableStateOf<File?>(null) }
    val photo = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(PostMediaRules.MAX_ITEMS),
    ) { uris ->
        uris.take(remaining.coerceAtLeast(0)).forEach { emitCopied(context, it, MediaKind.IMAGE, onPicked) }
    }
    val video = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(PostMediaRules.MAX_ITEMS),
    ) { uris ->
        uris.take(remaining.coerceAtLeast(0)).forEach { emitCopied(context, it, MediaKind.VIDEO, onPicked) }
    }
    val library = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(PostMediaRules.MAX_ITEMS),
    ) { uris ->
        uris.take(remaining.coerceAtLeast(0)).forEach { uri ->
            emitCopied(context, uri, mediaKindOf(context, uri), onPicked)
        }
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
    val askCamera = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (!granted || remaining <= 0) return@rememberLauncherForActivityResult
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
            takeCamera = {
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

private fun emitCopied(
    context: Context,
    uri: Uri,
    kind: MediaKind,
    onPicked: (OgtPickedMedia) -> Unit,
) {
    val ext = if (kind == MediaKind.VIDEO) "mp4" else "jpg"
    val dest = copyUri(context, uri, ext) ?: return
    onPicked(
        OgtPickedMedia(
            id = dest.absolutePath,
            kind = kind,
            path = dest.absolutePath,
            fromDevice = true,
            posterPath = if (kind == MediaKind.VIDEO) videoPoster(context, dest.absolutePath) else null,
        ),
    )
}

private fun copyUri(context: Context, uri: Uri, ext: String): File? = runCatching {
    val dest = newCacheFile(context, ext)
    context.contentResolver.openInputStream(uri)?.use { input ->
        dest.outputStream().use { input.copyTo(it) }
    } ?: return null
    dest.takeIf { it.length() > 0 }
}.getOrNull()

private fun videoPoster(context: Context, path: String): String? = runCatching {
    val retriever = MediaMetadataRetriever()
    retriever.setDataSource(path)
    val frame = retriever.frameAtTime
    retriever.release()
    val dest = newCacheFile(context, "jpg")
    dest.outputStream().use { out ->
        frame?.compress(Bitmap.CompressFormat.JPEG, 86, out) ?: return null
    }
    dest.absolutePath
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
