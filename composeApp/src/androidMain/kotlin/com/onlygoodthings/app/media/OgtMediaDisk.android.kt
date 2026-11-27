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
    val partial=File(file.parentFile,"${file.name}.part")
    partial.writeBytes(bytes)
    check(partial.renameTo(file)) { "No se pudo guardar la caché multimedia" }
}

internal actual fun mediaCacheFilePath(name: String): String =
    cacheDir()?.resolve(name)?.absolutePath.orEmpty()

internal actual fun mediaCacheFileExists(name:String):Boolean = cacheDir()?.resolve(name)?.let { it.isFile && it.length()>0 } ?: false
internal actual fun pruneMediaCacheFiles(maxBytes:Long) {
    val files=cacheDir()?.listFiles()?.filter { it.isFile && !it.name.endsWith(".part") }?.sortedByDescending { it.lastModified() }.orEmpty()
    var kept=0L
    for(file in files) { if(kept+file.length()<=maxBytes)kept+=file.length() else file.delete() }
}
private val mediaLock=Any()
internal actual fun <T> withMediaCacheLock(block:()->T):T=synchronized(mediaLock,block)
