package com.onlygoodthings.app.media

import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import kotlin.test.*

@OptIn(ExperimentalCoroutinesApi::class)
class MediaPrefetchQueueTest {
    @Test fun everyUnseenFileIsPreparedWithThreeWorkersAndDuplicatesAreJoined()=runTest {
        val seen=mutableListOf<String>();var active=0;var maximum=0
        val queue=MediaPrefetchQueue(backgroundScope) { url ->
            active++;maximum=maxOf(active,maximum);delay(10);seen+=url;active--
        }
        val urls=(0 until 100).map { "https://example.test/$it.mp4" }
        queue.enqueue(urls+urls);runCurrent()
        assertEquals(100,queue.pendingCount());assertEquals(3,maximum)
        advanceTimeBy(1000);runCurrent();queue.awaitIdle()
        assertEquals(urls.toSet(),seen.toSet());assertEquals(100,seen.size);assertEquals(0,queue.pendingCount())
    }
    @Test fun aFailedBackgroundFileCanBeRetriedWithoutDiscardingTheRest()=runTest {
        var attempts=0;val seen=mutableListOf<String>()
        val queue=MediaPrefetchQueue(backgroundScope) { url ->if(url=="retry"&&attempts++==0)error("offline") else seen+=url }
        queue.enqueue(listOf("retry","next"));runCurrent();queue.awaitIdle()
        assertEquals(listOf("next"),seen)
        queue.enqueue(listOf("retry"));runCurrent();queue.awaitIdle();assertEquals(listOf("next","retry"),seen)
    }
    @Test fun memoryIsBoundedByBytesAndRecentlyReadImagesStayAvailable() {
        val cache=MediaMemoryCache<ByteArray>(10) { it.size.toLong() }
        cache.put("first",ByteArray(4));cache.put("second",ByteArray(4));assertNotNull(cache.get("first"))
        cache.put("third",ByteArray(4));assertNotNull(cache.get("first"));assertNull(cache.get("second"));assertEquals(8L,cache.usedBytes())
        cache.put("large",ByteArray(100));assertNull(cache.get("large"));assertTrue(cache.usedBytes()<=10)
    }
}
