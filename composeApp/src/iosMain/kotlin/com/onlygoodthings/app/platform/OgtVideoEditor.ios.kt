package com.onlygoodthings.app.platform

import androidx.compose.runtime.*
import kotlinx.coroutines.*
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

interface IosVideoEditorHost {
    /** Null path with no error is cancellation. The source is never modified. */
    fun edit(path:String,maxDurationMs:Long,maxBytes:Long,onResult:(String?,String?,String?)->Unit)
    fun cancel()
}
object IosVideoEditorRuntime { var host:IosVideoEditorHost?=null }

@Composable
actual fun rememberOgtVideoEditor():OgtVideoEditor = remember {
    OgtVideoEditor { item,limit ->
        withContext(Dispatchers.Main) {
            val host=IosVideoEditorRuntime.host ?: error("El editor de video no está disponible")
            suspendCancellableCoroutine { continuation ->
                continuation.invokeOnCancellation { CoroutineScope(Dispatchers.Main).launch { host.cancel() } }
                val original=item.sourcePath?.takeIf { platform.Foundation.NSFileManager.defaultManager.fileExistsAtPath(it) } ?: item.path.removePrefix("file://")
                host.edit(original,limit ?: 0L,VideoUploadRules.MAX_BYTES) { path,poster,error ->
                    if(continuation.isActive) {
                        if(error!=null) continuation.resumeWithException(IllegalArgumentException(error))
                        else continuation.resume(path?.let { item.copy(path=it,posterPath=poster,preparedForUpload=true,sourcePath=original) })
                    }
                }
            }
        }
    }
}
