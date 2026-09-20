package com.onlygoodthings.shared.data.remote

import com.onlygoodthings.shared.data.SessionStore
import com.onlygoodthings.shared.domain.ApiResponse
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject

class RestNoticeRepository(
    private val client: HttpClient,
    private val session: SessionStore,
) {
    suspend fun registerToken(token: String, platform: String): Boolean = runCatching {
        post<String>("/api/v1/notifications/register", buildJsonObject {
            put("token", JsonPrimitive(token))
            put("platform", JsonPrimitive(platform))
        })
        true
    }.getOrDefault(false)

    suspend fun notifyMention(recipientUserId: String, postId: String, role: String): Boolean = runCatching {
        post<Int>("/api/v1/notifications/mention", buildJsonObject {
            put("recipientUserId", JsonPrimitive(recipientUserId))
            put("postId", JsonPrimitive(postId))
            put("role", JsonPrimitive(role))
        })
        true
    }.getOrDefault(false)

    private suspend inline fun <reified T> post(path: String, body: kotlinx.serialization.json.JsonObject): T {
        val response: ApiResponse<T> = client.post("${session.apiBaseUrl}$path") {
            contentType(ContentType.Application.Json)
            session.firebaseJwt?.let { header("Authorization", "Bearer $it") }
            setBody(body)
        }.body()
        return response.data ?: error(response.message ?: "Respuesta vacía")
    }
}
