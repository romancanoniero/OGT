package com.onlygoodthings.app.media

/** Disco de la cache de fotos: sobrevive al tmp del picker. */
internal expect fun readMediaCacheFile(name: String): ByteArray?

internal expect fun writeMediaCacheFile(name: String, bytes: ByteArray)
