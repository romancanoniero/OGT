package com.onlygoodthings.backend.infra

import redis.clients.jedis.JedisPooled

class RedisCache(redisUrl: String) {
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
}
