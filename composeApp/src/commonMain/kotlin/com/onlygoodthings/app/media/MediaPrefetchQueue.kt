package com.onlygoodthings.app.media

import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first

/** A bounded number of workers, not one coroutine/connection per unseen file. */
internal class MediaPrefetchQueue(scope:CoroutineScope,workers:Int=3,private val fetch:suspend (String)->Unit) {
    private val queue=Channel<String>(Channel.UNLIMITED)
    private val pending=mutableSetOf<String>()
    private val count=MutableStateFlow(0)
    init { repeat(workers) { scope.launch {
        for(url in queue) try { fetch(url) }
        catch(cancel:CancellationException) { throw cancel }
        catch(_:Exception) { /* A later enqueue or foreground load retries failures. */ }
        finally { withMediaCacheLock { pending.remove(url);count.value=pending.size } }
    } } }
    fun enqueue(urls:Iterable<String>) { withMediaCacheLock {
        for(url in urls) if(url.isNotBlank() && pending.add(url))queue.trySend(url)
        count.value=pending.size
    } }
    suspend fun awaitIdle() { count.first { it==0 } }
    fun pendingCount():Int=count.value
}
