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
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
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
    canReceive:(String,String)->Boolean={_,_->true},
    socketPath: String = "/db",
    acceptsIdentity: (com.onlygoodthings.backend.auth.VerifiedFirebaseUser) -> Boolean = { true },
    authTimeoutMillis: Long? = null,
    authorizationLifetimeMillis: (() -> Long)? = null,
) {
    webSocket(socketPath) {
        var authed = false
        var viewerId: String? = null
        val listens = mutableMapOf<Long, String>()
        val mutex = Mutex()
        val sendLock = Mutex()
        val authDeadline = authTimeoutMillis?.let { timeout -> launch {
            delay(timeout)
            if (!authed) close(CloseReason(CloseReason.Codes.CANNOT_ACCEPT, "authentication timeout"))
        } }
        var accessDeadline: Job? = null

        suspend fun emit(obj: JsonObject) {
            sendLock.withLock {
                outgoing.send(Frame.Text(obj.toString()))
            }
        }

        val session = this
        val treeHandler: (String, String) -> Unit = { path, payload ->
            session.launch {
                val viewer=viewerId ?: return@launch
                if(!kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { canReceive(viewer,path) })return@launch
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
                            val verified=kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { verifier.verify(token) }
                            require(acceptsIdentity(verified)) { "Acceso de revisión no disponible" }
                            identity.upsertFromFirebase(verified)
                        }.onSuccess { profile ->
                            if (authed && viewerId != profile.id) {
                                emit(errorMsg(id=null,code="IDENTITY_CHANGE_DENIED",message="Abrí una nueva conexión para cambiar de cuenta"))
                                close(CloseReason(CloseReason.Codes.CANNOT_ACCEPT,"identity change denied"))
                                return@webSocket
                            }
                            authed = true
                            authDeadline?.cancel()
                            accessDeadline?.cancel()
                            authorizationLifetimeMillis?.let { remaining -> accessDeadline = launch {
                                delay(remaining().coerceAtLeast(0))
                                close(CloseReason(CloseReason.Codes.CANNOT_ACCEPT, "review access expired"))
                            } }
                            viewerId = profile.id
                            emit(ackAuth())
                        }.onFailure { err ->
                            emit(errorMsg(code = "UNAUTHENTICATED", message = err.message ?: "Token inválido"))
                            close(CloseReason(CloseReason.Codes.CANNOT_ACCEPT, "unauthenticated"))
                            return@webSocket
                        }
                    }
                    "listen" -> {
                        val ownerId = viewerId
                        if (!authed || ownerId.isNullOrBlank()) {
                            emit(errorMsg(id = obj["id"]?.jsonPrimitive?.longOrNull, code = "UNAUTHENTICATED", message = "Auth requerido"))
                            continue
                        }
                        val id = obj["id"]?.jsonPrimitive?.longOrNull ?: continue
                        val path = obj["path"]?.jsonPrimitive?.contentOrNull?.trim('/').orEmpty()
                        if (!dbListenAllowed(path, ownerId) || !canReceive(ownerId,path)) {
                            emit(errorMsg(id = id, code = "PERMISSION_DENIED", message = "Path no autorizado"))
                            continue
                        }
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
            authDeadline?.cancel()
            accessDeadline?.cancel()
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
