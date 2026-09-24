package com.onlygoodthings.backend.infra

import redis.clients.jedis.Jedis
import redis.clients.jedis.JedisPooled
import redis.clients.jedis.JedisPubSub
import java.net.URI
import kotlin.concurrent.thread

class RedisCache(private val redisUrl: String) {
    private val jedis = JedisPooled(redisUrl)

    fun setPresence(userId: String, ttlSeconds: Long = 45) {
        jedis.setex("presence:$userId", ttlSeconds, "1")
    }

    fun allowRate(key: String, limit: Int, windowSeconds: Long): Boolean {
        val count = jedis.incr(key)
        if (count == 1L) {
            jedis.expire(key, windowSeconds)
        }
        return count <= limit
    }

    fun publish(channel: String, payload: String) {
        jedis.publish(channel, payload)
    }

    /** Escucha `ogt:tree:*` para el gateway `/db` (db-kmp-sdk). */
    fun subscribeTree(onMessage: (channel: String, payload: String) -> Unit) {
        thread(name = "ogt-tree-fanout", isDaemon = true) {
            Jedis(URI.create(redisUrl)).use { conn ->
                conn.psubscribe(
                    object : JedisPubSub() {
                        override fun onPMessage(pattern: String?, channel: String?, message: String?) {
                            if (!channel.isNullOrBlank() && message != null) onMessage(channel, message)
                        }
                    },
                    "ogt:tree:*",
                )
            }
        }
    }
}
