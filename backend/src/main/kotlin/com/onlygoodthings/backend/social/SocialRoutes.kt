package com.onlygoodthings.backend.social

import com.onlygoodthings.backend.auth.AuthPrincipal
import com.onlygoodthings.backend.http.JsonBody
import com.onlygoodthings.backend.http.optInt
import com.onlygoodthings.backend.http.optString
import com.onlygoodthings.backend.http.reqString
import com.onlygoodthings.backend.realtime.RealtimeHub
import com.onlygoodthings.shared.domain.ApiResponse
import com.onlygoodthings.shared.domain.FeedEventKind
import com.onlygoodthings.shared.domain.FeedMode
import com.onlygoodthings.shared.domain.SocialComment
import com.onlygoodthings.shared.domain.SocialLiveCounters
import com.onlygoodthings.shared.protocol.WsCodec
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.auth.principal
import io.ktor.server.request.receiveText
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.post

fun Route.socialRoutes(repository: SocialSqlRepository, hub: RealtimeHub) {
    post("/api/v1/social/feed") {
        val principal = call.principal<AuthPrincipal>() ?: return@post call.respond(
            HttpStatusCode.Unauthorized,
            ApiResponse.fail<Unit>("Token requerido", "UNAUTHENTICATED"),
        )
        val objectMapper = JsonBody.objectMapper
        val dataMap = objectMapper.readValue(call.receiveText(), object : com.fasterxml.jackson.core.type.TypeReference<Map<String, Any?>>() {})
        val pageSize = dataMap.optInt("pageSize", 20)
        val cursor = dataMap.optString("cursor")
        val mode = runCatching { FeedMode.valueOf((dataMap.optString("mode") ?: "HOME").uppercase()) }
            .getOrDefault(FeedMode.HOME)
        val family = dataMap.optString("family")?.let {
            val key = it.uppercase()
            when (key) {
                "NEIGHBORHOOD", "BARRIO" -> com.onlygoodthings.shared.domain.FeedTopicFamily.COMMUNITY
                else -> runCatching { com.onlygoodthings.shared.domain.FeedTopicFamily.valueOf(key) }.getOrNull()
            }
        }
        call.respond(ApiResponse.ok(repository.feed(principal.userId, mode, pageSize, cursor, family)))
    }

    post("/api/v1/social/feed-event") {
        val principal = call.principal<AuthPrincipal>() ?: return@post call.respond(
            HttpStatusCode.Unauthorized,
            ApiResponse.fail<Unit>("Token requerido", "UNAUTHENTICATED"),
        )
        val objectMapper = JsonBody.objectMapper
        val dataMap = objectMapper.readValue(call.receiveText(), object : com.fasterxml.jackson.core.type.TypeReference<Map<String, Any?>>() {})
        val kind = runCatching { FeedEventKind.valueOf(dataMap.reqString("kind").uppercase()) }.getOrNull()
            ?: return@post call.respond(HttpStatusCode.BadRequest, ApiResponse.fail<Unit>("kind inválido", "BAD_REQUEST"))
        repository.recordFeedEvent(
            viewerId = principal.userId,
            postId = dataMap.reqString("postId"),
            kind = kind,
            dwellMs = dataMap.optInt("dwellMs", -1).takeIf { it >= 0 },
        )
        call.respond(ApiResponse.ok("ok", "Evento registrado"))
    }

    post("/api/v1/social/impact") {
        val principal = call.principal<AuthPrincipal>() ?: return@post call.respond(
            HttpStatusCode.Unauthorized,
            ApiResponse.fail<Unit>("Token requerido", "UNAUTHENTICATED"),
        )
        val objectMapper = JsonBody.objectMapper
        val dataMap = objectMapper.readValue(call.receiveText(), object : com.fasterxml.jackson.core.type.TypeReference<Map<String, Any?>>() {})
        val postId = dataMap.reqString("postId")
        val updated = repository.clap(postId, principal.userId)
            ?: return@post call.respond(HttpStatusCode.NotFound, ApiResponse.fail<Unit>("Post inexistente", "NOT_FOUND"))
        hub.projectSocialPost(
            updated.id,
            WsCodec.json.encodeToString(
                SocialLiveCounters.serializer(),
                SocialLiveCounters(updated.id, updated.commentCount, updated.impactCount),
            ),
        )
        call.respond(ApiResponse.ok(updated, "Impacto registrado"))
    }

    post("/api/v1/social/comments") {
        if (call.principal<AuthPrincipal>() == null) {
            return@post call.respond(HttpStatusCode.Unauthorized, ApiResponse.fail<Unit>("Token requerido", "UNAUTHENTICATED"))
        }
        val objectMapper = JsonBody.objectMapper
        val dataMap = objectMapper.readValue(call.receiveText(), object : com.fasterxml.jackson.core.type.TypeReference<Map<String, Any?>>() {})
        call.respond(ApiResponse.ok(repository.comments(dataMap.reqString("postId"))))
    }

    post("/api/v1/social/comment") {
        val principal = call.principal<AuthPrincipal>() ?: return@post call.respond(
            HttpStatusCode.Unauthorized,
            ApiResponse.fail<Unit>("Token requerido", "UNAUTHENTICATED"),
        )
        val objectMapper = JsonBody.objectMapper
        val dataMap = objectMapper.readValue(call.receiveText(), object : com.fasterxml.jackson.core.type.TypeReference<Map<String, Any?>>() {})
        val comment = repository.addComment(
            postId = dataMap.reqString("postId"),
            userId = principal.userId,
            body = dataMap.reqString("body"),
            parentId = dataMap.optString("parentCommentId"),
        )
        repository.countersOf(comment.postId)?.let { live ->
            hub.projectSocialPost(live.id, WsCodec.json.encodeToString(SocialLiveCounters.serializer(), live))
        }
        hub.projectSocialComment(
            comment.postId,
            comment.id,
            WsCodec.json.encodeToString(SocialComment.serializer(), comment),
        )
        call.respond(ApiResponse.ok(comment, "Comentario publicado"))
    }

    post("/api/v1/social/report") {
        val principal = call.principal<AuthPrincipal>() ?: return@post call.respond(
            HttpStatusCode.Unauthorized,
            ApiResponse.fail<Unit>("Token requerido", "UNAUTHENTICATED"),
        )
        val objectMapper = JsonBody.objectMapper
        val dataMap = objectMapper.readValue(call.receiveText(), object : com.fasterxml.jackson.core.type.TypeReference<Map<String, Any?>>() {})
        repository.report(
            reporterId = principal.userId,
            postId = dataMap.reqString("postId"),
            reason = dataMap.reqString("reason"),
            details = dataMap.optString("details"),
        )
        call.respond(ApiResponse.ok("ok", "Reporte recibido"))
    }
}

