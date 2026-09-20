package com.onlygoodthings.app.map

import com.onlygoodthings.app.data.OgtPreviewSession
import com.onlygoodthings.shared.domain.GeoPoint
import kotlinx.coroutines.delay

/** Espera el fix real del chip; no usa lat/lng de seed. */
suspend fun awaitHere(session: OgtPreviewSession, gps: OgtLocationState): GeoPoint? {
    gps.refreshNow()
    repeat(24) {
        session.here()?.let { return it }
        gps.fix?.toGeoPoint()?.let { return it }
        delay(250)
    }
    return session.here() ?: gps.fix?.toGeoPoint()
}
