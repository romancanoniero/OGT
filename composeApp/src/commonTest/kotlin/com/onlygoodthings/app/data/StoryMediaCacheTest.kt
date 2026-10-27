package com.onlygoodthings.app.data

import com.onlygoodthings.app.platform.readLocalFileBytes
import com.onlygoodthings.shared.realtime.currentEpochMs
import kotlinx.coroutines.*
import kotlin.test.*

class StoryMediaCacheTest {
 @Test fun reopensAndRotatingSignedUrlsReuseDiskButRevocationAndAccountSwitchRemoveAccess()=runBlocking {
    var downloads=0
    val fetch:suspend(String)->ByteArray={downloads++;"private-fixture".encodeToByteArray()}
    val now=currentEpochMs();val row=playbackStory("cache-test",expires=now+60_000).copy(mediaUrl="https://example.test/a?token=1")
    val first=StoryMediaCache(fetch)
    try {
        first.bind("cache-test-a");first.authorize("cache-test-a",listOf(row),now)
        val url=first.resolve("cache-test-a",row)
        assertTrue(url.startsWith("file:"));assertNotNull(readLocalFileBytes(url));first.resolve("cache-test-a",row)
        assertEquals(1,downloads)
        val restarted=StoryMediaCache(fetch);restarted.bind("cache-test-a")
        assertFailsWith<IllegalStateException> { restarted.resolve("cache-test-a",row) }
        restarted.authorize("cache-test-a",listOf(row),now)
        assertEquals(url,restarted.resolve("cache-test-a",row.copy(mediaUrl="https://example.test/a?token=2")));assertEquals(1,downloads)
        restarted.authorize("cache-test-a",emptyList(),now)
        assertFailsWith<IllegalStateException> { restarted.resolve("cache-test-a",row) }
        assertNull(readLocalFileBytes(url))
        restarted.authorize("cache-test-a",listOf(row),now);val second=restarted.resolve("cache-test-a",row)
        restarted.bind("cache-test-b");assertNull(readLocalFileBytes(second))
        assertFailsWith<IllegalStateException> { restarted.resolve("cache-test-a",row) }
        Unit
    } finally { first.clear();first.close() }
 }
 @Test fun failedDownloadNeverBecomesACachedEntry()=runBlocking {
    var calls=0;val cache=StoryMediaCache(fetch={ calls++;if(calls==1)error("offline");byteArrayOf(1,2,3) })
    val row=playbackStory("retry-test",expires=currentEpochMs()+60_000)
    try {
        cache.bind("retry-user");cache.authorize("retry-user",listOf(row),currentEpochMs())
        assertFailsWith<IllegalStateException> { cache.resolve("retry-user",row) }
        cache.resolve("retry-user",row);cache.resolve("retry-user",row);assertEquals(2,calls)
        cache.invalidate("retry-user",row.id);cache.resolve("retry-user",row);assertEquals(3,calls)
    } finally { cache.clear();cache.close() }
 }
 @Test fun slowPrefetchDoesNotBlockCachedPagesAndSurvivesItsViewClosing()=runBlocking {
    val entered=CompletableDeferred<Unit>();val finish=CompletableDeferred<Unit>();var calls=0
    val cache=StoryMediaCache(fetch={ url -> calls++;if(url.endsWith("slow")) { entered.complete(Unit);finish.await() };byteArrayOf(1,2,3) })
    val now=currentEpochMs();val fast=playbackStory("fast",expires=now+60_000);val slow=playbackStory("slow",expires=now+60_000)
    try {
        cache.bind("prefetch-user");cache.authorize("prefetch-user",listOf(fast,slow),now)
        val cached=cache.resolve("prefetch-user",fast)
        val view=launch { cache.resolve("prefetch-user",slow) };entered.await()
        assertEquals(cached,withTimeout(1000) { cache.resolve("prefetch-user",fast) })
        view.cancelAndJoin()
        val next=async { cache.resolve("prefetch-user",slow) };finish.complete(Unit)
        assertTrue(next.await().startsWith("file:"));assertEquals(2,calls)
    } finally { finish.complete(Unit);cache.clear();cache.close() }
 }

 @Test fun timeoutBecomesARetryableErrorRatherThanACancelledViewer()=runBlocking {
    val cache=StoryMediaCache(fetch={ awaitCancellation() },downloadTimeoutMs=20)
    val row=playbackStory("timeout",expires=currentEpochMs()+60_000)
    try {
        cache.bind("timeout-user");cache.authorize("timeout-user",listOf(row),currentEpochMs())
        val error=assertFailsWith<IllegalStateException> { cache.resolve("timeout-user",row) }
        assertTrue(error.message.orEmpty().contains("tardó demasiado"))
    } finally { cache.clear();cache.close() }
 }

}