/*
Postman — feed rankeado (HOME) y tubo del grafo (FOLLOWING)

POST {{base}}/api/v1/social/feed
Authorization: Bearer {{jwt}}
{
  "pageSize": 20,
  "cursor": "0",
  "mode": "HOME",
  "family": "PETS"
}

family opcional: PETS | COMMUNITY | FOOD | OCEAN | NEWS | OTHER
(NEIGHBORHOOD y BARRIO se aceptan como alias de COMMUNITY)
Sin family, HOME mezcla afinidad + 1/5 de exploración.

POST {{base}}/api/v1/social/feed
Authorization: Bearer {{jwt}}
{
  "pageSize": 20,
  "mode": "FOLLOWING"
}

POST {{base}}/api/v1/social/feed-event
Authorization: Bearer {{jwt}}
{
  "postId": "55555555-5555-5555-5555-555555555555",
  "kind": "IMPRESSION"
}

POST {{base}}/api/v1/social/feed-event
Authorization: Bearer {{jwt}}
{
  "postId": "55555555-5555-5555-5555-555555555555",
  "kind": "DWELL",
  "dwellMs": 4200
}

kind: IMPRESSION | DWELL | CLAP | COMMENT | PROFILE_TAP | SHARE | HIDE | SKIP

El feed hidrata `media` (1 a 10 piezas IMAGE/VIDEO) y `sourceUrl` si el post es una noticia sembrada.
Cada post exige al menos una foto o un video, como Instagram.

POST {{base}}/api/v1/social/comment
Authorization: Bearer {{jwt}}
{
  "postId": "55555555-5555-5555-5555-555555555555",
  "body": "Gracias por sumar",
  "parentCommentId": null
}

Tras el INSERT, el backend proyecta `ogt/social/posts/{id}` y `.../comments/{commentId}`
al árbol de db-kmp-sdk (Redis `ogt:tree:…`). El cliente observa con OgtRealtime.
*/
