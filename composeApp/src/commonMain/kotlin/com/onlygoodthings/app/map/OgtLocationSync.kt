package com.onlygoodthings.app.map

import com.onlygoodthings.shared.domain.LocationSyncPolicy
import com.onlygoodthings.shared.realtime.currentEpochMs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Recibe fixes del chip (primer plano o servicio) y los manda al backend
 * sin depender de que un Composable esté montado.
 */
object OgtLocationSync {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val mutex = Mutex()
    private var lastLat: Double? = null
    private var lastLng: Double? = null
    private var lastMs: Long = 0L
    private var uploader: (suspend (Double, Double, Double?) -> Unit)? = null
    var enabled: Boolean = false

    fun bind(upload: suspend (latitude: Double, longitude: Double, accuracyMeters: Double?) -> Unit) {
        uploader = upload
    }

    fun offer(fix: DeviceLocation) {
        if (!enabled) return
        if (!fix.latitude.isFinite() || !fix.longitude.isFinite()) return
        scope.launch {
            mutex.withLock {
                val at = fix.epochMs.takeIf { it > 0L } ?: currentEpochMs()
                if (!LocationSyncPolicy.shouldUpload(lastLat, lastLng, lastMs, fix.latitude, fix.longitude, at)) {
                    return@withLock
                }
                val upload = uploader ?: return@withLock
                val ok = runCatching {
                    upload(fix.latitude, fix.longitude, fix.accuracyMeters?.toDouble())
                }.isSuccess
                if (ok) {
                    lastLat = fix.latitude
                    lastLng = fix.longitude
                    lastMs = at
                }
            }
        }
    }
}
