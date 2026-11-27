package com.onlygoodthings.app.data

import com.onlygoodthings.shared.data.local.*
import com.onlygoodthings.shared.domain.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlin.test.*

class FeedTimelineTest {
    private val home=FeedQuery()
    private fun id(n:Int)="00000000-0000-4000-8000-"+n.toString().padStart(12,'0')
    private fun post(n:Int,author:String="neighbor",count:Int=0)=SocialPost(
        id(n),AuthorKind.USER,author,"Nombre completo",body="Acción $n",mediaUrls=emptyList(),
        impactCount=count,commentCount=0,isStory=false,createdAtEpochMs=1,topic="Reforestación",
    )
    private inner class Fixture {
        val db=OgtLocalDatabase()
        val events=MutableSharedFlow<FeedSignal>(extraBufferCapacity=20)
        val transport=MutableStateFlow(0L)
        val scope=CoroutineScope(SupervisorJob()+Dispatchers.Unconfined)
        var tick=100_000L
        var requests=0
        var openings=0
        var saved=""
        var rows=listOf(post(1))
        var fetch:suspend (FeedQuery)->List<SocialPost> = { rows }
        var visibility:suspend (List<String>)->List<String> = { it }
        var get:suspend (String)->SocialPost = { requested -> rows.first { it.id==requested } }
        val timeline=FeedTimeline(db,prepare={},load={requests++;fetch(it)},visible={visibility(it)},loadPost={get(it)},
            reconcile={checked,allowed -> db.reconcilePostVisibility(checked,allowed);save()},persist=::save,
            scope=scope,now={tick},generations=transport,transportStarted={true},signals={ flow { openings++;emitAll(events) } })
        fun save() { saved=db.exportSocialFeed("viewer") }
        suspend fun start() {
            timeline.start("viewer")
            withTimeout(2000) { events.subscriptionCount.first { it>0 } }
        }
        suspend fun load(query:FeedQuery=home) { timeline.refresh(query)?.join() }
        suspend fun signal(value:SocialLiveCounters) { events.emit(FeedSignal.Post(value));yield() }
        fun page(query:FeedQuery=home)=timeline.cached(query)
        fun close() { timeline.close() }
    }

    @Test fun personalizedOrderStaysStableWhileReactingAndOnlyChangesWhenRequested()=runBlocking {
        val f=Fixture()
        try {
            f.rows=listOf(post(2),post(1));f.start();f.load()
            val order=f.page().posts.map { it.id }
            f.db.recordFeedEvent("viewer",id(1),FeedEventKind.CLAP)
            f.timeline.localChanged()
            assertEquals(order,f.page().posts.map { it.id })
            f.rows=listOf(post(1),post(2))
            f.timeline.refresh(home,force=true)!!.join()
            assertEquals(order,f.page().posts.map { it.id })
            f.timeline.refresh(home,force=true,reveal=true)!!.join()
            assertEquals(listOf(id(1),id(2)),f.page().posts.map { it.id })
        } finally { f.close() }
    }

