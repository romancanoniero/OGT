package com.onlygoodthings.app.media

import com.onlygoodthings.app.platform.localFilePath
import com.onlygoodthings.app.platform.readLocalFileBytes
import com.onlygoodthings.shared.data.createPlatformHttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsBytes
import io.ktor.http.isSuccess
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.sync.withLock

/**
 * Cache de multimedia del feed: memoria + disco.
 * La card muestra al toque si ya hay bytes (subida optimista).
 * Las descargas remotas se deduplican para no picar la misma URL dos veces.
 */
object OgtMediaCache {
    private val memory = MediaMemoryCache<ByteArray>(24L*1024*1024) { it.size.toLong() }
    private val io=CoroutineScope(SupervisorJob()+Dispatchers.Default)
    private val network=Semaphore(4) // Three background workers leave room for a visible image.
    private val diskLock=Mutex()
    private var writes=0
    private val inflight = mutableMapOf<String, Deferred<ByteArray?>>()
    private val inflightLock = Mutex()
    private val http by lazy { createPlatformHttpClient() }
    private val downloads by lazy { MediaPrefetchQueue(io) { key ->
        if(cachedFileUrl(key)==null)load(key)
    } }

    fun peek(url: String): ByteArray? {
        val key = canonicalMediaUrl(url)
        if (key.isBlank()) return null
        return memory.get(key)
    }

    fun ingest(url: String) {
        val key = canonicalMediaUrl(url)
        if (key.isBlank() || peek(key) != null) return
        val path = localFilePath(key) ?: return
        io.launch { readLocalFileBytes(path)?.let { save(key,it) } }
    }

    fun alias(from: String, to: String) {
        val src = canonicalMediaUrl(from)
        val dest = canonicalMediaUrl(to)
        if (src.isBlank() || dest.isBlank() || src == dest) return
        peek(src)?.let { bytes ->
            val already=peek(dest)
            if(already==null || already.contentEquals(bytes))put(dest,bytes)
            return
        }
        io.launch {
            val bytes=load(src) ?: return@launch
            val already=loadLocal(dest)
            if(already==null || already.contentEquals(bytes))save(dest,bytes)
        }
    }

    fun put(url: String, bytes: ByteArray) {
        val key = canonicalMediaUrl(url)
        if (key.isBlank() || bytes.isEmpty()) return
        putMemory(key, bytes)
        io.launch { runCatching { save(key,bytes) } }
    }

    /** Path local si ya está en disco (fotos y video). */
    fun cachedFileUrl(url: String): String? {
        val key = canonicalMediaUrl(url)
        if (key.isBlank()) return null
        if (key.startsWith("file://") || key.startsWith("/")) return key
        val path = mediaCacheFilePath(diskName(key))
        return if (mediaCacheFileExists(diskName(key))) "file://$path" else null
    }

    suspend fun cachedPlayableUrl(url: String): String {
        val key = canonicalMediaUrl(url)
        if (key.isBlank()) return key
        if (key.startsWith("file://") || key.startsWith("/")) return key
        if(cachedFileUrl(key)==null)load(key)
        return cachedFileUrl(key) ?: key
    }

    internal fun readCachedFile(
        key: String,
        readFile: (String) -> ByteArray? = ::readMediaCacheFile,
        writeFile: (String, ByteArray) -> Unit = ::writeMediaCacheFile,
    ): ByteArray? {
        val name = diskName(key)
        readFile(name)?.let { return it }
        val legacy = legacyDiskName(key)
        if (legacy == name) return null
        // Preserve existing cached downloads, giving AVFoundation a supported extension.
        val bytes = readFile(legacy) ?: return null
        return runCatching { writeFile(name, bytes); readFile(name) }.getOrNull()
    }

