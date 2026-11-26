package com.onlygoodthings.app.data

import com.onlygoodthings.shared.data.local.LocalSocialPost
import com.onlygoodthings.shared.data.local.OgtLocalDatabase
import com.onlygoodthings.shared.realtime.OgtRealtime
import com.onlygoodthings.shared.realtime.OgtSdk
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withTimeoutOrNull

/** Proyecta el posteo al árbol WS para que otros feeds lo inserten o actualicen. */
suspend fun announcePublishedPost(db: OgtLocalDatabase, post: LocalSocialPost) {
    if (!OgtSdk.isStarted()) return
    announceConfirmedPost { OgtRealtime().pushSocialCounters(db.toSocialLive(post)) }
}

/** The server has already saved and projected the post; a missing socket ACK must not hold the UI. */
internal suspend fun announceConfirmedPost(timeoutMillis: Long = 3_000, push: suspend () -> Unit) {
    try {
        withTimeoutOrNull(timeoutMillis) { push() }
    } catch (cancel: CancellationException) {
        throw cancel
    } catch (_: Exception) {
        // The REST response remains the authoritative publication confirmation.
    }
}
