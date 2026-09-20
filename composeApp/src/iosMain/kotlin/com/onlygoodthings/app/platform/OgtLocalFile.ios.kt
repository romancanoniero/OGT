package com.onlygoodthings.app.platform

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import platform.Foundation.NSData
import platform.Foundation.dataWithContentsOfFile
import platform.posix.memcpy

@OptIn(ExperimentalForeignApi::class)
actual fun readLocalFileBytes(path: String): ByteArray? {
    val file = localFilePath(path) ?: path
    val data = NSData.dataWithContentsOfFile(file) ?: return null
    val size = data.length.toInt()
    if (size <= 0) return null
    val out = ByteArray(size)
    out.usePinned { pinned ->
        memcpy(pinned.addressOf(0), data.bytes, data.length)
    }
    return out
}
