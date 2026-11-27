package com.onlygoodthings.app.media

/** Byte-bounded LRU. Reads used by Compose never read or decode a disk file. */
internal class MediaMemoryCache<T>(private val budget:Long,private val sizeOf:(T)->Long) {
    private val entries=linkedMapOf<String,T>()
    private var bytes=0L
    fun get(key:String):T?=withMediaCacheLock {
        entries.remove(key)?.also { entries[key]=it }
    }
    fun put(key:String,value:T)=withMediaCacheLock {
        entries.remove(key)?.let { bytes-=sizeOf(it) }
        val size=sizeOf(value)
        if(size in 1..budget) { entries[key]=value;bytes+=size }
        while(bytes>budget) {
            val oldest=entries.keys.firstOrNull() ?: break
            bytes-=sizeOf(entries.remove(oldest)!!)
        }
    }
    fun usedBytes():Long=withMediaCacheLock { bytes }
}
