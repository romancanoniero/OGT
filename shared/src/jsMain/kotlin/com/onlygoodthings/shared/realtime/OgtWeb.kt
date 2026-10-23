@file:OptIn(ExperimentalJsExport::class)

package com.onlygoodthings.shared.realtime

import com.onlygoodthings.shared.domain.SocialComment
import com.onlygoodthings.shared.domain.SocialLiveCounters
import kotlinx.browser.window
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json

private val liveJson = Json {
    encodeDefaults = true
    explicitNulls = false
    ignoreUnknownKeys = true
}

private fun defaultGateway(): String {
    val proto = if (window.location.protocol == "https:") "wss" else "ws"
    return "$proto://${window.location.host}/db"
}

/**
 * Fachada JS de [OgtSdk] + [OgtRealtime] (db-kmp-sdk / DbWeb).
 * La página vanilla hace `new OgtRealtime()` y observa el feed.
 */
@JsExport
@JsName("OgtRealtime")
class OgtWebRealtime {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var listenJob: Job? = null
    private val commentJobs = mutableMapOf<String, Job>()

    fun start(token: String, endpoint: String? = null) {
        val url = endpoint?.takeIf { it.isNotBlank() } ?: defaultGateway()
        val provider: suspend () -> String? = { token }
        OgtSdk.start(url, tokenProvider = provider)
    }

    fun observeSocialPosts(onLive: (String) -> Unit): () -> Unit {
        listenJob?.cancel()
        val job = scope.launch {
            OgtRealtime().observeSocialPosts().collect { live ->
                onLive(liveJson.encodeToString(SocialLiveCounters.serializer(), live))
            }
        }
        listenJob = job
        return { job.cancel() }
    }

    /** Mismos comentarios que ya escucha la ficha: `ogt/social/posts/{postId}/comments`. */
    fun observePostComments(postId: String, onLive: (String) -> Unit): () -> Unit {
        commentJobs.remove(postId)?.cancel()
        val job = scope.launch {
            OgtRealtime().observePostComments(postId).collect { comment ->
                onLive(liveJson.encodeToString(SocialComment.serializer(), comment))
            }
        }
        commentJobs[postId] = job
        return {
            job.cancel()
            commentJobs.remove(postId)
        }
    }

    fun shutdown() {
        listenJob?.cancel()
        listenJob = null
        commentJobs.values.forEach { it.cancel() }
        commentJobs.clear()
        OgtSdk.shutdown()
    }
}

fun main() {
    window.asDynamic().OgtRealtime = OgtWebRealtime::class.js
}
