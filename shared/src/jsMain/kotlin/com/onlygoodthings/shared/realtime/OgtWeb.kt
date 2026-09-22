@file:OptIn(ExperimentalJsExport::class)

package com.onlygoodthings.shared.realtime

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

    fun start(token: String, endpoint: String? = null) {
        val url = endpoint?.takeIf { it.isNotBlank() } ?: defaultGateway()
        OgtSdk.start(url, tokenProvider = { token })
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

    fun shutdown() {
        listenJob?.cancel()
        listenJob = null
        OgtSdk.shutdown()
    }
}

fun main() {
    window.asDynamic().OgtRealtime = OgtWebRealtime::class.js
}
