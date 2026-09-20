package com.onlygoodthings.app.platform

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.onlygoodthings.shared.domain.parseHonorToken
import com.onlygoodthings.shared.domain.parsePostDeepLink

/**
 * URI nativo: honor `/h/{token}` o post `/p/{id}`.
 */
object OgtIncomingLinks {
    var latest by mutableStateOf<String?>(null)
        private set
    var latestPostId by mutableStateOf<String?>(null)
        private set

    fun offer(uri: String) {
        parseHonorToken(uri)?.let { latest = it }
        parsePostDeepLink(uri)?.let { latestPostId = it }
    }

    fun clear() {
        latest = null
    }

    fun clearPost() {
        latestPostId = null
    }
}
