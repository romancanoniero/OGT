package com.onlygoodthings.app.media

import com.onlygoodthings.app.OgtApplication
import java.io.File

private fun cacheDir(): File? = runCatching {
    File(OgtApplication.instance.cacheDir, "ogt-media-cache").apply { mkdirs() }
}.getOrNull()

internal actual fun readMediaCacheFile(name: String): ByteArray? {
    val file = cacheDir()?.resolve(name) ?: return null
    return runCatching { if (file.exists()) file.readBytes() else null }.getOrNull()
}

internal actual fun writeMediaCacheFile(name: String, bytes: ByteArray) {
    if (bytes.isEmpty()) return
    val file = cacheDir()?.resolve(name) ?: return
    runCatching { file.writeBytes(bytes) }
}
