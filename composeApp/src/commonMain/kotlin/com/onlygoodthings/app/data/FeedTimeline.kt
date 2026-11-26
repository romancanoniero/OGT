package com.onlygoodthings.app.data

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.onlygoodthings.shared.data.local.LocalSocialPost
import com.onlygoodthings.shared.data.local.OgtLocalDatabase
import com.onlygoodthings.shared.domain.*
import com.onlygoodthings.shared.realtime.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class FeedQuery(val mode:FeedMode=FeedMode.HOME,val family:FeedTopicFamily?=null)
data class FeedPageState(
    val posts:List<LocalSocialPost> = emptyList(),
    val ready:Boolean=false,
    val refreshing:Boolean=false,
    val pendingIds:List<String> = emptyList(),
    val ownPublication:Int=0,
    val error:String?=null,
)

/** Presentation survives navigation, but never a change of authenticated account. */
class FeedPresentation {
    var mode by mutableStateOf(FeedMode.HOME)
    var family by mutableStateOf<FeedTopicFamily?>(null)
    val query get()=FeedQuery(mode,family)
    private val positions=mutableMapOf<FeedQuery,LazyListState>()
    private val acknowledgedOwn=mutableMapOf<FeedQuery,Int>()
    fun position(query:FeedQuery)=positions.getOrPut(query) { LazyListState() }
    fun acknowledgeOwn(query:FeedQuery,revision:Int):Boolean {
        val previous=acknowledgedOwn[query] ?: 0
        acknowledgedOwn[query]=revision
        return revision>previous
    }
}

sealed interface FeedSignal {
    data object Connected:FeedSignal
    data class Post(val value:SocialLiveCounters):FeedSignal
}

