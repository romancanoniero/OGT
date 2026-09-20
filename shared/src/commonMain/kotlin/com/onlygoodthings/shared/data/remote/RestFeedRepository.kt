package com.onlygoodthings.shared.data.remote

import com.onlygoodthings.shared.data.FeedRepository
import com.onlygoodthings.shared.data.SessionStore
import com.onlygoodthings.shared.domain.ApiResponse
import com.onlygoodthings.shared.domain.FeedEventKind
import com.onlygoodthings.shared.domain.FeedMode
import com.onlygoodthings.shared.domain.FeedTopicFamily
import com.onlygoodthings.shared.domain.SocialComment
import com.onlygoodthings.shared.domain.SocialPost
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject

class RestFeedRepository(
    private val client: HttpClient,
    private val session: SessionStore,
) : FeedRepository {

    override suspend fun loadFeed(
        cursor: String?,
        pageSize: Int,
        mode: FeedMode,
        family: FeedTopicFamily?,
    ): List<SocialPost> =
        post("/api/v1/social/feed", buildJsonObject {
            put("pageSize", JsonPrimitive(pageSize))
            put("mode", JsonPrimitive(mode.name))
            cursor?.let { put("cursor", JsonPrimitive(it)) }
            family?.let { put("family", JsonPrimitive(it.name)) }
        })

    override suspend fun clapPost(postId: String): SocialPost =
        post("/api/v1/social/impact", buildJsonObject {
            put("postId", JsonPrimitive(postId))
        })

    override suspend fun loadComments(postId: String): List<SocialComment> =
        post("/api/v1/social/comments", buildJsonObject {
            put("postId", JsonPrimitive(postId))
        })

    override suspend fun addComment(
        postId: String,
        body: String,
        parentCommentId: String?,
    ): SocialComment = post("/api/v1/social/comment", buildJsonObject {
        put("postId", JsonPrimitive(postId))
        put("body", JsonPrimitive(body))
        parentCommentId?.let { put("parentCommentId", JsonPrimitive(it)) }
    })

    override suspend fun reportPost(postId: String, reason: String, details: String?) {
        val response: ApiResponse<String> = client.post("${session.apiBaseUrl}/api/v1/social/report") {
            contentType(ContentType.Application.Json)
            session.firebaseJwt?.let { header("Authorization", "Bearer $it") }
            setBody(buildJsonObject {
                put("postId", JsonPrimitive(postId))
                put("reason", JsonPrimitive(reason))
                details?.let { put("details", JsonPrimitive(it)) }
            })
        }.body()
        if (!response.success) error(response.message ?: "No se pudo reportar")
    }

    override suspend fun recordFeedEvent(postId: String, kind: FeedEventKind, dwellMs: Int?) {
        post<String>("/api/v1/social/feed-event", buildJsonObject {
            put("postId", JsonPrimitive(postId))
            put("kind", JsonPrimitive(kind.name))
            dwellMs?.let { put("dwellMs", JsonPrimitive(it)) }
        })
    }

    private suspend inline fun <reified T> post(path: String, body: JsonObject): T {
        val response: ApiResponse<T> = client.post("${session.apiBaseUrl}$path") {
            contentType(ContentType.Application.Json)
            session.firebaseJwt?.let { header("Authorization", "Bearer $it") }
            setBody(body)
        }.body()
        return response.data ?: error(response.message ?: "Respuesta vacía")
    }
}
