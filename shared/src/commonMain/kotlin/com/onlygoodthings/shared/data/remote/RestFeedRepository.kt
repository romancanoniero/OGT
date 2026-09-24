package com.onlygoodthings.shared.data.remote

import com.onlygoodthings.shared.data.FeedRepository
import com.onlygoodthings.shared.data.SessionStore
import com.onlygoodthings.shared.domain.ApiResponse
import com.onlygoodthings.shared.domain.FeedEventKind
import com.onlygoodthings.shared.domain.FeedMode
import com.onlygoodthings.shared.domain.FeedTopicFamily
import com.onlygoodthings.shared.domain.PostMediaItem
import com.onlygoodthings.shared.domain.SocialAnecdote
import com.onlygoodthings.shared.domain.SocialComment
import com.onlygoodthings.shared.domain.SocialPost
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject

class RestFeedRepository(
    private val client: HttpClient,
    private val session: SessionStore,
) : FeedRepository {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

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
        anecdoteId: String?,
    ): SocialComment = post("/api/v1/social/comment", buildJsonObject {
        put("postId", JsonPrimitive(postId))
        put("body", JsonPrimitive(body))
        parentCommentId?.let { put("parentCommentId", JsonPrimitive(it)) }
        anecdoteId?.let { put("anecdoteId", JsonPrimitive(it)) }
    })

    override suspend fun editComment(commentId: String, body: String): SocialComment =
        post("/api/v1/social/comment/edit", buildJsonObject {
            put("commentId", JsonPrimitive(commentId))
            put("body", JsonPrimitive(body))
        })

    override suspend fun deleteComment(commentId: String) {
        post<String>("/api/v1/social/comment/delete", buildJsonObject {
            put("commentId", JsonPrimitive(commentId))
        })
    }

    override suspend fun addAnecdote(postId: String, body: String, sourceUrl: String?): SocialAnecdote =
        post("/api/v1/social/anecdote", buildJsonObject {
            put("postId", JsonPrimitive(postId))
            put("body", JsonPrimitive(body))
            sourceUrl?.let { put("sourceUrl", JsonPrimitive(it)) }
        })

    override suspend fun editAnecdote(anecdoteId: String, body: String): SocialAnecdote =
        post("/api/v1/social/anecdote/edit", buildJsonObject {
            put("anecdoteId", JsonPrimitive(anecdoteId))
            put("body", JsonPrimitive(body))
        })

    override suspend fun deleteAnecdote(anecdoteId: String) {
        post<String>("/api/v1/social/anecdote/delete", buildJsonObject {
            put("anecdoteId", JsonPrimitive(anecdoteId))
        })
    }

    override suspend fun clapAnecdote(anecdoteId: String): SocialAnecdote =
        post("/api/v1/social/anecdote/impact", buildJsonObject {
            put("anecdoteId", JsonPrimitive(anecdoteId))
        })

    override suspend fun heartAnecdote(anecdoteId: String): SocialAnecdote =
        post("/api/v1/social/anecdote/heart", buildJsonObject {
            put("anecdoteId", JsonPrimitive(anecdoteId))
        })

    override suspend fun repostAnecdote(anecdoteId: String): SocialPost =
        post("/api/v1/social/anecdote/repost", buildJsonObject {
            put("anecdoteId", JsonPrimitive(anecdoteId))
        })

    override suspend fun editPost(postId: String, body: String, topic: String?): SocialPost =
        post("/api/v1/social/post/edit", buildJsonObject {
            put("postId", JsonPrimitive(postId))
            put("body", JsonPrimitive(body))
            topic?.let { put("topic", JsonPrimitive(it)) }
        })

    override suspend fun deletePost(postId: String) {
        post<String>("/api/v1/social/post/delete", buildJsonObject {
            put("postId", JsonPrimitive(postId))
        })
    }

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

    override suspend fun publishPost(
        body: String,
        topic: String,
        media: List<PostMediaItem>,
        latitude: Double?,
        longitude: Double?,
        protagonistUserId: String?,
        participantUserIds: List<String>,
        honoreeName: String?,
    ): SocialPost = post("/api/v1/social/publish", buildJsonObject {
        put("body", JsonPrimitive(body))
        put("topic", JsonPrimitive(topic))
        latitude?.let { put("latitude", JsonPrimitive(it)) }
        longitude?.let { put("longitude", JsonPrimitive(it)) }
        protagonistUserId?.let { put("protagonistUserId", JsonPrimitive(it)) }
        honoreeName?.takeIf { it.isNotBlank() }?.let { put("honoreeName", JsonPrimitive(it)) }
        if (participantUserIds.isNotEmpty()) {
            put("participantUserIds", JsonArray(participantUserIds.map { JsonPrimitive(it) }))
        }
        put(
            "media",
            buildJsonArray {
                media.forEach { item ->
                    add(
                        buildJsonObject {
                            put("kind", JsonPrimitive(item.kind.name))
                            put("url", JsonPrimitive(item.url))
                            item.posterUrl?.let { put("posterUrl", JsonPrimitive(it)) }
                            item.altText?.let { put("altText", JsonPrimitive(it)) }
                        },
                    )
                }
            },
        )
    })

    override suspend fun follow(userId: String) {
        post<String>("/api/v1/social/follow", buildJsonObject {
            put("userId", JsonPrimitive(userId))
        })
    }

    override suspend fun unfollow(userId: String) {
        post<String>("/api/v1/social/unfollow", buildJsonObject {
            put("userId", JsonPrimitive(userId))
        })
    }

    override suspend fun recordFeedEvent(postId: String, kind: FeedEventKind, dwellMs: Int?) {
        post<String>("/api/v1/social/feed-event", buildJsonObject {
            put("postId", JsonPrimitive(postId))
            put("kind", JsonPrimitive(kind.name))
            dwellMs?.let { put("dwellMs", JsonPrimitive(it)) }
        })
    }

    private suspend inline fun <reified T> post(path: String, body: JsonObject): T {
        val raw = client.post("${session.apiBaseUrl}$path") {
            contentType(ContentType.Application.Json)
            session.firebaseJwt?.let { header("Authorization", "Bearer $it") }
            setBody(body)
        }.bodyAsText()
        val response = json.decodeFromString<ApiResponse<T>>(raw)
        return response.data ?: error(response.message ?: "Respuesta vacía")
    }
}
