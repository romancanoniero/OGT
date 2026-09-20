@file:OptIn(ExperimentalForeignApi::class)

package com.onlygoodthings.app.platform

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
actual fun rememberOgtMediaPicker(remaining: Int, onPicked: (OgtPickedMedia) -> Unit): OgtMediaPicker {
    val holder = remember { IosPickerHolder() }
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
    val delegate = IosPhPickerDelegate(onPicked)
    holder.libraryDelegate = delegate
    picker.delegate = delegate
    topViewController()?.presentViewController(picker, animated = true, completion = null)
}

private fun presentCamera(
    holder: IosPickerHolder,
    source: UIImagePickerControllerSourceType,
    onPicked: (OgtPickedMedia) -> Unit,
) {
    val picker = UIImagePickerController()
    picker.sourceType = source
    picker.mediaTypes = listOf("public.image", "public.movie")
    picker.allowsEditing = false
    val delegate = IosMediaPickerDelegate(onPicked)
    holder.cameraDelegate = delegate
    picker.delegate = delegate
    topViewController()?.presentViewController(picker, animated = true, completion = null)
}

private class IosPhPickerDelegate(
    private val onPicked: (OgtPickedMedia) -> Unit,
) : NSObject(), PHPickerViewControllerDelegateProtocol {

    override fun picker(picker: PHPickerViewController, didFinishPicking: List<*>) {
        picker.dismissViewControllerAnimated(true, completion = null)
        didFinishPicking.forEach { item ->
            val result = item as? PHPickerResult ?: return@forEach
            loadPickerResult(result, onPicked)
        }
    }
}

private fun loadPickerResult(result: PHPickerResult, onPicked: (OgtPickedMedia) -> Unit) {
    val provider = result.itemProvider
    if (provider.hasItemConformingToTypeIdentifier("public.movie")) {
        provider.loadFileRepresentationForTypeIdentifier("public.movie") { url, _ ->
            val src = url?.path ?: return@loadFileRepresentationForTypeIdentifier
            val dest = copyToCache(src, "mov")
            onPicked(
                OgtPickedMedia(
                    id = dest,
                    kind = MediaKind.VIDEO,
                    path = dest,
                    fromDevice = true,
                    posterPath = videoPoster(dest),
                ),
            )
        }
        return
    }
    provider.loadDataRepresentationForTypeIdentifier("public.image") { data, _ ->
        val image = data?.let { UIImage(data = it) } ?: return@loadDataRepresentationForTypeIdentifier
        val dest = writeJpeg(image) ?: return@loadDataRepresentationForTypeIdentifier
        onPicked(
            OgtPickedMedia(
                id = dest,
                kind = MediaKind.IMAGE,
                path = dest,
                fromDevice = true,
            ),
        )
    }
}

private class IosMediaPickerDelegate(
    private val onPicked: (OgtPickedMedia) -> Unit,
) : NSObject(), UIImagePickerControllerDelegateProtocol, UINavigationControllerDelegateProtocol {

    override fun imagePickerController(
        picker: UIImagePickerController,
        didFinishPickingMediaWithInfo: Map<Any?, *>,
    ) {
        picker.dismissViewControllerAnimated(true, completion = null)
        val type = didFinishPickingMediaWithInfo[UIImagePickerControllerMediaType] as? String
        val video = type?.contains("movie") == true || type?.contains("video") == true
        val media = if (video) {
            val url = didFinishPickingMediaWithInfo[UIImagePickerControllerMediaURL] as? NSURL
            val src = url?.path ?: return
            val dest = copyToCache(src, "mov")
            OgtPickedMedia(
                id = dest,
                kind = MediaKind.VIDEO,
                path = dest,
                fromDevice = true,
                posterPath = videoPoster(dest),
            )
        } else {
            val image = didFinishPickingMediaWithInfo[UIImagePickerControllerOriginalImage] as? UIImage ?: return
            val dest = writeJpeg(image) ?: return
            OgtPickedMedia(
                id = dest,
                kind = MediaKind.IMAGE,
                path = dest,
                fromDevice = true,
            )
        }
        onPicked(media)
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
    runCatching { NSFileManager.defaultManager.copyItemAtPath(src, dest, null) }
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
