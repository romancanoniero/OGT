package com.onlygoodthings.backend.social

import com.onlygoodthings.backend.realtime.RealtimeHub
import com.onlygoodthings.shared.protocol.WsCodec
import com.onlygoodthings.backend.auth.AuthPrincipal
import com.onlygoodthings.backend.http.*
import com.onlygoodthings.backend.infra.Database
import com.onlygoodthings.shared.domain.*
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.principal
import io.ktor.server.response.*
import io.ktor.server.routing.*

internal fun Map<String,Any?>.invitationDrafts():List<PostInvitationDraft> {
    val source=this["invitations"] ?: return emptyList()
    require(source is List<*>) { "Invitaciones inválidas" }
    return source.map { raw ->
        require(raw is Map<*,*>) { "Invitación inválida" }
        PostInvitationDraft(raw["recipientName"] as? String ?: "",raw["purpose"] as? String ?: "PARTICIPATION",raw["channel"] as? String ?: "",raw["contact"] as? String ?: "",raw["recipientUserId"] as? String)
    }
}

fun Route.publicPostInvitationRoutes(db:Database) {
    val invites=PostInvitations(db)
    get("/i/{token}") {
        val token=call.parameters["token"].orEmpty()
        require(Regex("^[A-Za-z0-9_-]{32,64}$").matches(token)) { "Invitación inválida" }
        val invitation=invites.peek(token)
        if(invitation==null) return@get call.respondText("Esta invitación venció o no está disponible.",ContentType.Text.Plain,HttpStatusCode.NotFound)
        val web="/#/invitacion/$token"
        val play="https://play.google.com/store/apps/details?id=$OgtAndroidPackage&referrer=invitation%3D$token"
        val ios=System.getenv("OGT_IOS_STORE_URL") ?: honorAppStoreUrl(System.getenv("OGT_IOS_APP_ID"))
        call.respondText("""<!doctype html><html lang="es"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><meta name="referrer" content="no-referrer"><title>Te invitaron a OGT</title></head><body style="font-family:system-ui;max-width:480px;margin:40px auto;padding:20px"><h1>Tenés una invitación a OGT</h1><p>Podés ingresar o registrarte y volver a esta publicación.</p><p><a href="ogt://i/$token">Abrir en la app</a></p><p><a href="$web">Continuar desde la web</a></p><p><a href="$play">Instalar en Android</a></p><p><a href="${ios.replace("&","&amp;").replace("\"","&quot;").replace("<","&lt;")}">Instalar en iPhone</a></p><p>Después de instalar, volvé a este enlace para continuar. En Android también se conserva la invitación desde la tienda.</p></body></html>""",ContentType.Text.Html)
    }
    post("/api/v1/social/invitations/peek") {
        val token=JsonBody.receiveMap(call).reqString("token")
        val view=invites.peek(token) ?: return@post call.respond(HttpStatusCode.NotFound,ApiResponse.fail<Unit>("La invitación venció o no está disponible.","INVITATION_UNAVAILABLE"))
        call.respond(ApiResponse.ok(view))
    }
}

fun Route.postInvitationRoutes(db:Database,hub:RealtimeHub) {
    val invites=PostInvitations(db)
    post("/api/v1/social/invitations/inbox") {
        val me=call.principal<AuthPrincipal>() ?: return@post call.respond(HttpStatusCode.Unauthorized)
        call.respond(ApiResponse.ok(invites.inbox(me.userId)))
    }
    post("/api/v1/social/invitations/create") {
        val me=call.principal<AuthPrincipal>() ?: return@post call.respond(HttpStatusCode.Unauthorized)
        val data=JsonBody.receiveMap(call)
        call.respond(ApiResponse.ok(invites.create(me.userId,data.reqString("postId"),data.invitationDrafts())))
    }
    post("/api/v1/social/invitations/list") {
        val me=call.principal<AuthPrincipal>() ?: return@post call.respond(HttpStatusCode.Unauthorized)
        call.respond(ApiResponse.ok(invites.mine(me.userId,JsonBody.receiveMap(call).reqString("postId"))))
    }
    post("/api/v1/social/invitations/accept") {
        val me=call.principal<AuthPrincipal>() ?: return@post call.respond(HttpStatusCode.Unauthorized)
        val data=JsonBody.receiveMap(call)
        val result=invites.accept(data.reqString("token"),me.userId,data.optString("action")=="DECLINE")
        if(result.status=="ACCEPTED" && result.purpose=="RECOGNITION") {
            SocialSqlRepository(db).byId(result.postId,me.userId)?.let { post ->
                hub.projectSocialPost(post.id,WsCodec.json.encodeToString(SocialLiveCounters.serializer(),SocialLiveCounters(
                    post.id,post.commentCount,post.impactCount,honoreeName=post.honoreeName,categoryId=post.categoryId,publicationKind=post.publicationKind,
                )))
            }
        }
        call.respond(ApiResponse.ok(result))
    }
    for(action in listOf("retry","revoke","share-opened"))post("/api/v1/social/invitations/$action") {
        val me=call.principal<AuthPrincipal>() ?: return@post call.respond(HttpStatusCode.Unauthorized)
        val id=JsonBody.receiveMap(call).reqString("id")
        when(action) { "retry" -> invites.retry(me.userId,id); "share-opened" -> invites.shareOpened(me.userId,id); else -> invites.revoke(me.userId,id) }
        call.respond(ApiResponse.ok("ok"))
    }
}
