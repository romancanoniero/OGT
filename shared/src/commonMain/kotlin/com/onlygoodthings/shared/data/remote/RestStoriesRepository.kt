package com.onlygoodthings.shared.data.remote

import com.onlygoodthings.shared.data.SessionStore
import com.onlygoodthings.shared.domain.*
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.*
import io.ktor.http.*
import kotlinx.serialization.json.*

class RestStoriesRepository(private val client: HttpClient, private val session: SessionStore) {
    private suspend inline fun <reified T> post(path: String, data: JsonObject): T {
        val token=session.firebaseJwt ?: error("Iniciá sesión para ver historias")
        val response=client.post("${session.apiBaseUrl}/api/v1/stories/$path") {
            contentType(ContentType.Application.Json);header(HttpHeaders.Authorization,"Bearer $token");setBody(data)
        }
        if(response.status==HttpStatusCode.NotFound) error("Las historias todavía no están disponibles. Intentá nuevamente más tarde.")
        if(response.status==HttpStatusCode.Unauthorized) error("Tu sesión venció. Volvé a iniciar sesión.")
        val body=response.body<ApiResponse<T>>()
        return body.data?.takeIf { response.status.isSuccess() && body.success }
            ?: error(body.message ?: "No se pudo completar la operación de historias")
    }
    suspend fun list(cursor: String? = null): StoryPage = post("list",buildJsonObject { cursor?.let { put("cursor",it) } })
    suspend fun seen(id: String): String = post("seen",buildJsonObject { put("storyId",id) })
    suspend fun report(id:String,reason:String,details:String?):String = post("report",buildJsonObject {
        put("storyId",id);put("reason",reason);details?.let { put("details",it) }
    })
    suspend fun remove(id: String): String = post("remove",buildJsonObject { put("storyId",id) })
    @OptIn(kotlin.io.encoding.ExperimentalEncodingApi::class)
    suspend fun publish(draft: StoryDraft, bytes: ByteArray, poster: ByteArray?): Story = post("publish",buildJsonObject {
        put("requestId",draft.id);put("caption",draft.caption);put("audience",draft.audience.name);put("kind",draft.kind.name)
        draft.frame?.let { put("frame",Json.encodeToJsonElement(it)) }
        put("bytesBase64",kotlin.io.encoding.Base64.encode(bytes))
        poster?.let { put("posterBase64",kotlin.io.encoding.Base64.encode(it)) }
    })
}
