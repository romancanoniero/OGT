package com.onlygoodthings.app.notify

import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.onlygoodthings.shared.data.local.LocalNotification
import com.onlygoodthings.shared.realtime.currentEpochMs

/**
 * Push en segundo plano: el sistema pinta el notification payload.
 * En primer plano FCM entrega acá y mostramos el banner nosotros.
 */
class OgtMessagingService : FirebaseMessagingService() {
    override fun onNewToken(token: String) {
        OgtPushToken.latest = token
    }

    override fun onMessageReceived(message: RemoteMessage) {
        val data = message.data
        val title = message.notification?.title ?: data["title"].orEmpty()
        val body = message.notification?.body ?: data["body"].orEmpty()
        if (title.isBlank()) return
        showSystemNotice(
            LocalNotification(
                id = data["id"] ?: "fcm-${currentEpochMs()}",
                kind = data["kind"] ?: "MENTION",
                title = title,
                body = body,
                timeLabel = "Ahora",
                urgent = data["kind"] == "MENTION",
                postId = data["postId"],
                deepLink = data["deepLink"],
            ),
        )
    }
}

object OgtPushToken {
    var latest: String? = null
}
