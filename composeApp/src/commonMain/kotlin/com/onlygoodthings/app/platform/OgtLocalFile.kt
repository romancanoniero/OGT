package com.onlygoodthings.app.platform

/** Lee bytes de un archivo local (path absoluto o file://). */
expect fun readLocalFileBytes(path: String): ByteArray?

fun localFilePath(url: String): String? {
    val raw = when {
        url.startsWith("file://") -> url.removePrefix("file://")
        url.startsWith("file:") -> url.removePrefix("file:")
        url.startsWith("/") -> url
        else -> null
    }
    return raw?.takeIf { it.isNotBlank() }
}

fun isLocalMediaUrl(url: String): Boolean = localFilePath(url) != null
