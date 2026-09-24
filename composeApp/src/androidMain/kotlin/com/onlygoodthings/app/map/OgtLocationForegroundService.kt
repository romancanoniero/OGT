package com.onlygoodthings.app.map

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.onlygoodthings.app.MainActivity

/**
 * Mantiene el chip GPS vivo cuando la UI no está en primer plano
 * y entrega cada fix a [OgtLocationSync].
 */
class OgtLocationForegroundService : Service() {
    private var session: LiveSession? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        ensureChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notification = buildNotification()
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(
                NOTIF_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION,
            )
        } else {
            startForeground(NOTIF_ID, notification)
        }
        if (session == null) {
            session = startLiveUpdates(this) { fix ->
                OgtLocationSync.offer(fix)
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        session?.close()
        session = null
        super.onDestroy()
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT < 26) return
        val manager = getSystemService(NotificationManager::class.java) ?: return
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Ubicación",
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = "Actualiza tu barrio aunque la app no esté abierta"
            setShowBadge(false)
        }
        manager.createNotificationChannel(channel)
    }

    private fun buildNotification(): Notification {
        val launch = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("GPS activo")
            .setContentText("Actualizando tu ubicación para el barrio")
            .setSmallIcon(com.onlygoodthings.app.R.mipmap.ic_launcher)
            .setContentIntent(launch)
            .setOngoing(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
    }

    companion object {
        private const val CHANNEL_ID = "ogt_location"
        private const val NOTIF_ID = 7101
    }
}
