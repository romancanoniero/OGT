package com.onlygoodthings.backend.notify

import com.google.firebase.FirebaseApp
import com.google.firebase.messaging.AndroidConfig
import com.google.firebase.messaging.AndroidNotification
import com.google.firebase.messaging.ApnsConfig
import com.google.firebase.messaging.Aps
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.Message
import com.google.firebase.messaging.Notification
import com.onlygoodthings.backend.infra.Database
import com.onlygoodthings.shared.domain.postDeepLink
import java.util.UUID

class NoticeSqlRepository(private val db: Database) {

    fun register(userId: String, token: String, platform: String) {
        val plat = platform.uppercase().let { if (it == "IOS") "IOS" else "ANDROID" }
        db.withConnection { connection ->
            connection.prepareStatement(
                """
                INSERT INTO device_tokens (id, user_id, token, platform)
                VALUES (?::uuid, ?::uuid, ?, ?)
                ON CONFLICT (token) DO UPDATE SET user_id = EXCLUDED.user_id, platform = EXCLUDED.platform, updated_at = now()
                """.trimIndent(),
            ).use { stmt ->
                stmt.setString(1, UUID.randomUUID().toString())
                stmt.setString(2, userId)
                stmt.setString(3, token)
                stmt.setString(4, plat)
                stmt.executeUpdate()
            }
        }
    }

    fun notifyMention(actorId: String, recipientId: String, postId: String): Int {
        if (actorId == recipientId) return 0
        val actorName = displayName(actorId) ?: "Alguien de la comunidad"
        val title = "$actorName te mencionó"
        val body = "Contó algo bueno que hiciste. Tocá para verlo en la comunidad."
        val link = postDeepLink(postId)
        return send(recipientId, title, body, "MENTION", postId, link)
    }

    fun send(recipientId: String, title: String, body: String, kind: String, postId: String?, deepLink: String): Int {
        if (FirebaseApp.getApps().isEmpty()) return 0
        val tokens = tokensOf(recipientId)
        if (tokens.isEmpty()) return 0
        var sent = 0
        tokens.forEach { token ->
            runCatching {
                val message = Message.builder()
                    .setToken(token)
                    .setNotification(Notification.builder().setTitle(title).setBody(body).build())
                    .putData("kind", kind)
                    .putData("title", title)
                    .putData("body", body)
                    .putData("postId", postId.orEmpty())
                    .putData("deepLink", deepLink)
                    .setAndroidConfig(
                        AndroidConfig.builder()
                            .setPriority(AndroidConfig.Priority.HIGH)
                            .setNotification(
                                AndroidNotification.builder()
                                    .setChannelId("ogt_mentions")
                                    .build(),
                            )
                            .build(),
                    )
                    .setApnsConfig(
                        ApnsConfig.builder()
                            .setAps(Aps.builder().setSound("default").setBadge(1).build())
                            .putHeader("apns-priority", "10")
                            .build(),
                    )
                    .build()
                FirebaseMessaging.getInstance().send(message)
                sent += 1
            }.onFailure { error ->
                val stale = error.message.orEmpty().contains("registration-token-not-registered", ignoreCase = true) ||
                    error.message.orEmpty().contains("UNREGISTERED", ignoreCase = true)
                if (stale) deleteToken(token)
            }
        }
        return sent
    }

    private fun deleteToken(token: String) {
        db.withConnection { connection ->
            connection.prepareStatement("DELETE FROM device_tokens WHERE token = ?").use { stmt ->
                stmt.setString(1, token)
                stmt.executeUpdate()
            }
        }
    }

    private fun displayName(userId: String): String? = db.withConnection { connection ->
        connection.prepareStatement("SELECT display_name FROM users WHERE id = ?::uuid").use { stmt ->
            stmt.setString(1, userId)
            stmt.executeQuery().use { rs -> if (rs.next()) rs.getString(1) else null }
        }
    }

    private fun tokensOf(userId: String): List<String> = db.withConnection { connection ->
        connection.prepareStatement("SELECT token FROM device_tokens WHERE user_id = ?::uuid").use { stmt ->
            stmt.setString(1, userId)
            stmt.executeQuery().use { rs ->
                buildList { while (rs.next()) add(rs.getString(1)) }
            }
        }
    }
}
