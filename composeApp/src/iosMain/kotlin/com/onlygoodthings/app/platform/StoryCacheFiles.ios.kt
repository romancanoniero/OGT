@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)
package com.onlygoodthings.app.platform

import kotlinx.cinterop.*
import platform.Foundation.*

private fun cachePath(key:String):String {
    require(key.matches(Regex("[a-zA-Z0-9_.-]+")))
    val base=NSSearchPathForDirectoriesInDomains(NSCachesDirectory,NSUserDomainMask,true).first() as String
    val dir="$base/story-playback"
    NSFileManager.defaultManager.createDirectoryAtPath(dir,true,null,null)
    return "$dir/$key"
}
internal actual fun readStoryCacheFile(key:String):ByteArray?=readLocalFileBytes(cachePath(key))
internal actual fun writeStoryCacheFile(key:String,bytes:ByteArray):String {
    require(bytes.isNotEmpty())
    val data=bytes.usePinned { NSData.create(bytes=it.addressOf(0),length=bytes.size.toULong()) }
    check(data.writeToFile(cachePath(key),true)) { "No se pudo guardar la historia" }
    return NSURL.fileURLWithPath(cachePath(key)).absoluteString!!
}
internal actual fun storyCacheFileUrl(key:String):String?=cachePath(key).takeIf { NSFileManager.defaultManager.fileExistsAtPath(it) }?.let { NSURL.fileURLWithPath(it).absoluteString }
internal actual fun deleteStoryCacheFile(key:String) { NSFileManager.defaultManager.removeItemAtPath(cachePath(key),null) }
internal actual fun clearStoryCacheFiles() { NSFileManager.defaultManager.removeItemAtPath(cachePath("index.json").substringBeforeLast('/'),null) }

internal actual fun pruneStoryCacheFiles(keep:Set<String>) {
    val dir=cachePath("index.json").substringBeforeLast('/')
    NSFileManager.defaultManager.contentsOfDirectoryAtPath(dir,null)?.filterIsInstance<String>()?.filter { it !in keep }?.forEach { NSFileManager.defaultManager.removeItemAtPath("$dir/$it",null) }
}
