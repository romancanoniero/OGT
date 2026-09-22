package com.onlygoodthings.backend.realtime

import com.onlygoodthings.backend.auth.FirebaseTokenVerifier
import com.onlygoodthings.backend.infra.IdentityStore
import com.onlygoodthings.shared.realtime.OgtDbPaths
import io.ktor.server.routing.Route
import io.ktor.server.websocket.webSocket
import io.ktor.websocket.CloseReason
import io.ktor.websocket.Frame
import io.ktor.websocket.close
import io.ktor.websocket.readText
import kotlinx.coroutines.channels.ClosedReceiveChannelException
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put

/**
 * Socket ` /db ` del protocolo db-kmp-sdk (auth / listen / value / child_*).
 * La UI web usa la misma fachada que OgtRealtime; no un WS inventado.
 */
fun Route.dbGatewayRoutes(
    verifier: FirebaseTokenVerifier,
    identity: IdentityStore,
    hub: RealtimeHub,
) {
    webSocket("/db") {
        var authed = false
        val listens = mutableMapOf<Long, String>()
        val mutex = Mutex()
        val sendLock = Mutex()

        suspend fun emit(obj: JsonObject) {
            sendLock.withLock {
                outgoing.send(Frame.Text(obj.toString()))
            }
        }

        val session = this
        val treeHandler: (String, String) -> Unit = { path, payload ->
            session.launch {
                mutex.withLock {
                    listens.forEach { (id, listenPath) ->
                        when {
                            path == listenPath -> emit(valueEvent(id, payload))
                            path.startsWith("$listenPath/") -> {
                                val rest = path.removePrefix("$listenPath/")
                                val key = rest.substringBefore('/')
                                if (key.isNotBlank() && !rest.contains('/')) {
                                    emit(childChanged(id, key, payload))
                                }
                            }
                        }
                    }
                }
            }
        }
        hub.addTreeListener(treeHandler)
        try {
            for (frame in incoming) {
                val text = (frame as? Frame.Text)?.readText() ?: continue
                val obj = runCatching { json.parseToJsonElement(text).jsonObject }.getOrNull() ?: continue
                when (obj["t"]?.jsonPrimitive?.contentOrNull) {
                    "auth" -> {
                        val token = obj["token"]?.jsonPrimitive?.contentOrNull.orEmpty()
                        runCatching {
                            identity.upsertFromFirebase(verifier.verify(token))
                        }.onSuccess {
                            authed = true
                            emit(ackAuth())
                        }.onFailure { err ->
                            emit(errorMsg(code = "UNAUTHENTICATED", message = err.message ?: "Token inválido"))
                            close(CloseReason(CloseReason.Codes.CANNOT_ACCEPT, "unauthenticated"))
                            return@webSocket
                        }
                    }
                    "listen" -> {
                        if (!authed) {
                            emit(errorMsg(id = obj["id"]?.jsonPrimitive?.longOrNull, code = "UNAUTHENTICATED", message = "Auth requerido"))
                            continue
                        }
                        val id = obj["id"]?.jsonPrimitive?.longOrNull ?: continue
                        val path = obj["path"]?.jsonPrimitive?.contentOrNull?.trim('/').orEmpty()
                        mutex.withLock { listens[id] = path }
                        emit(listenAck(id))
                        if (path == OgtDbPaths.SOCIAL_FEED) {
                            emit(valueEvent(id, "{}"))
                        }
                    }
                    "unlisten" -> {
                        val id = obj["id"]?.jsonPrimitive?.longOrNull ?: continue
                        mutex.withLock { listens.remove(id) }
                        emit(unlistenAck(id))
                    }
                    else -> Unit
                }
            }
        } catch (_: ClosedReceiveChannelException) {
            // cliente cortó
        } finally {
            hub.removeTreeListener(treeHandler)
        }
    }
}

private val json = Json { ignoreUnknownKeys = true }

private fun ackAuth(): JsonObject = buildJsonObject {
    put("t", "ack")
    put("rid", JsonPrimitive(0))
}

private fun listenAck(id: Long): JsonObject = buildJsonObject {
    put("t", "listen_ack")
    put("id", id)
}

private fun unlistenAck(id: Long): JsonObject = buildJsonObject {
    put("t", "unlisten_ack")
    put("id", id)
}

private fun valueEvent(id: Long, payload: String): JsonObject = buildJsonObject {
    put("t", "value")
    put("id", id)
    put("snapshot", runCatching { json.parseToJsonElement(payload) }.getOrDefault(JsonNull))
}

private fun childChanged(id: Long, key: String, payload: String): JsonObject = buildJsonObject {
    put("t", "child_changed")
    put("id", id)
    put("key", key)
    put("prev", JsonNull)
    put("value", runCatching { json.parseToJsonElement(payload) }.getOrDefault(JsonNull))
}

private fun errorMsg(rid: Long? = null, id: Long? = null, code: String, message: String): JsonObject =
    buildJsonObject {
        put("t", "error")
        if (rid != null) put("rid", rid)
        if (id != null) put("id", id)
        put("code", code)
        put("message", message)
    }
