package com.onlygoodthings.shared.protocol.frames

import com.onlygoodthings.shared.protocol.WsChannel
import kotlinx.serialization.Serializable

@Serializable
data class ConnectAuthFrame(
    val firebaseJwt: String,
    val initialChannel: WsChannel,
    val clientVersion: String,
    val devicePlatform: String,
)
