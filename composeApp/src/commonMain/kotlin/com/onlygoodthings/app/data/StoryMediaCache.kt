package com.onlygoodthings.app.data

import com.onlygoodthings.app.platform.*
import com.onlygoodthings.shared.domain.*
import com.onlygoodthings.shared.realtime.currentEpochMs
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable internal data class CachedStoryMedia(val id:String,val source:String,val expires:Long,val bytes:Int,val used:Long,val key:String)
@Serializable private data class StoryCacheIndex(val owner:String,val entries:List<CachedStoryMedia>)
internal const val STORY_CACHE_BYTES=128L*1024*1024
/** LRU plus hard expiry. Retention is restricted to the latest authorized server list. */
internal fun retainedStoryMedia(entries:List<CachedStoryMedia>,allowed:Set<String>,now:Long,budget:Long=STORY_CACHE_BYTES):List<CachedStoryMedia> {
    var size=0L
    return entries.filter { it.id in allowed && it.expires>now && it.bytes>0 }.sortedByDescending { it.used }.filter { if(size+it.bytes<=budget) { size+=it.bytes;true } else false }
}

/** Media only: cached bytes never grant permission to display a story. */
internal class StoryMediaCache(private val fetch:suspend (String)->ByteArray,private val downloadTimeoutMs:Long=30_000) {
    private val lock=Mutex()
    private val downloads=CoroutineScope(SupervisorJob()+Dispatchers.Default)
    private data class Pending(val id:String,val token:Any,val task:Deferred<String>)
    private val pending=mutableMapOf<String,Pending>()
    private var generation=0
    private val json=Json { ignoreUnknownKeys=true }
    private var owner=""
    private var entries=listOf<CachedStoryMedia>()
    private var allowed=setOf<String>()
    suspend fun bind(user:String)=withContext(Dispatchers.Default) { lock.withLock {
        generation++;pending.values.forEach { it.task.cancel() };pending.clear()
        val stored=runCatching { readStoryCacheFile("index.json")?.decodeToString()?.let { json.decodeFromString<StoryCacheIndex>(it) } }.getOrNull()
        if(stored?.owner!=user) clearStoryCacheFiles()
        owner=user;allowed=emptySet();entries=stored?.takeIf { it.owner==user }?.entries.orEmpty()
        pruneStoryCacheFiles(entries.map { it.key }.toSet()+"index.json")
    } }
    suspend fun authorize(user:String,stories:List<Story>,now:Long)=withContext(Dispatchers.Default) { lock.withLock {
        if(owner!=user)return@withLock
        allowed=stories.map { it.id }.toSet()
        pending.filterValues { it.id !in allowed }.values.forEach { it.task.cancel() }
        prune(now)
    } }
    private fun save() { runCatching { writeStoryCacheFile("index.json",json.encodeToString(StoryCacheIndex(owner,entries)).encodeToByteArray()) } }
    private fun prune(now:Long) {
        val keep=retainedStoryMedia(entries,allowed,now)
        (entries-keep.toSet()).forEach { deleteStoryCacheFile(it.key) };entries=keep;save()
    }
    suspend fun resolve(user:String,story:Story,poster:Boolean=false):String=withContext(Dispatchers.Default) {
        val result=lock.withLock {
            check(owner==user && story.id in allowed && story.expiresAtEpochMs>currentEpochMs()) { "Esta historia ya no está disponible" }
            val remote=(if(poster)story.posterUrl else story.mediaUrl) ?: error("Sin imagen de portada")
            val source=remote.substringBefore('?')
            val hit=entries.firstOrNull { it.id==story.id && it.source==source }
            val local=hit?.let { storyCacheFileUrl(it.key) }
            if(local!=null) {
                entries=entries.map { if(it.key==hit.key)it.copy(used=currentEpochMs()) else it };save()
                return@withLock local to null
            }
            val key="${story.id.filter { it.isLetterOrDigit() || it=='-' }}-${if(poster)"poster.jpg" else if(story.kind==MediaKind.VIDEO)"video.mp4" else "image.jpg"}"
            val requestKey=key+"|"+source
            pending[requestKey]?.takeIf { !it.task.isCancelled }?.let { return@withLock null to it.task }
            val version=generation;val ticket=Any()
            val task=downloads.async(start=CoroutineStart.LAZY) {
                try {
                    val bytes=try { withTimeout(downloadTimeoutMs) { fetch(remote) } }
                    catch(timeout:TimeoutCancellationException) { throw IllegalStateException("La descarga tardó demasiado. Volvé a intentarlo.",timeout) }
                    check(bytes.isNotEmpty() && bytes.size<=12*1024*1024) { "Archivo de historia inválido" }
                    lock.withLock {
                        currentCoroutineContext().ensureActive()
                        check(owner==user && generation==version && story.id in allowed && story.expiresAtEpochMs>currentEpochMs()) { "Esta historia ya no está disponible" }
                        val url=writeStoryCacheFile(key,bytes)
                        entries=entries.filterNot { it.key==key }+CachedStoryMedia(story.id,source,story.expiresAtEpochMs,bytes.size,currentEpochMs(),key)
                        prune(currentEpochMs());url
                    }
                } finally { withContext(NonCancellable) { lock.withLock { if(pending[requestKey]?.token===ticket)pending.remove(requestKey) } } }
            }
            pending[requestKey]=Pending(story.id,ticket,task)
            null to task
        }
        result.first ?: requireNotNull(result.second).await()
    }
    suspend fun invalidate(user:String,id:String)=withContext(Dispatchers.Default) { lock.withLock {
        if(owner!=user)return@withLock
        pending.filterValues { it.id==id }.values.forEach { it.task.cancel() }
        entries.filter { it.id==id }.forEach { deleteStoryCacheFile(it.key) };entries=entries.filterNot { it.id==id };save()
    } }
    suspend fun clear()=withContext(Dispatchers.Default) { lock.withLock {
        generation++;pending.values.forEach { it.task.cancel() };pending.clear()
        owner="";entries=emptyList();allowed=emptySet();clearStoryCacheFiles()
    } }
    fun close() { downloads.cancel() }
}
