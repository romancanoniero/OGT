package com.onlygoodthings.app.data

import androidx.compose.runtime.*
import com.onlygoodthings.app.auth.AuthPrefs
import com.onlygoodthings.app.platform.*
import com.onlygoodthings.shared.data.SessionStore
import com.onlygoodthings.shared.data.createPlatformHttpClient
import com.onlygoodthings.shared.data.remote.RestStoriesRepository
import com.onlygoodthings.shared.domain.*
import com.onlygoodthings.shared.realtime.*
import kotlinx.coroutines.*
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsBytes
import io.ktor.http.isSuccess
import kotlin.random.Random

class StoriesController(private val session: SessionStore, private val prefs: AuthPrefs, private val tokenProvider: suspend () -> String? = { session.firebaseJwt }) {
    private val http=createPlatformHttpClient()
    private val mediaCache=StoryMediaCache(fetch={ url ->
        val response=http.get(url)
        check(response.status.isSuccess()) { "Esta historia ya no está disponible" }
        response.bodyAsBytes()
    })
    suspend fun mediaSource(story:Story,poster:Boolean=false):String {
        val user=owner;val version=generation
        check(readable.any { it.id==story.id }) { "Esta historia ya no está disponible" }
        val url=mediaCache.resolve(user,story,poster)
        check(owner==user && generation==version && readable.any { it.id==story.id }) { "Esta historia ya no está disponible" }
        return url
    }
    suspend fun invalidateStoryMedia(story:Story) { mediaCache.invalidate(owner,story.id) }
    private suspend fun accountApi(user: String, version: Int): RestStoriesRepository {
        val token=tokenProvider() ?: error("Iniciá sesión para ver historias")
        if(owner!=user || generation!=version) throw CancellationException("Account changed")
        session.firebaseJwt=token
        return RestStoriesRepository(http,SessionStore().apply { apiBaseUrl=session.apiBaseUrl;firebaseJwt=token })
    }
    private var generation=0
    private val scope=CoroutineScope(SupervisorJob()+Dispatchers.Main)
    private var cacheClear: Job?=null
    private var cacheBinding: Job?=null
    private var accountJob: Job?=null
    private var refreshJob: Job?=null
    private var uploadJob: Job?=null
    private val draftStore=StoryDraftStore({ prefs.storyDraftsJson }, { prefs.storyDraftsJson=it })
    init { scope.launch { draftStore.failure.collect { if(it!=null) publishError="No se pudo guardar el borrador. Podés reintentar antes de cerrar la app." } } }
    private var owner=""
    private var offset=0L
    private var lastVerifiedAt=0L
    var now by mutableStateOf(currentEpochMs());private set
    var stories by mutableStateOf<List<Story>>(emptyList());private set
    var draft by mutableStateOf<StoryDraft?>(null);private set
    var loading by mutableStateOf(false);private set
    var publishing by mutableStateOf(false);private set
    var error by mutableStateOf<String?>(null);private set
    var publishError by mutableStateOf<String?>(null);private set
    var publishedId by mutableStateOf<String?>(null);private set
    val readable: List<Story> get() = if(currentEpochMs()-lastVerifiedAt>30_000) emptyList() else activeStories(stories,now)

