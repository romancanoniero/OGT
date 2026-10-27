package com.onlygoodthings.app.platform

import android.media.MediaMetadataRetriever
import com.onlygoodthings.app.OgtApplication
import kotlinx.coroutines.*
import java.io.File

private suspend fun metadata(path:String):Triple<Long,Boolean,Int> = withContext(Dispatchers.IO) {
    val reader=MediaMetadataRetriever()
    try {
        reader.setDataSource(path.removePrefix("file://"))
        val duration=reader.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: error("Video inválido")
        require(duration>0)
        Triple(duration,reader.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_AUDIO)=="yes",
            reader.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 720)
    } finally { reader.release() }
}

actual suspend fun inspectPickedVideo(path:String):PickedVideoInfo {
    val info=metadata(path)
    return PickedVideoInfo(info.first,info.second)
}

internal actual suspend fun removePickedVideoAudio(item:OgtPickedMedia):String {
    val info=metadata(item.path)
    return withContext(Dispatchers.Main) {
        withTimeout(90_000) {
            // Process the current prepared segment, not sourcePath's untrimmed original.
            val result=exportAndroidVideo(OgtApplication.instance,item.copy(sourcePath=item.path),
                VideoTrimRange(0,info.first),info.third,removeAudio=true)
            result.posterPath?.takeIf { it!=item.posterPath }?.let { File(it).delete() }
            result.path
        }
    }
}
