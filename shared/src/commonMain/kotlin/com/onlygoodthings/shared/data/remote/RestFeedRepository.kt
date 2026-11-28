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
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.put
import kotlinx.serialization.json.buildJsonObject

class RestFeedRepository(
    private val client: HttpClient,
    private val session: SessionStore,
) : FeedRepository {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    suspend fun recognitionCategories():List<com.onlygoodthings.shared.domain.RecognitionCategory> = post("/api/v1/social/categories",buildJsonObject {})

    suspend fun publicationPlaces(query:String,lang:String="es"):List<com.onlygoodthings.shared.domain.GeoHit> =
        post("/api/v1/geo/places/search",buildJsonObject { put("query",query);put("lang",lang) })

    suspend fun publicationPlace(point:com.onlygoodthings.shared.domain.GeoPoint,lang:String="es"):com.onlygoodthings.shared.domain.GeoHit =
        post("/api/v1/geo/places/reverse",buildJsonObject { put("latitude",point.latitude);put("longitude",point.longitude);put("lang",lang) })

    suspend fun publishRecognition(draft:com.onlygoodthings.shared.domain.RecognitionPostDraft):SocialPost =
        post("/api/v1/social/publish",json.encodeToJsonElement(com.onlygoodthings.shared.domain.RecognitionPostDraft.serializer(),draft) as JsonObject)

    suspend fun publishPublication(draft:com.onlygoodthings.shared.domain.PublicationDraft):SocialPost =
        post("/api/v1/social/publish",json.encodeToJsonElement(com.onlygoodthings.shared.domain.PublicationDraft.serializer(),draft) as JsonObject)

    suspend fun editPublication(id:String,body:String,hashtags:List<String>,fullName:String?,attribution:String,place:String?=null,point:com.onlygoodthings.shared.domain.GeoPoint?=null,pointChanged:Boolean=false):SocialPost =
        post("/api/v1/social/post/edit",buildJsonObject {
            put("postId",id);put("body",body);put("attributionUrl",attribution)
            place?.let { put("place",it) }
            if(pointChanged){put("latitude",point?.latitude?.let(::JsonPrimitive) ?: kotlinx.serialization.json.JsonNull);put("longitude",point?.longitude?.let(::JsonPrimitive) ?: kotlinx.serialization.json.JsonNull)}
            put("hashtags",buildJsonArray { hashtags.forEach { add(it) } })
            fullName?.takeIf { it.isNotBlank() }?.let { put("honoreeFullName",it) }
        })

    suspend fun publishGathering(draft:com.onlygoodthings.shared.domain.GatheringPostDraft):SocialPost =
        post("/api/v1/social/publish",json.encodeToJsonElement(com.onlygoodthings.shared.domain.GatheringPostDraft.serializer(),draft) as JsonObject)
    suspend fun editGathering(id:String,body:String,topic:String,place:String,start:Long?,attribution:String,point:com.onlygoodthings.shared.domain.GeoPoint?=null,pointChanged:Boolean=false):SocialPost =
        post("/api/v1/social/post/edit",buildJsonObject { put("postId",id);put("body",body);put("topic",topic);if(start!=null){put("place",place);put("eventStartsAtEpochMs",start)};put("attributionUrl",attribution);if(pointChanged){put("latitude",point?.latitude?.let(::JsonPrimitive) ?: kotlinx.serialization.json.JsonNull);put("longitude",point?.longitude?.let(::JsonPrimitive) ?: kotlinx.serialization.json.JsonNull)} })

    suspend fun getPost(id:String):SocialPost = post("/api/v1/social/post/get",buildJsonObject { put("postId",JsonPrimitive(id)) })

    suspend fun setNickname(nickname:String):com.onlygoodthings.shared.domain.UserLive = post("/api/v1/users/nickname",buildJsonObject { put("nickname",JsonPrimitive(nickname)) })

    suspend fun search(query:String,pageSize:Int=8):com.onlygoodthings.shared.domain.SearchPage = post("/api/v1/social/search",buildJsonObject { put("query",JsonPrimitive(query));put("pageSize",JsonPrimitive(pageSize)) })

    suspend fun ownCoupons():List<com.onlygoodthings.shared.domain.BusinessCoupon> = post("/api/v1/coupons/mine",buildJsonObject {})
    suspend fun claimCoupon(code:String) {
        post<JsonObject>("/api/v1/coupons/claim",buildJsonObject {put("code",JsonPrimitive(code))})
    }

    suspend fun blockedUsers():List<com.onlygoodthings.shared.domain.BlockedUser> = post("/api/v1/users/blocks",buildJsonObject {})
    suspend fun setUserBlocked(id:String,blocked:Boolean) {
        post<String>("/api/v1/users/${if(blocked)"block" else "unblock"}",buildJsonObject { put("userId",JsonPrimitive(id)) })
    }

    override suspend fun loadFeed(
        cursor: String?,
        pageSize: Int,
        mode: FeedMode,
        family: FeedTopicFamily?,
    ): List<SocialPost> = loadNearbyFeed(cursor,pageSize,mode,family,com.onlygoodthings.shared.domain.AdoptionProximity.DEFAULT_RADIUS_KM)

    suspend fun feedLocation(device:com.onlygoodthings.shared.domain.GeoPoint?=null):com.onlygoodthings.shared.domain.GeoPoint? {
        val data:JsonObject=post("/api/v1/social/feed-scope",buildJsonObject { device?.let { put("latitude",it.latitude);put("longitude",it.longitude) } })
        return com.onlygoodthings.shared.domain.AdoptionProximity.point((data["latitude"] as? JsonPrimitive)?.doubleOrNull,(data["longitude"] as? JsonPrimitive)?.doubleOrNull)
    }

    suspend fun discoveryScope(device:com.onlygoodthings.shared.domain.GeoPoint?=null):com.onlygoodthings.shared.domain.FeedDiscoveryScope = post("/api/v1/social/feed-scope",buildJsonObject { device?.let { put("latitude",it.latitude);put("longitude",it.longitude) } })
    suspend fun saveDiscoveryPlace(place:String,point:com.onlygoodthings.shared.domain.GeoPoint):com.onlygoodthings.shared.domain.FeedDiscoveryScope =
        post("/api/v1/users/discovery-place",buildJsonObject { put("place",place);put("latitude",point.latitude);put("longitude",point.longitude) })

    suspend fun loadNearbyFeed(cursor:String?,pageSize:Int,mode:FeedMode,family:FeedTopicFamily?,adoptionRadiusKm:Int,device:com.onlygoodthings.shared.domain.GeoPoint?=null):List<SocialPost> =
        post("/api/v1/social/feed", buildJsonObject {
            put("adoptionRadiusKm",JsonPrimitive(adoptionRadiusKm))
            device?.let { put("latitude",it.latitude);put("longitude",it.longitude) }
            put("pageSize", JsonPrimitive(pageSize))
            put("mode", JsonPrimitive(mode.name))
            cursor?.let { put("cursor", JsonPrimitive(it)) }
            family?.let { put("family", JsonPrimitive(it.name)) }
        })

    suspend fun visiblePostIds(ids: List<String>): List<String> =
        post("/api/v1/social/visibility", buildJsonObject {
            put("postIds", buildJsonArray { ids.forEach { add(it) } })
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

    override suspend fun diffusePost(postId: String, enabled: Boolean): SocialPost =
        post("/api/v1/social/post/diffuse", buildJsonObject { put("postId",postId);put("enabled",enabled) })

    override suspend fun repostAnecdote(anecdoteId: String): SocialPost =
        post("/api/v1/social/anecdote/repost", buildJsonObject {
            put("anecdoteId", JsonPrimitive(anecdoteId))
        })

    suspend fun editRecognition(postId:String,body:String,topic:String,attributionUrl:String,fullName:String,categoryId:String?):SocialPost =
        post("/api/v1/social/post/edit",buildJsonObject {
            put("postId",postId);put("body",body);put("topic",topic);put("attributionUrl",attributionUrl);put("honoreeFullName",fullName)
            categoryId?.let { put("categoryId",it) }
        })

    override suspend fun editPost(postId: String, body: String, topic: String?, attributionUrl: String?): SocialPost =
        post("/api/v1/social/post/edit", buildJsonObject {
            put("postId", JsonPrimitive(postId))
            put("body", JsonPrimitive(body))
            attributionUrl?.let { put("attributionUrl", JsonPrimitive(com.onlygoodthings.shared.domain.ContentAttribution.normalize(it).orEmpty())) }
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
        attributionUrl: String?,
    ): SocialPost = post("/api/v1/social/publish", buildJsonObject {
        put("body", JsonPrimitive(body))
        com.onlygoodthings.shared.domain.ContentAttribution.normalize(attributionUrl)?.let { put("attributionUrl", JsonPrimitive(it)) }
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
