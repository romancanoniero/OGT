package com.onlygoodthings.shared.protocol

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/**
 * Sobre común JSON (y mapeable 1:1 a Protobuf).
 * `payload` se decodifica según [type].
 */
@Serializable
data class WsEnvelope(
    val type: WsFrameType,
    val requestId: String,
    val channel: WsChannel? = null,
    val sentAtEpochMs: Long,
    val payload: JsonElement,
)

@Serializable
data class WsAck(
    val requestId: String,
    val ok: Boolean,
    val detail: String? = null,
)

@Serializable
data class WsError(
    val requestId: String,
    val code: String,
    val message: String,
)
