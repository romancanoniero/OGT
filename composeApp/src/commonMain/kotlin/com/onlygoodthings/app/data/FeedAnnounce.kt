package com.onlygoodthings.app.data

import com.onlygoodthings.shared.data.local.LocalSocialPost
import com.onlygoodthings.shared.data.local.OgtLocalDatabase
import com.onlygoodthings.shared.realtime.OgtRealtime
import com.onlygoodthings.shared.realtime.OgtSdk

/** Proyecta el posteo al árbol WS para que otros feeds lo inserten o actualicen. */
suspend fun announcePublishedPost(db: OgtLocalDatabase, post: LocalSocialPost) {
    if (!OgtSdk.isStarted()) return
    runCatching { OgtRealtime().pushSocialCounters(db.toSocialLive(post)) }
}
