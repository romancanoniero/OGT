@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)
package com.onlygoodthings.app.platform

import kotlinx.coroutines.*
import platform.AVFoundation.*
import platform.CoreMedia.CMTimeGetSeconds
import platform.Foundation.NSFileManager
import platform.Foundation.NSURL
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

interface IosVideoToolsHost {
    fun removeAudio(path: String, onResult: (String?, String?) -> Unit)
    fun cancelAudio()
}
object IosVideoToolsRuntime { var host: IosVideoToolsHost? = null }

actual suspend fun inspectPickedVideo(path: String): PickedVideoInfo = withContext(Dispatchers.Default) {
    require(NSFileManager.defaultManager.fileExistsAtPath(path.removePrefix("file://"))) { "El video ya no está en el dispositivo." }
    val asset=AVURLAsset.URLAssetWithURL(NSURL.fileURLWithPath(path.removePrefix("file://")),null)
    withTimeout(15_000) {
        suspendCancellableCoroutine { done ->
            done.invokeOnCancellation { asset.cancelLoading() }
            asset.loadValuesAsynchronouslyForKeys(listOf("duration","tracks")) {
                if(done.isActive) try {
                    require(asset.statusOfValueForKey("duration",null)==AVKeyValueStatusLoaded && asset.statusOfValueForKey("tracks",null)==AVKeyValueStatusLoaded)
                    val duration=CMTimeGetSeconds(asset.duration)
                    require(duration.isFinite() && duration>0)
                    done.resume(PickedVideoInfo((duration*1000).toLong(),asset.tracksWithMediaType(AVMediaTypeAudio).isNotEmpty()))
                } catch(_:Exception) { if(done.isActive) done.resumeWithException(IllegalArgumentException("No se pudo leer el video.")) }
            }
        }
    }
}

internal actual suspend fun removePickedVideoAudio(item: OgtPickedMedia): String = withContext(Dispatchers.Main) {
    val host=IosVideoToolsRuntime.host ?: error("Las herramientas de video no están disponibles.")
    withTimeout(90_000) {
        suspendCancellableCoroutine { done ->
            done.invokeOnCancellation { CoroutineScope(Dispatchers.Main).launch { host.cancelAudio() } }
            host.removeAudio(item.path.removePrefix("file://")) { path,error ->
                if(done.isActive) {
                    if(path!=null) done.resume(path)
                    else done.resumeWithException(IllegalArgumentException(error ?: "No se pudo quitar el sonido."))
                }
            }
        }
    }
}
