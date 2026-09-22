package com.onlygoodthings.app.media

import com.onlygoodthings.app.platform.localFilePath
import com.onlygoodthings.app.platform.readLocalFileBytes
import com.onlygoodthings.shared.data.createPlatformHttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsBytes
import io.ktor.http.isSuccess
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Cache de multimedia del feed: memoria + disco.
 * La card muestra al toque si ya hay bytes (subida optimista).
 * Las descargas remotas se deduplican para no picar la misma URL dos veces.
 */
object OgtMediaCache {
    private const val MaxMemory = 48
    private val memory = mutableMapOf<String, ByteArray>()
    private val memoryOrder = mutableListOf<String>()
    private val inflight = mutableMapOf<String, CompletableDeferred<ByteArray?>>()
    private val inflightLock = Mutex()
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

    /** Path local si ya está en disco (fotos y video). */
    fun cachedFileUrl(url: String): String? {
        val key = url.trim()
        if (key.isBlank()) return null
        if (key.startsWith("file://") || key.startsWith("/")) return key
        val path = mediaCacheFilePath(diskName(key))
        return if (readMediaCacheFile(diskName(key)) != null) "file://$path" else null
    }

    suspend fun cachedPlayableUrl(url: String): String {
        val key = url.trim()
        if (key.isBlank()) return key
        if (key.startsWith("file://") || key.startsWith("/")) return key
        load(key)
        return cachedFileUrl(key) ?: key
    }

    suspend fun load(url: String): ByteArray? {
        val key = url.trim()
        if (key.isBlank()) return null
        peek(key)?.let { return it }
        val mine = CompletableDeferred<ByteArray?>()
        val winner = inflightLock.withLock {
            inflight[key] ?: mine.also { inflight[key] = it }
        }
        if (winner !== mine) return winner.await()
        val bytes = runCatching { loadUncached(key) }.getOrNull()
        mine.complete(bytes)
        inflightLock.withLock {
            if (inflight[key] === mine) inflight.remove(key)
        }
        return bytes
    }

    suspend fun prefetch(urls: Iterable<String>) {
        val keys = urls.map { it.trim() }.filter { it.isNotBlank() }.distinct().take(24)
        if (keys.isEmpty()) return
        coroutineScope {
            keys.map { key -> async { load(key) } }.awaitAll()
        }
    }

    private suspend fun loadUncached(key: String): ByteArray? {
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
        memoryOrder.remove(key)
        memoryOrder.add(key)
        while (memory.size > MaxMemory) {
            val evict = memoryOrder.removeFirstOrNull() ?: break
            if (evict != key) memory.remove(evict)
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
