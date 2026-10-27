package com.onlygoodthings.app.platform

/** Private OS cache directory; never camera roll, shared storage or device backups. */
internal expect fun readStoryCacheFile(key:String):ByteArray?
internal expect fun writeStoryCacheFile(key:String,bytes:ByteArray):String
internal expect fun storyCacheFileUrl(key:String):String?
internal expect fun deleteStoryCacheFile(key:String)
internal expect fun clearStoryCacheFiles()

internal expect fun pruneStoryCacheFiles(keep:Set<String>)
