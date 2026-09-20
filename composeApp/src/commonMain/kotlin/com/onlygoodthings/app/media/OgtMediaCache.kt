package com.onlygoodthings.app.media

import com.onlygoodthings.app.platform.localFilePath
import com.onlygoodthings.app.platform.readLocalFileBytes
import com.onlygoodthings.shared.data.createPlatformHttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsBytes
import io.ktor.http.isSuccess

/**
 * Cache de multimedia del río: memoria + disco.
 * La card muestra al toque si ya hay bytes (subida optimista).
 */
object OgtMediaCache {
    private const val MaxMemory = 16
    private val memory = mutableMapOf<String, ByteArray>()
    private val http by lazy { createPlatformHttpClient() }

    fun peek(url: String): ByteArray? {
        val key = url.trim()
        if (key.isBlank()) return null
        memory[key]?.let { return it }
        return readMediaCacheFile(diskName(key))?.also { putMemory(key, it) }
    }

    fun ingest(url: String) {
        val key = url.trim()
        if (key.isBlank() || peek(key) != null) return
        val path = localFilePath(key) ?: return
        readLocalFileBytes(path)?.let { put(key, it) }
    }

    fun alias(from: String, to: String) {
        val src = from.trim()
        val dest = to.trim()
        if (src.isBlank() || dest.isBlank() || src == dest) return
        val bytes = peek(src) ?: return
        val already = peek(dest)
        if (already != null && !already.contentEquals(bytes)) return
        put(dest, bytes)
    }

    fun put(url: String, bytes: ByteArray) {
        val key = url.trim()
        if (key.isBlank() || bytes.isEmpty()) return
        putMemory(key, bytes)
        runCatching { writeMediaCacheFile(diskName(key), bytes) }
    }

    suspend fun load(url: String): ByteArray? {
        val key = url.trim()
        if (key.isBlank()) return null
        peek(key)?.let { return it }
        localFilePath(key)?.let { path ->
            readLocalFileBytes(path)?.also { put(key, it) }?.let { return it }
        }
        if (!isRemoteUrl(key)) return null
        val bytes = runCatching {
            val response = http.get(key) {
                header("User-Agent", "OnlyGoodThings/0.1 (Compose Multiplatform; media)")
            }
            if (!response.status.isSuccess()) null else response.bodyAsBytes()
        }.getOrNull()
        if (bytes != null && bytes.isNotEmpty()) put(key, bytes)
        return bytes
    }

    private fun putMemory(key: String, bytes: ByteArray) {
        memory[key] = bytes
        if (memory.size > MaxMemory) {
            memory.keys.firstOrNull { it != key }?.let { memory.remove(it) }
        }
    }
}

fun isRemoteMediaUrl(url: String): Boolean = isRemoteUrl(url.trim())

private fun isRemoteUrl(url: String): Boolean =
    url.startsWith("http://") || url.startsWith("https://")

internal fun diskName(url: String): String {
    val tail = url.substringAfterLast('/').ifBlank { url.takeLast(48) }
    val safe = buildString(tail.length) {
        tail.forEach { ch ->
            append(if (ch.isLetterOrDigit() || ch == '.' || ch == '-' || ch == '_') ch else '_')
        }
    }.take(96)
    val stamp = url.hashCode().toUInt().toString(16)
    return "m${url.length.toString(16)}_${stamp}_$safe.bin"
}
