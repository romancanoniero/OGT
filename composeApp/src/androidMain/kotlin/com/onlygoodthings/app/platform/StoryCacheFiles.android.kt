package com.onlygoodthings.app.platform

import com.onlygoodthings.app.OgtApplication
import java.io.File

private fun cacheFile(key:String):File {
    require(key.matches(Regex("[a-zA-Z0-9_.-]+")))
    return File(File(OgtApplication.instance.cacheDir,"story-playback").apply { mkdirs() },key)
}
internal actual fun readStoryCacheFile(key:String):ByteArray?=cacheFile(key).takeIf { it.isFile }?.readBytes()
internal actual fun writeStoryCacheFile(key:String,bytes:ByteArray):String {
    val file=cacheFile(key);val partial=cacheFile("$key.part");partial.writeBytes(bytes)
    check(partial.renameTo(file)) { "No se pudo guardar la historia" };return android.net.Uri.fromFile(file).toString()
}
internal actual fun storyCacheFileUrl(key:String):String?=cacheFile(key).takeIf { it.isFile }?.let { android.net.Uri.fromFile(it).toString() }
internal actual fun deleteStoryCacheFile(key:String) { cacheFile(key).delete() }
internal actual fun clearStoryCacheFiles() { File(OgtApplication.instance.cacheDir,"story-playback").deleteRecursively() }

internal actual fun pruneStoryCacheFiles(keep:Set<String>) {
    File(OgtApplication.instance.cacheDir,"story-playback").listFiles()?.filter { it.name !in keep }?.forEach { it.delete() }
}
