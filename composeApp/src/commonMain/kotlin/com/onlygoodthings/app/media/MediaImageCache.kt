package com.onlygoodthings.app.media

import androidx.compose.ui.graphics.ImageBitmap
import com.onlygoodthings.app.map.decodeTileBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Decoded previews are reused while scrolling back, within a separate memory budget. */
object MediaImageCache {
    private val images=MediaMemoryCache<ImageBitmap>(48L*1024*1024) { it.width.toLong()*it.height*4 }
    fun peek(url:String):ImageBitmap?=images.get(canonicalMediaUrl(url))
    suspend fun load(url:String):ImageBitmap?=withContext(Dispatchers.Default) {
        val key=canonicalMediaUrl(url)
        images.get(key) ?: OgtMediaCache.load(key)?.let(::decodeTileBitmap)?.also { images.put(key,it) }
    }
}
