package com.onlygoodthings.app.data

import com.onlygoodthings.shared.domain.StoryDraft
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** UI updates only immutable memory. One background writer serializes ordered snapshots. */
internal class StoryDraftStore(
    read: () -> String,
    private val write: (String) -> Unit,
    dispatcher: CoroutineDispatcher = Dispatchers.Default,
) {
    private val json=Json { ignoreUnknownKeys=true }
    private var drafts=runCatching { json.decodeFromString<Map<String,StoryDraft>>(read()) }.getOrDefault(emptyMap())
    private data class Save(val drafts:Map<String,StoryDraft>,val done:CompletableDeferred<Unit>?=null)
    private val saves=Channel<Save>(Channel.UNLIMITED)
    private val scope=CoroutineScope(SupervisorJob()+dispatcher)
    private val mutableFailure=MutableStateFlow<Throwable?>(null)
    val failure=mutableFailure.asStateFlow()
    private val writer=scope.launch {
        try {
            for(first in saves) {
                var latest=first
                val acknowledgements=mutableListOf<CompletableDeferred<Unit>>()
                first.done?.let(acknowledgements::add)
                // Coalesce pending keystrokes without delaying the input or dropping flush barriers.
                while(true) {
                    val next=saves.tryReceive().getOrNull() ?: break
                    latest=next
                    next.done?.let(acknowledgements::add)
                }
                try {
                    write(json.encodeToString(latest.drafts))
                    mutableFailure.value=null
                    acknowledgements.forEach { it.complete(Unit) }
                } catch(cancel:CancellationException) { throw cancel }
                catch(error:Exception) {
                    mutableFailure.value=error
                    acknowledgements.forEach { it.completeExceptionally(error) }
                }
            }
        } finally { scope.cancel() }
    }
    fun get(owner:String):StoryDraft?=drafts[owner]
    fun update(owner:String,value:StoryDraft?) {
        require(value==null || value.ownerId==owner)
        drafts=drafts.toMutableMap().apply { if(value==null) remove(owner) else put(owner,value) }.toMap()
        check(saves.trySend(Save(drafts)).isSuccess) { "El guardado de borradores está cerrado" }
    }
    suspend fun flush() {
        val done=CompletableDeferred<Unit>()
        saves.send(Save(drafts,done))
        done.await()
    }
    /** Drain the last edits even when the screen/controller's UI scope is disposed. */
    fun close() { saves.close() }
    suspend fun awaitClosed() { writer.join() }
}
