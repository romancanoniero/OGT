package com.onlygoodthings.shared.protocol

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.encodeToJsonElement

object WsCodec {
    val json: Json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        explicitNulls = false
        classDiscriminator = "kind"
    }

    fun encode(envelope: WsEnvelope): String = json.encodeToString(envelope)

    fun decode(raw: String): WsEnvelope = json.decodeFromString(raw)

    inline fun <reified T> payloadOf(envelope: WsEnvelope): T =
        json.decodeFromJsonElement(envelope.payload)

    inline fun <reified T> toPayload(value: T): JsonElement =
        json.encodeToJsonElement(value)
}
