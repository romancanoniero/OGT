@file:OptIn(ExperimentalForeignApi::class)

package com.onlygoodthings.app.platform

import kotlinx.coroutines.*
import androidx.compose.runtime.*
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.onlygoodthings.shared.domain.MediaKind
import kotlinx.cinterop.ExperimentalForeignApi
import platform.AVFoundation.AVAssetImageGenerator
import platform.AVFoundation.AVURLAsset
import platform.CoreMedia.CMTimeMake
import platform.Foundation.NSCachesDirectory
import platform.Foundation.NSData
import platform.Foundation.NSFileManager
import platform.Foundation.NSSearchPathForDirectoriesInDomains
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSUserDomainMask
import platform.Foundation.NSURL
import platform.Foundation.NSUUID
import platform.Foundation.writeToFile
import platform.PhotosUI.PHPickerConfiguration
import platform.PhotosUI.PHPickerFilter
import platform.PhotosUI.PHPickerResult
import platform.PhotosUI.PHPickerViewController
import platform.PhotosUI.PHPickerViewControllerDelegateProtocol
import platform.UIKit.UIApplication
import platform.UIKit.UIImage
import platform.UIKit.UIImageJPEGRepresentation
import platform.UIKit.UIImagePickerController
import platform.UIKit.UIImagePickerControllerDelegateProtocol
import platform.UIKit.UIImagePickerControllerMediaType
import platform.UIKit.UIImagePickerControllerMediaURL
import platform.UIKit.UIImagePickerControllerOriginalImage
import platform.UIKit.UIImagePickerControllerSourceType
import platform.UIKit.UINavigationControllerDelegateProtocol
import platform.UIKit.UIViewController
import platform.darwin.NSObject

@Composable
actual fun rememberPlatformOgtMediaPicker(remaining: Int, onPicked: (OgtPickedMedia) -> Unit): OgtMediaPicker {
    val holder = remember { IosPickerHolder() }
    DisposableEffect(holder) { onDispose { holder.scope.cancel() } }
    return remember(onPicked, remaining) {
        OgtMediaPicker(
            pickPhoto = {
                presentPhPicker(holder, remaining, photosOnly = true, videosOnly = false, onPicked)
            },
            pickVideo = {
                presentPhPicker(holder, remaining, photosOnly = false, videosOnly = true, onPicked)
            },
            pickLibrary = {
                presentPhPicker(holder, remaining, photosOnly = false, videosOnly = false, onPicked)
            },
            errorMessage=holder.error,
            isProcessing=holder.processing,
            recordVideo = {
                if (remaining > 0) {
                    val camera = UIImagePickerControllerSourceType.UIImagePickerControllerSourceTypeCamera
                    val source = if (UIImagePickerController.isSourceTypeAvailable(camera)) camera else UIImagePickerControllerSourceType.UIImagePickerControllerSourceTypePhotoLibrary
                    presentCamera(holder, source, onPicked, videoOnly = true)
                }
            },
            takeCamera = {
                if (remaining > 0) {
                    val camera = UIImagePickerControllerSourceType.UIImagePickerControllerSourceTypeCamera
                    val source = if (UIImagePickerController.isSourceTypeAvailable(camera)) {
                        camera
                    } else {
                        UIImagePickerControllerSourceType.UIImagePickerControllerSourceTypePhotoLibrary
                    }
                    presentCamera(holder, source, onPicked)
                }
            },
        )
    }
}

private class IosPickerHolder {
    val scope=CoroutineScope(SupervisorJob()+Dispatchers.Default)
    val error=mutableStateOf<String?>(null)
    val processing=mutableStateOf(false)
    var cameraDelegate: IosMediaPickerDelegate? = null
    var libraryDelegate: IosPhPickerDelegate? = null
}

private fun presentPhPicker(
    holder: IosPickerHolder,
    remaining: Int,
    photosOnly: Boolean,
    videosOnly: Boolean,
    onPicked: (OgtPickedMedia) -> Unit,
) {
    if (remaining <= 0) return
    val config = PHPickerConfiguration()
    config.selectionLimit = remaining.toLong()
    config.filter = when {
        photosOnly -> PHPickerFilter.imagesFilter
        videosOnly -> PHPickerFilter.videosFilter
        else -> PHPickerFilter.anyFilterMatchingSubfilters(
            listOf(PHPickerFilter.imagesFilter, PHPickerFilter.videosFilter),
        )
    }
    val picker = PHPickerViewController(configuration = config)
    val delegate = IosPhPickerDelegate(holder,onPicked)
    holder.libraryDelegate = delegate
    picker.delegate = delegate
    topViewController()?.presentViewController(picker, animated = true, completion = null)
}

private fun presentCamera(
    holder: IosPickerHolder,
    source: UIImagePickerControllerSourceType,
    onPicked: (OgtPickedMedia) -> Unit,
    videoOnly: Boolean = false,
) {
    val picker = UIImagePickerController()
    picker.sourceType = source
    picker.mediaTypes = if(videoOnly) listOf("public.movie") else listOf("public.image", "public.movie")
    if(videoOnly) picker.videoMaximumDuration=30.0
    picker.allowsEditing = false
    val delegate = IosMediaPickerDelegate(holder,onPicked)
    holder.cameraDelegate = delegate
    picker.delegate = delegate
    topViewController()?.presentViewController(picker, animated = true, completion = null)
}