    fun start(userId: String) {
        if(owner==userId && accountJob?.isActive==true) return
        clear(clearMedia=false);owner=userId
        draft=draftStore.get(owner)
        val cleanup=cacheClear
        cacheBinding=scope.launch { cleanup?.join();mediaCache.bind(userId) }
        accountJob=scope.launch {
            cacheBinding?.join()
            launch {
                while(isActive) {
                    now=currentEpochMs()+offset
                    stories=activeStories(stories,now)
                    delay(250)
                }
            }
            launch { while(isActive) { refresh();delay(10_000) } }
            launch {
                if(OgtSdk.isStarted()) runCatching { OgtRealtime().observeStoryChanges().collect { refresh() } }
            }
        }
    }
    fun refresh() {
        if(owner.isBlank() || refreshJob?.isActive==true) return
        val user=owner
        val version=generation
        refreshJob=scope.launch {
            loading=true
            try {
                cacheBinding?.join()
                val api=accountApi(user,version)
                var page=api.list()
                val rows=page.stories.toMutableList()
                while(page.nextCursor!=null && rows.size<2000) { page=api.list(page.nextCursor);rows+=page.stories }
                if(owner==user && generation==version) {
                    val fresh=activeStories(rows.distinctBy { it.id },page.serverNowEpochMs)
                    mediaCache.authorize(user,fresh,page.serverNowEpochMs)
                    if(owner!=user || generation!=version)return@launch
                    offset=page.serverNowEpochMs-currentEpochMs();now=page.serverNowEpochMs
                    stories=fresh
                    lastVerifiedAt=currentEpochMs();error=null
                    val pending=draft
                    if(!publishing && pending!=null && stories.any { it.id==pending.id }) {
                        updateDraft(null);publishedId=pending.id
                    }
                }
            } catch(cancel:CancellationException) { throw cancel }
            catch(failure:Exception) { if(owner==user && generation==version) error=failure.message ?: "No se pudieron cargar las historias" }
            finally { if(owner==user && generation==version) loading=false }
        }
    }
    fun choose(item: OgtPickedMedia) {
        val id=buildString {
            repeat(36) { index->append(if(index in listOf(8,13,18,23)) '-' else if(index==14) '4' else if(index==19) "89ab"[Random.nextInt(4)] else "0123456789abcdef"[Random.nextInt(16)]) }
        }
        updateDraft(StoryDraft(id,owner,item.path,item.kind,item.posterPath,prepared=item.preparedForUpload,sourcePath=item.sourcePath,audioMuted=item.audioMuted,unmutedPath=item.unmutedPath,frame=StoryFrame()))
        publishError=null;publishedId=null
    }
    fun updateDraft(value: StoryDraft?) {
        if(publishing || owner.isBlank()) return
        if(value!=null && value.ownerId!=owner) return
        draft=value
        draftStore.update(owner,value)
    }
    fun updateCaption(text:String) {
        require(text.length<=StoryRules.MAX_CAPTION)
        draft?.let { updateDraft(it.copy(caption=text)) }
    }
    fun updateAudience(audience:StoryAudience) {
        draft?.let { updateDraft(it.copy(audience=audience)) }
    }
    suspend fun flushDraft() { draftStore.flush() }
    fun publish() {
        val original=draft ?: return
        if(publishing || original.ownerId!=owner) return
        val user=owner
        val version=generation
        publishing=true;publishError=null
        uploadJob=scope.launch {
            try {
                val api=accountApi(user,version)
                val prepared=if(original.prepared) OgtPickedMedia(original.id,original.kind,original.path,true,original.posterPath)
                    else prepareStoryMedia(OgtPickedMedia(original.id,original.kind,original.path,true,original.posterPath))
                val readyDraft=original.copy(path=prepared.path,posterPath=prepared.posterPath,prepared=true)
                if(user!=owner || generation!=version) return@launch
                draft=readyDraft
                draftStore.update(user,readyDraft)
                draftStore.flush()
                val saved=withContext(Dispatchers.Default) {
                    val bytes=readLocalFileBytes(prepared.path) ?: error("La foto o el video ya no está en el dispositivo. Elegilo otra vez.")
                    require(bytes.size<=StoryRules.MAX_BYTES) { "El archivo supera 8 MB. Elegí un video más liviano." }
                    val poster=prepared.posterPath?.let { readLocalFileBytes(it) }
                    api.publish(readyDraft,bytes,poster)
                }
                if(user==owner && generation==version) {
                    publishing=false;updateDraft(null);publishedId=saved.id
                    stories=(listOf(saved)+stories).distinctBy { it.id }
                    mediaCache.authorize(user,stories,now)
                    lastVerifiedAt=currentEpochMs();refresh()
                }
            } catch(cancel:CancellationException) { throw cancel }
            catch(failure:Exception) { if(user==owner && generation==version) publishError=failure.message ?: "No se pudo publicar. Podés reintentar." }
            finally { if(user==owner && generation==version) publishing=false }
        }
    }
    fun seen(id: String) {
        if(stories.firstOrNull { it.id==id }?.seen!=false) return
        val user=owner
        val version=generation
        scope.launch {
            try {
                accountApi(user,version).seen(id)
                if(user==owner && generation==version) stories=stories.map { if(it.id==id) it.copy(seen=true) else it }
            } catch(cancel:CancellationException) { throw cancel }
            catch(_:Exception) { /* Leave unread so the next viewing retries. */ }
        }
    }
    suspend fun report(id:String,reason:String,details:String?) {
        val user=owner;val version=generation
        accountApi(user,version).report(id,reason,details)
    }
    fun remove(id: String) {
        val user=owner
        val version=generation
        scope.launch {
            try { accountApi(user,version).remove(id);if(user==owner && generation==version) { stories=stories.filterNot { it.id==id };mediaCache.authorize(user,stories,now);refresh() } }
            catch(cancel:CancellationException) { throw cancel }
            catch(failure:Exception) { if(user==owner && generation==version) error=failure.message ?: "No se pudo eliminar la historia" }
        }
    }
    fun clear(clearMedia:Boolean=true) {
        if(clearMedia) { val previous=cacheClear;cacheClear=scope.launch { previous?.join();mediaCache.clear() } }
        generation++
        cacheBinding?.cancel();accountJob?.cancel();refreshJob?.cancel();uploadJob?.cancel()
        owner="";stories=emptyList();draft=null;publishedId=null;lastVerifiedAt=0
        error=null;publishError=null;publishing=false;loading=false
    }
    fun close() { clear(clearMedia=false);draftStore.close();mediaCache.close();scope.cancel();http.close() }
}
