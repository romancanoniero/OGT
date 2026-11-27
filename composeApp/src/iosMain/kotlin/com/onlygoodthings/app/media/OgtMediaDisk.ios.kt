@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)
package com.onlygoodthings.app.media

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import platform.Foundation.NSCachesDirectory
import platform.Foundation.NSData
import platform.Foundation.NSFileManager
import platform.Foundation.NSSearchPathForDirectoriesInDomains
import platform.Foundation.NSUserDomainMask
import platform.Foundation.create
import platform.Foundation.dataWithContentsOfFile
import platform.Foundation.writeToFile
import platform.Foundation.NSLock
import platform.Foundation.NSFileSize
import platform.Foundation.NSFileModificationDate
import platform.Foundation.NSDate
import platform.Foundation.timeIntervalSince1970
import platform.posix.memcpy

@OptIn(ExperimentalForeignApi::class)
private fun cacheDir(): String {
    val root = (NSSearchPathForDirectoriesInDomains(NSCachesDirectory, NSUserDomainMask, true).firstOrNull() as? String)
        ?: platform.Foundation.NSTemporaryDirectory()
    val dir = "$root/ogt-media-cache"
    NSFileManager.defaultManager.createDirectoryAtPath(dir, true, null, null)
    return dir
}

@OptIn(ExperimentalForeignApi::class)
internal actual fun readMediaCacheFile(name: String): ByteArray? {
    val data = NSData.dataWithContentsOfFile("${cacheDir()}/$name") ?: return null
    val size = data.length.toInt()
    if (size <= 0) return null
    val out = ByteArray(size)
    out.usePinned { pinned ->
        memcpy(pinned.addressOf(0), data.bytes, data.length)
    }
    return out
}

@OptIn(ExperimentalForeignApi::class)
internal actual fun writeMediaCacheFile(name: String, bytes: ByteArray) {
    if (bytes.isEmpty()) return
    bytes.usePinned { pinned ->
        val data = NSData.create(bytes = pinned.addressOf(0), length = bytes.size.toULong())
        check(data.writeToFile("${cacheDir()}/$name", atomically = true)) { "No se pudo guardar la caché multimedia" }
    }
}

internal actual fun mediaCacheFilePath(name: String): String = "${cacheDir()}/$name"

internal actual fun mediaCacheFileExists(name:String):Boolean =
    (NSFileManager.defaultManager.attributesOfItemAtPath(mediaCacheFilePath(name),null)?.get(NSFileSize) as? Number)?.toLong()?.let { it>0 } ?: false

internal actual fun pruneMediaCacheFiles(maxBytes:Long) {
    val dir=cacheDir();val manager=NSFileManager.defaultManager
    val files=manager.contentsOfDirectoryAtPath(dir,null)?.filterIsInstance<String>()?.filterNot { it.endsWith(".part") }?.mapNotNull { name ->
        val attributes=manager.attributesOfItemAtPath("$dir/$name",null) ?: return@mapNotNull null
        Triple(name,(attributes[NSFileSize] as? Number)?.toLong() ?: 0L,(attributes[NSFileModificationDate] as? NSDate)?.timeIntervalSince1970 ?: 0.0)
    }?.sortedByDescending { it.third }.orEmpty()
    var kept=0L
    for(file in files) { if(kept+file.second<=maxBytes)kept+=file.second else manager.removeItemAtPath("$dir/${file.first}",null) }
}
private val mediaLock=NSLock()
internal actual fun <T> withMediaCacheLock(block:()->T):T {
    mediaLock.lock();return try { block() } finally { mediaLock.unlock() }
}
