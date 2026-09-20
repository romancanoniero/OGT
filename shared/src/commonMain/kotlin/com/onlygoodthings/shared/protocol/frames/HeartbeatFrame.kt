package com.onlygoodthings.shared.protocol.frames

import kotlinx.serialization.Serializable

@Serializable
data class HeartbeatFrame(
    val ping: Boolean = true,
)
