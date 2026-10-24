package com.onlygoodthings.app.media

/** Disco de la cache de fotos y video: sobrevive al tmp del picker. */
internal expect fun readMediaCacheFile(name: String): ByteArray?

internal expect fun writeMediaCacheFile(name: String, bytes: ByteArray)

internal expect fun mediaCacheFilePath(name: String): String

/** El archivo ya está en disco. No hace falta leerlo entero para no volver a bajarlo. */
internal expect fun mediaCacheExists(name: String): Boolean
