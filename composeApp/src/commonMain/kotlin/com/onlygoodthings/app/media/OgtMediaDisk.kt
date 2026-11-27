package com.onlygoodthings.app.media

/** Disco de la cache de fotos: sobrevive al tmp del picker. */
internal expect fun readMediaCacheFile(name: String): ByteArray?

internal expect fun writeMediaCacheFile(name: String, bytes: ByteArray)

internal expect fun mediaCacheFilePath(name: String): String

internal expect fun mediaCacheFileExists(name:String):Boolean
internal expect fun pruneMediaCacheFiles(maxBytes:Long)
internal expect fun <T> withMediaCacheLock(block:()->T):T