    @Test fun returningToTheFeedReusesItsSnapshotWithoutAnEmptyIntermediateState()=runBlocking {
        val f=Fixture()
        try {
            f.start();f.load()
            val original=f.page().posts
            val gate=CompletableDeferred<List<SocialPost>>()
            f.fetch={gate.await()};f.tick+=31_000
            val request=f.timeline.refresh(home)!!
            assertTrue(f.page().refreshing)
            assertSame(original,f.page().posts)
            assertEquals(listOf(id(1)),f.page().posts.map { it.id })
            gate.complete(listOf(post(1,count=7)));request.join()
            assertEquals(7,f.page().posts.single().impactCount)
            assertTrue(f.page().ready)
        } finally { f.close() }
    }
    @Test fun aRecentPostPrecedesAnOlderOwnPostEvenWithAnOldRankedOrCachedOrder()=runBlocking {
        val f=Fixture()
        try {
            val now=com.onlygoodthings.shared.realtime.currentEpochMs()
            f.rows=listOf(post(2,author="viewer").copy(createdAtEpochMs=now-3_600_000),post(1).copy(createdAtEpochMs=now-1000))
            f.start();f.load()
            assertEquals(listOf(id(1),id(2)),f.page().posts.map { it.id })
            f.timeline.localChanged()
            assertEquals(listOf(id(1),id(2)),f.page().posts.map { it.id })
        } finally { f.close() }
    }
    @Test fun shortNavigationDoesNotRepeatRequestsAndErrorsKeepTheLastContent()=runBlocking {
        val f=Fixture()
        try {
            f.start();f.load();f.load()
            assertEquals(1,f.requests)
            f.fetch={error("offline")}
            f.timeline.refresh(home,force=true)!!.join()
            assertEquals(listOf(id(1)),f.page().posts.map { it.id })
            assertNotNull(f.page().error)
            assertFalse(f.page().refreshing)
        } finally { f.close() }
    }
    @Test fun coldCacheIsReconciledBeforePaintingAndConfirmedRemovalIsSavedEvenIfFeedFails()=runBlocking {
        val f=Fixture()
        try {
            f.db.upsertRemoteSocial(post(1));f.db.upsertRemoteSocial(post(2))
            f.visibility={listOf(id(1))}
            f.fetch={
                assertFalse(f.saved.contains(id(2)))
                error("offline after validation")
            }
            f.start()
            assertFalse(f.page().ready);assertTrue(f.page().posts.isEmpty())
            f.load()
            assertTrue(f.page().ready)
            assertEquals(listOf(id(1)),f.page().posts.map { it.id })
            val restarted=OgtLocalDatabase();restarted.importSocialFeed(f.saved)
            assertTrue(restarted.posts.none { it.id==id(2) })
        } finally { f.close() }
    }
    @Test fun anInitialNetworkFailureStillAllowsTheExistingOfflineCache()=runBlocking {
        val f=Fixture()
        try {
            f.db.upsertRemoteSocial(post(1))
            f.visibility={error("offline")};f.fetch={error("offline")}
            f.start();f.load()
            assertEquals(listOf(id(1)),f.page().posts.map { it.id })
            assertTrue(f.page().ready)
        } finally { f.close() }
    }
    @Test fun socketCountersUpdateTheCachedFeedWithoutAnyScreenSubscriber()=runBlocking {
        val f=Fixture()
        try {
            f.start();f.load()
            f.signal(SocialLiveCounters(id(1),commentCount=3,impactCount=9))
            assertEquals(9,f.page().posts.single().impactCount)
            assertEquals(3,f.page().posts.single().commentCount)
            delay(250)
            val restarted=OgtLocalDatabase();restarted.importSocialFeed(f.saved)
            assertEquals(9,restarted.post(id(1)).impactCount)
            assertEquals(1,f.requests)
        } finally { f.close() }
    }
    @Test fun newPostsAreHydratedWithTheirMediaAndQueuedWithoutMovingTheExistingRows()=runBlocking {
        val f=Fixture()
        try {
            f.start();f.load()
            val fresh=post(2).copy(media=listOf(PostMediaItem("photo",MediaKind.IMAGE,"https://example.test/photo.jpg")))
            f.rows=listOf(fresh,post(1))
            f.signal(SocialLiveCounters(id(2),0,0,authorUserId="neighbor",body=fresh.body,tag=fresh.topic))
            assertEquals(listOf(id(1)),f.page().posts.map { it.id })
            assertEquals(listOf(id(2)),f.page().pendingIds)
            assertEquals("https://example.test/photo.jpg",f.db.mediaOf(id(2)).single().url)
            f.timeline.reveal(home)
            assertEquals(listOf(id(2),id(1)),f.page().posts.map { it.id })
            assertTrue(f.page().pendingIds.isEmpty())
        } finally { f.close() }
    }
    @Test fun aDeniedNewPostNeverEntersTheOfflineCacheOrTheVisibleFeed()=runBlocking {
        val f=Fixture()
        try {
            f.start();f.load();f.get={error("not visible to this account")}
            f.signal(SocialLiveCounters(id(2),0,0,authorUserId="blocked",body="No admitir",tag="Reforestación"))
            assertTrue(f.db.posts.none { it.id==id(2) })
            assertTrue(f.page().pendingIds.isEmpty())
            assertEquals(listOf(id(1)),f.page().posts.map { it.id })
        } finally { f.close() }
    }
    @Test fun removalDuringARefreshCannotBeUndoneByALateResponse()=runBlocking {
        val f=Fixture()
        try {
            f.start();f.load()
            val gate=CompletableDeferred<List<SocialPost>>();f.fetch={gate.await()}
            val request=f.timeline.refresh(home,force=true)!!
            f.signal(SocialLiveCounters(id(1),0,0,moderationStatus="REMOVED"))
            assertTrue(f.page().posts.isEmpty())
            gate.complete(listOf(post(1)));request.join()
            assertTrue(f.db.posts.none { it.id==id(1) })
            assertTrue(f.page().posts.isEmpty())
            assertFalse(f.saved.contains(id(1)))
        } finally { f.close() }
    }
    @Test fun anInFlightRefreshPreservesOptimisticReactionsAndTheirRollback()=runBlocking {
        val f=Fixture()
        try {
            f.start();f.load()
            val original=f.db.post(id(1))
            val gate=CompletableDeferred<List<SocialPost>>();f.fetch={gate.await()}
            val request=f.timeline.refresh(home,force=true)!!
            f.db.clapPost("viewer",id(1));yield()
            gate.complete(listOf(post(1)));request.join()
            assertTrue(f.page().posts.single().viewerHasImpacted)
            assertEquals(1,f.page().posts.single().impactCount)
            f.db.restorePost(original);yield()
            assertFalse(f.page().posts.single().viewerHasImpacted)
            assertEquals(0,f.page().posts.single().impactCount)
        } finally { f.close() }
    }
    @Test fun aLateResponseFromThePreviousAccountCannotPopulateTheNextTimeline()=runBlocking {
        val f=Fixture()
        try {
            f.start()
            val gate=CompletableDeferred<List<SocialPost>>()
            f.fetch={withContext(NonCancellable){gate.await()}}
            val previous=f.timeline.refresh(home)!!
            f.timeline.start("another-account")
            gate.complete(listOf(post(1)));previous.join()
            assertTrue(f.timeline.pages.value.isEmpty())
            assertTrue(f.db.posts.isEmpty())
            assertFalse(f.page().ready)
        } finally { f.close() }
    }
    @Test fun filtersAndScrollHoldersSurviveNavigationAndResetOnAccountChange()=runBlocking {
        val f=Fixture()
        try {
            f.start();f.load()
            val view=f.timeline.presentation
            val query=FeedQuery(FeedMode.FOLLOWING,FeedTopicFamily.PETS)
            view.mode=query.mode;view.family=query.family
            val position=view.position(query)
            assertSame(position,f.timeline.presentation.position(query))
            assertNotSame(position,view.position(home))
            assertEquals(query,f.timeline.presentation.query)
            assertTrue(view.acknowledgeOwn(query,1))
            assertFalse(view.acknowledgeOwn(query,1))
            f.timeline.start("other")
            assertEquals(home,f.timeline.presentation.query)
            assertNotSame(position,f.timeline.presentation.position(query))
        } finally { f.close() }
    }
    @Test fun passiveRevalidationKeepsOrderAndExplicitRefreshAcceptsTheLatestOrder()=runBlocking {
        val f=Fixture()
        try {
            f.rows=listOf(post(1),post(2));f.start();f.load()
            f.rows=listOf(post(2),post(1));f.timeline.refresh(home,force=true)!!.join()
            assertEquals(listOf(id(1),id(2)),f.page().posts.map { it.id })
            f.timeline.refresh(home,force=true,reveal=true)!!.join()
            assertEquals(listOf(id(2),id(1)),f.page().posts.map { it.id })
        } finally { f.close() }
    }
    @Test fun foregroundAndTransportReconnectionsResumeOneSubscriptionAndReconcile()=runBlocking {
        val f=Fixture()
        try {
            f.start();f.load()
            f.timeline.setForeground(false)
            withTimeout(2000) { f.events.subscriptionCount.first { it==0 } }
            f.rows=listOf(post(1,count=11))
            f.timeline.setForeground(true)
            withTimeout(2000) { f.events.subscriptionCount.first { it==1 } }
            assertEquals(11,f.page().posts.single().impactCount)
            val openings=f.openings;f.transport.value++
            withTimeout(2000) { while(f.openings==openings)yield() }
            assertEquals(1,f.events.subscriptionCount.value)
            f.events.emit(FeedSignal.Connected);yield()
            assertEquals(3,f.requests)
        } finally { f.close() }
    }
    @Test fun followingRemovesPostsNoLongerReturnedWithoutClearingTheHomeCache()=runBlocking {
        val f=Fixture()
        try {
            f.db.follows+=LocalFollow("viewer","neighbor")
            f.start();f.load()
            val following=FeedQuery(FeedMode.FOLLOWING)
            f.load(following)
            assertEquals(listOf(id(1)),f.page(following).posts.map { it.id })
            f.rows=emptyList()
            f.timeline.refresh(following,force=true)!!.join()
            assertTrue(f.page(following).posts.isEmpty())
            assertEquals(listOf(id(1)),f.page(home).posts.map { it.id })
        } finally { f.close() }
    }
    @Test fun reconnectDuringARequestReplaysOneRevalidationAndNeverPaintsAnEmptyList()=runBlocking {
        val f=Fixture()
        try {
            f.start();f.load()
            val gate=CompletableDeferred<List<SocialPost>>()
            f.fetch={if(f.requests==2)gate.await() else listOf(post(1,count=12))}
            val request=f.timeline.refresh(home,force=true)!!
            f.events.emit(FeedSignal.Connected);yield()
            f.events.emit(FeedSignal.Connected);yield()
            assertEquals(2,f.requests)
            assertEquals(listOf(id(1)),f.page().posts.map { it.id })
            gate.complete(listOf(post(1,count=2)));request.join();yield()
            assertEquals(3,f.requests)
            assertEquals(12,f.page().posts.single().impactCount)
        } finally { f.close() }
    }
    @Test fun ownNewPublicationAppearsImmediatelyAndIsAcknowledgedOnlyOnce()=runBlocking {
        val f=Fixture()
        try {
            f.start();f.load()
            f.db.upsertRemoteSocial(post(2,"viewer").copy(createdAtEpochMs=com.onlygoodthings.shared.realtime.currentEpochMs()))
            yield()
            assertEquals(id(2),f.page().posts.first().id)
            val revision=f.page().ownPublication
            assertTrue(f.timeline.presentation.acknowledgeOwn(home,revision))
            f.db.bumpSocial();yield()
            assertFalse(f.timeline.presentation.acknowledgeOwn(home,f.page().ownPublication))
        } finally { f.close() }
    }
    @Test fun aRemovedUnknownPostCannotBeResurrectedByItsLateHydration()=runBlocking {
        val f=Fixture()
        try {
            f.start();f.load()
            val gate=CompletableDeferred<SocialPost>()
            f.get={withContext(NonCancellable){gate.await()}}
            f.signal(SocialLiveCounters(id(2),0,0,authorUserId="neighbor",body="Nueva acción",tag="Reforestación"))
            f.signal(SocialLiveCounters(id(2),0,0,moderationStatus="REMOVED"))
            gate.complete(post(2));yield()
            assertTrue(f.db.posts.none { it.id==id(2) })
            assertTrue(f.page().pendingIds.isEmpty())
        } finally { f.close() }
    }

    @Test fun aLateFeedResponseCannotUndoAnOptimisticEditOrDelete()=runBlocking {
        val f=Fixture()
        try {
            val original=post(1,"viewer")
            f.rows=listOf(original);f.start();f.load()
            var gate=CompletableDeferred<List<SocialPost>>();f.fetch={gate.await()}
            var request=f.timeline.refresh(home,force=true)!!
            f.db.editOwnPost("viewer",id(1),"Relato corregido");yield()
            gate.complete(listOf(original));request.join()
            assertEquals("Relato corregido",f.page().posts.single().body)
            gate=CompletableDeferred();request=f.timeline.refresh(home,force=true)!!
            f.db.removeOwnPost("viewer",id(1));yield()
            gate.complete(listOf(original));request.join()
            assertTrue(f.page().posts.isEmpty())
            assertTrue(f.db.posts.none { it.id==id(1) })
        } finally { f.close() }
    }

}