/** One account-owned timeline. Screens only read it; navigation never clears its content. */
class FeedTimeline(
    private val db:OgtLocalDatabase,
    private val prepare:suspend ()->Unit,
    private val load:suspend (FeedQuery)->List<SocialPost>,
    private val visible:suspend (List<String>)->List<String>,
    private val loadPost:suspend (String)->SocialPost,
    private val reconcile:(Set<String>,Set<String>)->Unit,
    private val persist:()->Unit,
    private val prefetch:suspend (List<String>)->Unit={},
    private val scope:CoroutineScope=CoroutineScope(SupervisorJob()+Dispatchers.Main),
    private val now:()->Long=::currentEpochMs,
    private val generations:Flow<Long> = OgtSdk.generation,
    private val transportStarted:()->Boolean=OgtSdk::isStarted,
    private val signals:()->Flow<FeedSignal> = {
        merge(OgtRealtime().observeSocialPosts().map { FeedSignal.Post(it) },
            OgtRealtime().observeFeedConnections().map { FeedSignal.Connected })
    },
) {
    private data class Entry(var order:List<String>,var latest:List<String>,var fetchedAt:Long?=null)
    private val entries=mutableMapOf<FeedQuery,Entry>()
    private val _pages=MutableStateFlow<Map<FeedQuery,FeedPageState>>(emptyMap())
    val pages:StateFlow<Map<FeedQuery,FeedPageState>> = _pages.asStateFlow()
    var presentation=FeedPresentation();private set
    private var owner=""
    private var generation=0
    private var initialized=false
    private var accountJob:Job?=null
    private var cacheJob:Job?=null
    private val requests=mutableMapOf<FeedQuery,Job>()
    private val refreshPending=mutableSetOf<FeedQuery>()
    private val revealPending=mutableSetOf<FeedQuery>()
    private var followed=emptySet<String>()
    private val hydrations=mutableMapOf<String,Job>()
    private val hydrationPending=mutableSetOf<String>()
    private val removed=mutableSetOf<String>()
    private val foreground=MutableStateFlow(true)
    private val validationMutex=Mutex()
    private var validatedAt:Long?=null

    fun cached(query:FeedQuery):FeedPageState = pages.value[query] ?: FeedPageState(
        posts=if(initialized) river(query,null) else emptyList(),ready=initialized,
    )
    private fun river(query:FeedQuery,order:List<String>?) =
        db.composeVisibleRiver(owner,query.mode,query.family,order).filter {
            it.id !in removed && (query.mode!=FeedMode.FOLLOWING || db.shouldShowInFeed(owner,it,query.mode,query.family))
        }
    private fun publish(query:FeedQuery,value:FeedPageState) {
        if(pages.value[query]!=value) _pages.value=pages.value+(query to value)
    }
    private fun entry(query:FeedQuery):Entry = entries.getOrPut(query) {
        val rows=river(query,null)
        publish(query,FeedPageState(if(initialized)rows else emptyList(),ready=initialized))
        Entry(rows.map { it.id },rows.map { it.id })
    }
    private fun rebuild() {
        entries.forEach { (query,entry) ->
            val previous=cached(query)
            val rows=if(previous.ready)river(query,entry.order) else emptyList()
            val pendingAllowed=if(previous.pendingIds.isEmpty())emptySet() else db.visibleFeed(owner,query.mode,query.family).map { it.id }.toSet()
            val ownAdded=previous.ready && rows.any { row ->
                db.isOwnPost(row,db.viewerAliases(owner)) && previous.posts.none { it.id==row.id }
            }
            publish(query,previous.copy(posts=rows,
                pendingIds=previous.pendingIds.filter { id ->
                    val post=db.posts.firstOrNull { it.id==id }
                    post!=null && id !in removed && rows.none { it.id==id } && id in pendingAllowed
                },
                ownPublication=previous.ownPublication+if(ownAdded)1 else 0))
        }
    }
    fun start(userId:String) {
        if(owner==userId && accountJob?.isActive==true)return
        clear();owner=userId;followed=db.followedIds(userId)
        if(userId.isNotBlank())db.bindSocialFeedViewer(userId)
        if(userId.isBlank())return
        val version=generation
        accountJob=scope.launch {
            launch {
                combine(db.feedTick,db.socialTick) { a,b -> a to b }.drop(1).collect {
                    val graph=db.followedIds(owner)
                    if(graph!=followed) {
                        followed=graph
                        entries.filterKeys { it.mode==FeedMode.FOLLOWING }.values.forEach { it.fetchedAt=null }
                    }
                    rebuild();schedulePersistence(userId,version)
                }
            }
            launch {
                combine(generations,foreground) { transport,active -> transport to active }.collectLatest { (_,active) ->
                    var attempt=0
                    while(active && isActive && transportStarted()) {
                        try {
                            signals().collect { signal ->
                                attempt=0
                                when(signal) {
                                    FeedSignal.Connected -> {
                                        validatedAt=null
                                        val query=presentation.query
                                        if(entries.containsKey(query)) refresh(query,force=true)
                                    }
                                    is FeedSignal.Post -> applyLive(signal.value)
                                }
                            }
                        } catch(cancel:CancellationException) { throw cancel }
                        catch(_:Exception) { delay(storyRetryDelay(attempt++)) }
                    }
                }
            }
        }
    }
    fun localChanged() {
        if(owner.isBlank())return
        rebuild();schedulePersistence(owner,generation)
    }
    fun setForeground(active:Boolean) {
        if(foreground.value==active)return
        foreground.value=active
        if(!active && owner.isNotBlank() && initialized)persist()
        if(active && entries.containsKey(presentation.query))refresh(presentation.query,force=true)
    }
    private fun current(user:String,version:Int)=owner==user && generation==version
    private fun schedulePersistence(user:String,version:Int) {
        cacheJob?.cancel()
        cacheJob=scope.launch { delay(200);if(current(user,version))persist() }
    }
    private suspend fun validate(user:String,version:Int) = validationMutex.withLock {
        val last=validatedAt
        if(last!=null && now()-last in 0 until 30_000)return@withLock
        val serverId=Regex("[0-9a-fA-F]{8}(-[0-9a-fA-F]{4}){3}-[0-9a-fA-F]{12}")
        var complete=true
        db.feedPosts().map { it.id }.filter(serverId::matches).chunked(100).forEach { ids ->
            try {
                val allowed=visible(ids).toSet()
                if(!current(user,version))throw CancellationException("Account changed")
                removed+=ids.filter { it !in allowed }
                reconcile(ids.toSet(),allowed) // Persist confirmed removals before the next request.
            } catch(cancel:CancellationException) { throw cancel }
            catch(_:Exception) { complete=false } // Keep offline cache and older-server compatibility.
        }
        if(complete)validatedAt=now()
        rebuild()
    }
    fun refresh(query:FeedQuery,force:Boolean=false,reveal:Boolean=false):Job? {
        if(owner.isBlank())return null
        val entry=entry(query)
        rebuild()
        requests[query]?.takeIf { it.isActive }?.let {
            if(force)refreshPending+=query
            if(reveal)revealPending+=query
            return it
        }
        if(!force && entry.fetchedAt?.let { now()-it in 0 until 30_000 }==true)return null
        val user=owner;val version=generation
        val job=scope.launch(start=CoroutineStart.LAZY) {
            publish(query,cached(query).copy(refreshing=true))
            try {
                prepare()
                if(!current(user,version))return@launch
                validate(user,version)
                val before=db.posts.associateBy { it.id }
                val mediaBefore=db.postMedia.groupBy { it.postId }.mapValues { (_,rows)->rows.sortedBy { it.sortOrder } }
                val removedBefore=removed.toSet()
                val remote=load(query)
                currentCoroutineContext().ensureActive()
                if(!current(user,version))return@launch
                val accepted=remote.filter { it.id !in removed || it.id in removedBefore }
                accepted.forEach { post ->
                    val existing=db.posts.firstOrNull { it.id==post.id }
                    // A late response cannot undo a reaction, edit, deletion or socket update.
                    if(existing==before[post.id] && db.mediaOf(post.id)==mediaBefore[post.id].orEmpty()) {
                        removed.remove(post.id);db.upsertRemoteSocial(post,silent=true)
                    }
                }
                val old=cached(query)
                val ids=accepted.map { it.id }.filter { id -> db.posts.any { it.id==id } }
                entry.latest=ids
                if(entry.fetchedAt==null || !old.ready || old.posts.isEmpty() || reveal)entry.order=ids
                else if(query.mode==FeedMode.FOLLOWING)entry.order=entry.order.filter { it in ids }
                initialized=true;entry.fetchedAt=now()
                publish(query,old.copy(ready=true,refreshing=false,error=null,
                    pendingIds=if(reveal)emptyList() else (old.pendingIds+ids.filter { it !in entry.order }).distinct()))
                rebuild();persist()
                // Wake details/screens observing the same database after a silent batch merge.
                db.bumpSocial()
                try { prefetch(accepted.flatMap { post -> post.media.flatMap { listOfNotNull(it.url,it.posterUrl) }+post.mediaUrls }) }
                catch(cancel:CancellationException) { throw cancel }
                catch(_:Exception) { /* Media loading retries independently from feed revalidation. */ }
            } catch(cancel:CancellationException) { throw cancel }
            catch(_:Exception) {
                if(current(user,version)) {
                    initialized=true
                    publish(query,cached(query).copy(ready=true,error="No se pudo actualizar. Mostramos el contenido guardado."))
                    rebuild()
                }
            } finally {
                if(current(user,version)) {
                    publish(query,cached(query).copy(refreshing=false))
                    requests.remove(query)
                    val again=refreshPending.remove(query)
                    val revealAgain=revealPending.remove(query)
                    if(again)refresh(query,force=true,reveal=revealAgain)
                }
            }
        }
        requests[query]=job;job.start();return job
    }
    fun reveal(query:FeedQuery) {
        val entry=entry(query);val page=cached(query)
        entry.order=(page.pendingIds+entry.latest+entry.order).distinct()
        publish(query,page.copy(pendingIds=emptyList()));rebuild()
    }
    private fun applyLive(live:SocialLiveCounters) {
        if(!live.moderationStatus.equals("VISIBLE",true)) {
            removed+=live.id;hydrations.remove(live.id)?.cancel();hydrationPending.remove(live.id)
            reconcile(setOf(live.id),emptySet());rebuild();return
        }
        val before=db.posts.firstOrNull { it.id==live.id }
        val needsContent=before==null || (live.body!=null && live.body!=before.body) ||
            (live.tag!=null && live.tag!=before.tag)
        if(before!=null) { db.applySocialLive(live);rebuild() }
        // Unknown posts are admitted only after authenticated REST hydration (including media).
        if(initialized && (needsContent || live.id in removed))hydrate(live.id)
    }
    private fun hydrate(id:String) {
        hydrationPending+=id
        if(hydrations[id]?.isActive==true)return
        val user=owner;val version=generation
        val job=scope.launch(start=CoroutineStart.LAZY) {
            try {
                do {
                    hydrationPending.remove(id)
                    prepare()
                    val before=db.posts.firstOrNull { it.id==id }
                    val post=loadPost(id)
                    currentCoroutineContext().ensureActive()
                    if(!current(user,version))return@launch
                    if(db.posts.firstOrNull { it.id==id }!=before)continue
                    removed.remove(id);db.upsertRemoteSocial(post,silent=true)
                    entries.forEach { (query,entry) ->
                        val page=cached(query)
                        val row=db.posts.first { it.id==id }
                        if(page.ready && id !in entry.order && db.shouldShowInFeed(owner,row,query.mode,query.family)) {
                            publish(query,page.copy(pendingIds=(listOf(id)+page.pendingIds).distinct()))
                        }
                    }
                    rebuild();persist();db.bumpSocial()
                } while(id in hydrationPending)
            } catch(cancel:CancellationException) { throw cancel }
            catch(_:Exception) { /* A denied/offline post is never inserted; re-entry can reconcile. */ }
            finally {
                if(current(user,version) && hydrations[id]===currentCoroutineContext()[Job])hydrations.remove(id)
            }
        }
        hydrations[id]=job;job.start()
    }
    fun clear() {
        generation++;accountJob?.cancel();cacheJob?.cancel()
        requests.values.forEach { it.cancel() };hydrations.values.forEach { it.cancel() }
        requests.clear();refreshPending.clear();revealPending.clear();hydrations.clear();hydrationPending.clear();removed.clear();entries.clear()
        _pages.value=emptyMap();owner="";initialized=false;validatedAt=null;presentation=FeedPresentation()
    }
    fun close() { clear();scope.cancel() }
}
