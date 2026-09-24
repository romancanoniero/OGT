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
        data.writeToFile("${cacheDir()}/$name", atomically = true)
    }
}

internal actual fun mediaCacheFilePath(name: String): String = "${cacheDir()}/$name"