    suspend fun load(url: String): ByteArray? = withContext(Dispatchers.Default) {
        val key = canonicalMediaUrl(url)
        if (key.isBlank()) return@withContext null
        peek(key)?.let { return@withContext it }
        val winner = inflightLock.withLock {
            inflight[key] ?: io.async(start=CoroutineStart.LAZY) {
                try { loadUncached(key) }
                catch(cancel:CancellationException) { throw cancel }
                catch(_:Exception) { null }
                finally { withContext(NonCancellable) { inflightLock.withLock { inflight.remove(key) } } }
            }.also { inflight[key]=it }
        }
        // A disposed cell stops waiting, while the shared background download continues.
        winner.await()
    }

    /** Starts every known file, including hidden carousel pages, without holding feed refresh open. */
    fun enqueue(urls:Iterable<String>) {
        downloads.enqueue(urls.map(::canonicalMediaUrl).filter(::isRemoteUrl).distinct())
    }
    suspend fun prefetch(urls:Iterable<String>) {
        enqueue(urls);downloads.awaitIdle()
    }

    private fun loadLocal(key:String):ByteArray? = peek(key) ?: readCachedFile(key)

    private suspend fun save(key:String,bytes:ByteArray) {
        putMemory(key,bytes)
        diskLock.withLock {
            writeMediaCacheFile(diskName(key),bytes)
            if(++writes%16==0)pruneMediaCacheFiles(512L*1024*1024)
        }
    }

    private suspend fun loadUncached(key: String): ByteArray? {
        loadLocal(key)?.let { putMemory(key,it);return it }
        localFilePath(key)?.let { path ->
            readLocalFileBytes(path)?.also { save(key, it) }?.let { return it }
        }
        if (!isRemoteUrl(key)) return null
        val bytes = network.withPermit {
            val (requestUrl,bearer)=StoreReviewMedia.request(key)
            val response = http.get(requestUrl) {
                header("User-Agent", "OnlyGoodThings/0.1 (Compose Multiplatform; media)")
                bearer?.let { header("Authorization","Bearer $it") }
            }
            // Never persist a range response as if it were a complete playable file.
            if (response.status.value!=200) null else response.bodyAsBytes().takeIf { it.isNotEmpty() && it.size<=100*1024*1024 }
        }
        if (bytes != null)runCatching { save(key, bytes) }
        return bytes
    }

    private fun putMemory(key: String, bytes: ByteArray) {
        if(!key.substringBefore('?').substringBefore('#').endsWith(".mp4",true) && !key.substringBefore('?').substringBefore('#').endsWith(".webm",true))memory.put(key,bytes)
    }
}

fun isRemoteMediaUrl(url: String): Boolean = isRemoteUrl(canonicalMediaUrl(url))

/**
 * A small number of historical posts still refer to the old internal OGT
 * backend address, which is not reachable from a user's phone. Rewrite only
 * those media URLs to the current public media proxy; leave all other hosts
 * and routes untouched.
 */
fun canonicalMediaUrl(raw: String): String {
    val url = raw.trim()
    val privateOrigin = when {
        url.startsWith("http://217.216.82.209:19080/media/") -> "http://217.216.82.209:19080"
        url.startsWith("https://217.216.82.209:19080/media/") -> "https://217.216.82.209:19080"
        else -> return url
    }
    return "https://onlygoodthings.lat" + url.removePrefix(privateOrigin)
}

private fun isRemoteUrl(url: String): Boolean =
    url.startsWith("http://") || url.startsWith("https://")

internal fun diskName(url: String): String {
    val legacy = legacyDiskName(url)
    val extension = url.substringBefore('#').substringBefore('?').substringAfterLast('.').lowercase()
    return if (extension == "mp4" || extension == "webm") legacy.removeSuffix(".bin") + "." + extension else legacy
}

internal fun legacyDiskName(url: String): String {
    val tail = url.substringAfterLast('/').ifBlank { url.takeLast(48) }
    val safe = buildString(tail.length) {
        tail.forEach { ch ->
            append(if (ch.isLetterOrDigit() || ch == '.' || ch == '-' || ch == '_') ch else '_')
        }
    }.take(96)
    val stamp = url.hashCode().toUInt().toString(16)
    return "m${url.length.toString(16)}_${stamp}_$safe.bin"
}
