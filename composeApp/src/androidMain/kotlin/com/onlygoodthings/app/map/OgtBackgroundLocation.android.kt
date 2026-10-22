package com.onlygoodthings.app.map

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import com.onlygoodthings.app.OgtApplication

actual object OgtBackgroundLocation {
    actual fun start() {
        val ctx = OgtApplication.instance
        val fine = ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_FINE_LOCATION)
        val coarse = ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_COARSE_LOCATION)
        val granted = fine == PackageManager.PERMISSION_GRANTED || coarse == PackageManager.PERMISSION_GRANTED
        if (!granted) return
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
