package com.onlygoodthings.shared.data.remote

import com.onlygoodthings.shared.data.SessionStore
import com.onlygoodthings.shared.domain.*
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.*
import io.ktor.http.*
import kotlinx.serialization.json.*

class RestPostInvitations(private val client:HttpClient,private val session:SessionStore) {
    private val json=Json { ignoreUnknownKeys=true;encodeDefaults=true }
    suspend fun peek(token:String):PostInvitationView = request("peek",buildJsonObject { put("token",token) },false)
    suspend fun accept(token:String,decline:Boolean=false):PostInvitationView = request("accept",buildJsonObject { put("token",token);put("action",if(decline)"DECLINE" else "ACCEPT") })
    suspend fun inbox():List<PostInvitationView> = request("inbox",buildJsonObject {})
    suspend fun list(postId:String):List<PostInvitationView> = request("list",buildJsonObject { put("postId",postId) })
    suspend fun create(postId:String,drafts:List<PostInvitationDraft>):List<PostInvitationView> = request("create",buildJsonObject {
        put("postId",postId);put("invitations",JsonArray(drafts.map { json.encodeToJsonElement(PostInvitationDraft.serializer(),it) }))
    })
    suspend fun shareOpened(id:String) { request<String>("share-opened",buildJsonObject { put("id",id) }) }
    suspend fun revoke(id:String) { request<String>("revoke",buildJsonObject { put("id",id) }) }
    private suspend inline fun <reified T> request(path:String,data:JsonObject,auth:Boolean=true):T {
        val result:ApiResponse<T> = client.post("${session.apiBaseUrl}/api/v1/social/invitations/$path") {
            contentType(ContentType.Application.Json);setBody(data)
            if(auth)session.firebaseJwt?.let { header("Authorization","Bearer $it") }
        }.body()
        check(result.success) { result.message ?: "No se pudo completar la invitación." }
        return result.data ?: error("La invitación no está disponible.")
    }
}
