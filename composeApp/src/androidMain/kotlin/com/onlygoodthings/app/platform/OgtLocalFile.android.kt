package com.onlygoodthings.app.platform

import java.io.File

actual fun readLocalFileBytes(path: String): ByteArray? {
    val file = File(localFilePath(path) ?: path)
    return runCatching { if (file.exists()) file.readBytes() else null }.getOrNull()
}
