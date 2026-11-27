package com.onlygoodthings.shared.domain

import kotlinx.serialization.Serializable

@Serializable
data class PostLifetime(val postId: String, val expiresAtEpochMs: Long? = null, val archived: Boolean = false)

object PostLifetimeRules {
    const val DAYS = 90
    fun active(expiresAtEpochMs: Long?, now: Long) = expiresAtEpochMs == null || expiresAtEpochMs > now
}
