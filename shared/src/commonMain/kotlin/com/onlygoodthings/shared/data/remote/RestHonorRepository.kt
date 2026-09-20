package com.onlygoodthings.shared.data.remote

import com.onlygoodthings.shared.data.SessionStore
import com.onlygoodthings.shared.domain.ApiResponse
import com.onlygoodthings.shared.domain.HonorChannel
import com.onlygoodthings.shared.domain.HonorMentionView
import com.onlygoodthings.shared.domain.PostPersonRole
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject

class RestHonorRepository(
    private val client: HttpClient,
    private val session: SessionStore,
) {
    suspend fun peek(token: String): HonorMentionView? = runCatching {
        post<HonorMentionView>("/api/v1/honor/peek", buildJsonObject {
            put("token", JsonPrimitive(token))
        }, auth = false)
    }.getOrNull()

    suspend fun issue(
        givenName: String,
        channel: HonorChannel,
        contact: String,
        token: String? = null,
    ): HonorMentionView? = runCatching {
        post<HonorMentionView>("/api/v1/honor/issue", buildJsonObject {
            put("givenName", JsonPrimitive(givenName))
            put("channel", JsonPrimitive(channel.name))
            put("contact", JsonPrimitive(contact))
            token?.let { put("token", JsonPrimitive(it)) }
        })
    }.getOrNull()

    suspend fun attach(token: String, postId: String, role: PostPersonRole): HonorMentionView? = runCatching {
        post<HonorMentionView>("/api/v1/honor/attach", buildJsonObject {
            put("token", JsonPrimitive(token))
            put("postId", JsonPrimitive(postId))
            put("role", JsonPrimitive(role.name))
        })
    }.getOrNull()

    suspend fun claim(token: String): HonorMentionView? = runCatching {
        post<HonorMentionView>("/api/v1/honor/claim", buildJsonObject {
            put("token", JsonPrimitive(token))
        })
    }.getOrNull()

    private suspend inline fun <reified T> post(
        path: String,
        body: kotlinx.serialization.json.JsonObject,
        auth: Boolean = true,
    ): T {
        val response: ApiResponse<T> = client.post("${session.apiBaseUrl}$path") {
            contentType(ContentType.Application.Json)
            if (auth) session.firebaseJwt?.let { header("Authorization", "Bearer $it") }
            setBody(body)
        }.body()
        return response.data ?: error(response.message ?: "Respuesta vacía")
    }
}
