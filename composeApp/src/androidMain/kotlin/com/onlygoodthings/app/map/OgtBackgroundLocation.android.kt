package com.onlygoodthings.app.map

import android.content.Intent
import androidx.core.content.ContextCompat
import com.onlygoodthings.app.OgtApplication

actual object OgtBackgroundLocation {
    actual fun start() {
        val ctx = OgtApplication.instance
        runCatching {
            ContextCompat.startForegroundService(
                ctx,
                Intent(ctx, OgtLocationForegroundService::class.java),
            )
        }
    }

    actual fun stop() {
        val ctx = OgtApplication.instance
        runCatching { ctx.stopService(Intent(ctx, OgtLocationForegroundService::class.java)) }
    }
}
