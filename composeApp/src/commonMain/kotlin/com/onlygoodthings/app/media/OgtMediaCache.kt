package com.onlygoodthings.app.media

import com.onlygoodthings.app.platform.localFilePath
import com.onlygoodthings.app.platform.readLocalFileBytes
import com.onlygoodthings.shared.data.createPlatformHttpClient
import com.onlygoodthings.shared.data.local.OgtMediaSources
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsBytes
import io.ktor.http.isSuccess
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Cache de multimedia del feed: memoria + disco.
 * La card muestra al toque si ya hay bytes (subida optimista).
 * Las descargas remotas se deduplican para no picar la misma URL dos veces.
 */
object OgtMediaCache {
    private const val MaxMemory = 48
    /** Tope del spinner: un host caído no puede dejar la card girando. */
    private const val MediaTimeoutMs = 6_000L
    /** Un mp4 corto no entra en 6 s. Si corta antes, no queda archivo para la segunda vez. */
    private const val VideoTimeoutMs = 45_000L
    private val memory = mutableMapOf<String, ByteArray>()
    private val memoryOrder = mutableListOf<String>()
    private val misses = mutableSetOf<String>()
    private val inflight = mutableMapOf<String, CompletableDeferred<ByteArray?>>()
    private val inflightLock = Mutex()
    private val http by lazy { createPlatformHttpClient() }

    fun peek(url: String): ByteArray? {
        val key = mediaKey(url)
        if (key.isBlank()) return null
        memory[key]?.let { return it }
        return readMediaCacheFile(diskName(key))?.also { putMemory(key, it) }
    }

    fun ingest(url: String) {
        val key = mediaKey(url)
        if (key.isBlank() || peek(key) != null) return
        val path = localFilePath(key) ?: return
        readLocalFileBytes(path)?.let { put(key, it) }
    }

    fun alias(from: String, to: String) {
        val src = mediaKey(from)
        val dest = mediaKey(to)
        if (src.isBlank() || dest.isBlank() || src == dest) return
        val bytes = peek(src) ?: return
        val already = peek(dest)
        if (already != null && !already.contentEquals(bytes)) return
        put(dest, bytes)
    }

    fun put(url: String, bytes: ByteArray) {
        val key = mediaKey(url)
        if (key.isBlank() || bytes.isEmpty()) return
        putMemory(key, bytes)
        runCatching { writeMediaCacheFile(diskName(key), bytes) }
    }

    /** Path local si ya está en disco (fotos y video). No vuelve a leer ni a bajar el archivo. */
    fun cachedFileUrl(url: String): String? {
        val key = mediaKey(url)
        if (key.isBlank()) return null
        if (key.startsWith("file://") || key.startsWith("/")) return key
        val name = diskName(key)
        if (!mediaCacheExists(name)) return null
        val path = mediaCacheFilePath(name)
        return if (path.isBlank()) null else "file://$path"
    }

    /**
     * Solo un archivo ya guardado. Si todavía no está, devuelve vacío:
     * el reproductor no tiene que abrir la URL remota.
     */
    suspend fun cachedPlayableUrl(url: String): String {
        val key = mediaKey(url)
        if (key.isBlank()) return ""
        if (key.startsWith("file://") || key.startsWith("/")) return key
        cachedFileUrl(key)?.let { return it }
        load(key)
        return cachedFileUrl(key).orEmpty()
    }

    /** Un pull-to-refresh puede volver a pedir fotos que antes no respondieron. */
    fun retryMisses() {
        if (!inflightLock.tryLock()) return
        try {
            misses.clear()
        } finally {
            inflightLock.unlock()
        }
    }

    suspend fun load(url: String): ByteArray? {
        val key = mediaKey(url)
        if (key.isBlank()) return null
        peek(key)?.let { return it }
        val skipped = inflightLock.withLock { key in misses }
        if (skipped) return null
        val mine = CompletableDeferred<ByteArray?>()
        val winner = inflightLock.withLock {
            inflight[key] ?: mine.also { inflight[key] = it }
        }
        if (winner !== mine) return winner.await()
        return try {
            val wait = if (isVideoUrl(key)) VideoTimeoutMs else MediaTimeoutMs
            val bytes = withTimeoutOrNull(wait) { loadUncached(key) }
            val stored = if (bytes != null && bytes.isNotEmpty()) bytes else null
            inflightLock.withLock {
                // Un video que no llegó se puede reintentar. Una foto caída, no.
                if (stored == null) {
                    if (!isVideoUrl(key)) misses.add(key)
                } else {
                    misses.remove(key)
                }
            }
            if (!mine.isCompleted) mine.complete(stored)
            stored
        } catch (cancel: CancellationException) {
            // Si el ganador muere, los que esperan reciben null y pueden reintentar.
            if (!mine.isCompleted) mine.complete(null)
            throw cancel
        } catch (_: Exception) {
            inflightLock.withLock { if (!isVideoUrl(key)) misses.add(key) }
            if (!mine.isCompleted) mine.complete(null)
            null
        } finally {
            withContext(NonCancellable) {
                inflightLock.withLock {
                    if (inflight[key] === mine) inflight.remove(key)
                }
            }
        }
    }

    suspend fun prefetch(urls: Iterable<String>) {
        val keys = urls.map { mediaKey(it) }.filter { it.isNotBlank() }.distinct().take(24)
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
        val bytes = try {
            val response = http.get(key) {
                header("User-Agent", "OnlyGoodThings/0.1 (Compose Multiplatform; media)")
            }
            if (!response.status.isSuccess()) null else response.bodyAsBytes()
        } catch (cancel: CancellationException) {
            throw cancel
        } catch (_: Exception) {
            null
        }
        if (bytes != null && bytes.isNotEmpty()) put(key, bytes)
        return bytes
    }

    private fun mediaKey(url: String): String = OgtMediaSources.currentUrl(canonicalMediaUrl(url))

    private fun isVideoUrl(url: String): Boolean {
        val path = url.substringBefore('?').lowercase()
        return path.endsWith(".mp4") || path.endsWith(".m4v") || path.endsWith(".mov") || path.endsWith(".webm")
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

fun isRemoteMediaUrl(url: String): Boolean = isRemoteUrl(canonicalMediaUrl(url))

/**
 * Las notas viejas guardan `http://217.216.82.209:19080/media/…`.
 * Ese puerto no está abierto desde afuera. El mismo archivo se sirve en el dominio público.
 */
fun canonicalMediaUrl(raw: String): String {
    val url = raw.trim()
    val schemeEnd = url.indexOf("://")
    if (schemeEnd <= 0) return url
    val rest = url.substring(schemeEnd + 3)
    val blocked = "217.216.82.209:19080"
    if (!rest.startsWith(blocked)) return url
    return "https://onlygoodthings.lat" + rest.removePrefix(blocked)
}

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