private class IosPhPickerDelegate(
    private val holder:IosPickerHolder,
    private val onPicked: (OgtPickedMedia) -> Unit,
) : NSObject(), PHPickerViewControllerDelegateProtocol {

    override fun picker(picker: PHPickerViewController, didFinishPicking: List<*>) {
        holder.processing.value = didFinishPicking.isNotEmpty()
        picker.dismissViewControllerAnimated(true) {
            holder.scope.launch {
                try { for(item in didFinishPicking) {
                    val result=item as? PHPickerResult ?: continue
                    try { val media=loadPickerResult(result);withContext(Dispatchers.Main) { onPicked(media) } }
                    catch(cancel:CancellationException) { throw cancel }
                    catch(_:Exception) { withContext(Dispatchers.Main) { holder.error.value="No se pudo copiar el archivo. Elegilo otra vez." } }
                } } finally { withContext(NonCancellable + Dispatchers.Main) { holder.processing.value = false } }
            }
        }
    }
}

private suspend fun loadPickerResult(result:PHPickerResult):OgtPickedMedia = suspendCancellableCoroutine { done ->
    val provider=result.itemProvider
    fun fail() { if(done.isActive) done.resumeWithException(IllegalArgumentException("No se pudo leer el archivo")) }
    if(provider.hasItemConformingToTypeIdentifier("public.movie")) {
        provider.loadFileRepresentationForTypeIdentifier("public.movie") { url,_ ->
            try {
                val src=url?.path ?: error("Archivo no disponible")
                val dest=copyToCache(src,"mov")
                // The editor generates a verified poster after trimming. Extracting
                // it here delays opening the editor and reads the same asset twice.
                val media=OgtPickedMedia(dest,MediaKind.VIDEO,dest,true)
                if(done.isActive) done.resume(media)
            } catch(_:Exception) { fail() }
        }
    } else {
        provider.loadDataRepresentationForTypeIdentifier("public.image") { data,_ ->
            try {
                val image=data?.let { UIImage(data=it) } ?: error("Imagen no disponible")
                val dest=writeJpeg(image) ?: error("No se pudo copiar la imagen")
                if(done.isActive) done.resume(OgtPickedMedia(dest,MediaKind.IMAGE,dest,true))
            } catch(_:Exception) { fail() }
        }
    }
}

private class IosMediaPickerDelegate(
    private val holder:IosPickerHolder,
    private val onPicked: (OgtPickedMedia) -> Unit,
) : NSObject(), UIImagePickerControllerDelegateProtocol, UINavigationControllerDelegateProtocol {

    override fun imagePickerController(
        picker: UIImagePickerController,
        didFinishPickingMediaWithInfo: Map<Any?, *>,
    ) {
        holder.processing.value = true
        picker.dismissViewControllerAnimated(true) {
        holder.scope.launch {
        try {
        val type = didFinishPickingMediaWithInfo[UIImagePickerControllerMediaType] as? String
        val video = type?.contains("movie") == true || type?.contains("video") == true
        val media = if (video) {
            val url = didFinishPickingMediaWithInfo[UIImagePickerControllerMediaURL] as? NSURL
            val src = url?.path ?: return@launch
            val dest = copyToCache(src, "mov")
            OgtPickedMedia(
                id = dest,
                kind = MediaKind.VIDEO,
                path = dest,
                fromDevice = true,
            )
        } else {
            val image = didFinishPickingMediaWithInfo[UIImagePickerControllerOriginalImage] as? UIImage ?: return@launch
            val dest = writeJpeg(image) ?: return@launch
            OgtPickedMedia(
                id = dest,
                kind = MediaKind.IMAGE,
                path = dest,
                fromDevice = true,
            )
        }
        withContext(Dispatchers.Main) { onPicked(media) }
        } catch(cancel:CancellationException) { throw cancel }
        catch(_:Exception) { withContext(Dispatchers.Main) { holder.error.value="No se pudo preparar el archivo. Elegilo otra vez." } }
        finally { withContext(NonCancellable + Dispatchers.Main) { holder.processing.value = false } }
        }
        }
    }

    override fun imagePickerControllerDidCancel(picker: UIImagePickerController) {
        picker.dismissViewControllerAnimated(true, completion = null)
    }
}

private fun ensureCacheDir(): String {
    val root = (NSSearchPathForDirectoriesInDomains(NSCachesDirectory, NSUserDomainMask, true).firstOrNull() as? String)
        ?: NSTemporaryDirectory()
    val dir = "${root.trimEnd('/')}/ogt-media"
    NSFileManager.defaultManager.createDirectoryAtPath(dir, true, null, null)
    return dir
}

private fun writeJpeg(image: UIImage): String? {
    val path = "${ensureCacheDir()}/${NSUUID.UUID().UUIDString}.jpg"
    val data: NSData = UIImageJPEGRepresentation(image, 0.86) ?: return null
    val ok = data.writeToFile(path, atomically = true)
    return path.takeIf { ok }
}

private fun copyToCache(src: String, ext: String): String {
    val dest = "${ensureCacheDir()}/${NSUUID.UUID().UUIDString}.$ext"
    check(NSFileManager.defaultManager.copyItemAtPath(src,dest,null)) { "No se pudo copiar el archivo" }
    return dest
}

private fun videoPoster(path: String): String? = runCatching {
    val url = NSURL.fileURLWithPath(path)
    val asset = AVURLAsset.URLAssetWithURL(url, null)
    val gen = AVAssetImageGenerator(asset)
    gen.appliesPreferredTrackTransform = true
    val cg = gen.copyCGImageAtTime(CMTimeMake(0, 1), actualTime = null, error = null) ?: return null
    val image = UIImage(cg)
    writeJpeg(image)
}.getOrNull()

private fun topViewController(): UIViewController? {
    val window = UIApplication.sharedApplication.keyWindow
    var top = window?.rootViewController
    while (top?.presentedViewController != null) {
        top = top?.presentedViewController
    }
    return top
}
