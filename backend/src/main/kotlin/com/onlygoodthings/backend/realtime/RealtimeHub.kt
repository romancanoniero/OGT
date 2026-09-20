package com.onlygoodthings.backend.realtime

import com.onlygoodthings.backend.infra.RedisCache
import com.onlygoodthings.shared.protocol.WsChannel
import com.onlygoodthings.shared.protocol.WsCodec
import com.onlygoodthings.shared.protocol.WsEnvelope
import com.onlygoodthings.shared.realtime.OgtDbPaths
import io.ktor.websocket.DefaultWebSocketSession
import io.ktor.websocket.Frame
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.ConcurrentHashMap

class RealtimeHub(private val redis: RedisCache) {
    private val mutex = Mutex()
    private val sessions = ConcurrentHashMap<String, MutableSet<DefaultWebSocketSession>>()

    suspend fun register(userId: String, session: DefaultWebSocketSession) {
        mutex.withLock {
            sessions.getOrPut(userId) { mutableSetOf() }.add(session)
        }
        redis.setPresence(userId)
    }

    suspend fun unregister(userId: String, session: DefaultWebSocketSession) {
        mutex.withLock {
            sessions[userId]?.remove(session)
        }
    }

    suspend fun broadcast(channel: WsChannel, envelope: WsEnvelope) {
        val encoded = WsCodec.encode(envelope)
        redis.publish("ogt:ws:${channel.name.lowercase()}", encoded)
        sessions.values.flatten().forEach { session ->
            runCatching { session.send(Frame.Text(encoded)) }
        }
    }

    fun projectParkingSpot(spotId: String, payloadJson: String) {
        redis.publish("ogt:tree:${OgtDbPaths.parkingSpot(spotId)}", payloadJson)
    }

    fun projectAlert(alertId: String, payloadJson: String) {
        redis.publish("ogt:tree:${OgtDbPaths.impactAlert(alertId)}", payloadJson)
    }

    fun projectSocialPost(postId: String, payloadJson: String) {
        redis.publish("ogt:tree:${OgtDbPaths.socialPost(postId)}", payloadJson)
    }

    fun projectSocialComment(postId: String, commentId: String, payloadJson: String) {
        redis.publish("ogt:tree:${OgtDbPaths.socialComment(postId, commentId)}", payloadJson)
    }
}
