@file:OptIn(ExperimentalJsExport::class)

package com.onlygoodthings.shared.realtime

import com.onlygoodthings.shared.domain.ChatLive
import com.onlygoodthings.shared.domain.ChatThreadLive
import com.onlygoodthings.shared.domain.ProfileLive
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
    private val jobs = mutableListOf<Job>()

    fun start(token: String, endpoint: String? = null) {
        val url = endpoint?.takeIf { it.isNotBlank() } ?: defaultGateway()
        OgtSdk.start(url, tokenProvider = { token })
    }

    fun observeSocialPosts(onLive: (String) -> Unit): () -> Unit {
        val job = scope.launch {
            OgtRealtime().observeSocialPosts().collect { live ->
                onLive(liveJson.encodeToString(SocialLiveCounters.serializer(), live))
            }
        }
        jobs += job
        return { job.cancel(); jobs.remove(job) }
    }

    fun observeUserProfiles(onLive: (String) -> Unit): () -> Unit {
        val job = scope.launch {
            OgtRealtime().observeUserProfiles().collect { live ->
                onLive(liveJson.encodeToString(ProfileLive.serializer(), live))
            }
        }
        jobs += job
        return { job.cancel(); jobs.remove(job) }
    }

    fun observeChatThreads(onLive: (String) -> Unit): () -> Unit {
        val job = scope.launch {
            OgtRealtime().observeChatThreads().collect { live ->
                onLive(liveJson.encodeToString(ChatThreadLive.serializer(), live))
            }
        }
        jobs += job
        return { job.cancel(); jobs.remove(job) }
    }

    fun observeChatMessages(matchId: String, onLive: (String) -> Unit): () -> Unit {
        val job = scope.launch {
            OgtRealtime().observeChatMessages(matchId).collect { live ->
                onLive(liveJson.encodeToString(ChatLive.serializer(), live))
            }
        }
        jobs += job
        return { job.cancel(); jobs.remove(job) }
    }

    fun shutdown() {
        jobs.forEach { it.cancel() }
        jobs.clear()
        OgtSdk.shutdown()
    }
}

fun main() {
    window.asDynamic().OgtRealtime = OgtWebRealtime::class.js
}
