package com.onlygoodthings.backend.social

import com.onlygoodthings.backend.auth.AuthPrincipal
import com.onlygoodthings.backend.http.JsonBody
import com.onlygoodthings.backend.http.optDouble
import com.onlygoodthings.backend.http.optInt
import com.onlygoodthings.backend.http.optString
import com.onlygoodthings.backend.http.reqString
import com.onlygoodthings.backend.realtime.RealtimeHub
import com.onlygoodthings.shared.domain.ApiResponse
import com.onlygoodthings.shared.domain.FeedEventKind
import com.onlygoodthings.shared.domain.FeedMode
import com.onlygoodthings.shared.domain.MediaKind
import com.onlygoodthings.shared.domain.MoneyLedgerKind
import com.onlygoodthings.shared.domain.PostMediaItem
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

    post("/api/v1/social/publish") {
        val principal = call.principal<AuthPrincipal>() ?: return@post call.respond(
            HttpStatusCode.Unauthorized,
            ApiResponse.fail<Unit>("Token requerido", "UNAUTHENTICATED"),
        )
        val dataMap = JsonBody.receiveMap(call)
        val media = dataMap.optMedia()
        val participants = (dataMap["participantUserIds"] as? List<*>)?.mapNotNull { it as? String }.orEmpty()
        val saved = repository.publish(
            userId = principal.userId,
            body = dataMap.reqString("body"),
            topic = dataMap.optString("topic") ?: "Comunidad",
            media = media,
            latitude = dataMap.optDouble("latitude"),
            longitude = dataMap.optDouble("longitude"),
            protagonistUserId = dataMap.optString("protagonistUserId"),
            participantUserIds = participants,
            honoreeName = dataMap.optString("honoreeName"),
        )
        hub.projectSocialPost(
            saved.id,
            WsCodec.json.encodeToString(
                SocialLiveCounters.serializer(),
                SocialLiveCounters(
                    saved.id,
                    saved.commentCount,
                    saved.impactCount,
                    authorUserId = saved.authorId,
                    body = saved.body,
                    tag = saved.topic,
                    createdAtEpochMs = saved.createdAtEpochMs,
                ),
            ),
        )
        call.respond(ApiResponse.ok(saved, "Publicado en el feed, sin pagar alcance"))
    }

    post("/api/v1/social/follow") {
        val principal = call.principal<AuthPrincipal>() ?: return@post call.respond(
            HttpStatusCode.Unauthorized,
            ApiResponse.fail<Unit>("Token requerido", "UNAUTHENTICATED"),
        )
        val dataMap = JsonBody.receiveMap(call)
        repository.follow(principal.userId, dataMap.reqString("userId"))
        call.respond(ApiResponse.ok("ok", "Ahora seguís a esa persona"))
    }

    post("/api/v1/social/unfollow") {
        val principal = call.principal<AuthPrincipal>() ?: return@post call.respond(
            HttpStatusCode.Unauthorized,
            ApiResponse.fail<Unit>("Token requerido", "UNAUTHENTICATED"),
        )
        val dataMap = JsonBody.receiveMap(call)
        repository.unfollow(principal.userId, dataMap.reqString("userId"))
        call.respond(ApiResponse.ok("ok", "Dejaste de seguir"))
    }

    post("/api/v1/social/following") {
        val principal = call.principal<AuthPrincipal>() ?: return@post call.respond(
            HttpStatusCode.Unauthorized,
            ApiResponse.fail<Unit>("Token requerido", "UNAUTHENTICATED"),
        )
        call.respond(ApiResponse.ok(repository.followingIds(principal.userId)))
    }

    post("/api/v1/social/search") {
        val principal = call.principal<AuthPrincipal>() ?: return@post call.respond(
            HttpStatusCode.Unauthorized,
            ApiResponse.fail<Unit>("Token requerido", "UNAUTHENTICATED"),
        )
        val dataMap = JsonBody.receiveMap(call)
        call.respond(
            ApiResponse.ok(
                repository.search(
                    viewerId = principal.userId,
                    query = dataMap.optString("query").orEmpty(),
                    pageSize = dataMap.optInt("pageSize", 20),
                ),
            ),
        )
    }

    post("/api/v1/social/posts-by-author") {
        val principal = call.principal<AuthPrincipal>() ?: return@post call.respond(
            HttpStatusCode.Unauthorized,
            ApiResponse.fail<Unit>("Token requerido", "UNAUTHENTICATED"),
        )
        val dataMap = JsonBody.receiveMap(call)
        call.respond(
            ApiResponse.ok(
                repository.postsByAuthor(
                    viewerId = principal.userId,
                    authorId = dataMap.reqString("userId"),
                    pageSize = dataMap.optInt("pageSize", 20),
                    cursor = dataMap.optString("cursor"),
                ),
            ),
        )
    }

    post("/api/v1/users/profile") {
        val principal = call.principal<AuthPrincipal>() ?: return@post call.respond(
            HttpStatusCode.Unauthorized,
            ApiResponse.fail<Unit>("Token requerido", "UNAUTHENTICATED"),
        )
        val dataMap = JsonBody.receiveMap(call)
        val userId = dataMap.optString("userId")?.takeIf { it.isNotBlank() } ?: principal.userId
        val card = repository.neighborCard(principal.userId, userId)
            ?: return@post call.respond(HttpStatusCode.NotFound, ApiResponse.fail<Unit>("Vecino inexistente", "NOT_FOUND"))
        call.respond(ApiResponse.ok(card))
    }

    post("/api/v1/social/promote") {
        val principal = call.principal<AuthPrincipal>() ?: return@post call.respond(
            HttpStatusCode.Unauthorized,
            ApiResponse.fail<Unit>("Token requerido", "UNAUTHENTICATED"),
        )
        val dataMap = JsonBody.receiveMap(call)
        val updated = repository.spendReach(
            postId = dataMap.reqString("postId"),
            payerUserId = principal.userId,
            amountCents = (dataMap["amountCents"] as? Number)?.toLong() ?: error("Campo requerido: amountCents"),
            kind = MoneyLedgerKind.PROMOTED_REACH,
            companyId = dataMap.optString("companyId"),
            campaignId = dataMap.optString("campaignId"),
        ) ?: return@post call.respond(HttpStatusCode.NotFound, ApiResponse.fail<Unit>("Post inexistente", "NOT_FOUND"))
        call.respond(ApiResponse.ok(updated, "Promoción identificada. No suma reputación."))
    }

    post("/api/v1/ads/spend") {
        val principal = call.principal<AuthPrincipal>() ?: return@post call.respond(
            HttpStatusCode.Unauthorized,
            ApiResponse.fail<Unit>("Token requerido", "UNAUTHENTICATED"),
        )
        val dataMap = JsonBody.receiveMap(call)
        val updated = repository.spendReach(
            postId = dataMap.reqString("postId"),
            payerUserId = principal.userId,
            amountCents = (dataMap["amountCents"] as? Number)?.toLong() ?: error("Campo requerido: amountCents"),
            kind = MoneyLedgerKind.AD_IMPRESSION,
            companyId = dataMap.optString("companyId"),
            campaignId = dataMap.optString("campaignId"),
        ) ?: return@post call.respond(HttpStatusCode.NotFound, ApiResponse.fail<Unit>("Post inexistente", "NOT_FOUND"))
        call.respond(ApiResponse.ok(updated, "Publicidad. No es impacto OGT."))
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
        call.respond(ApiResponse.ok(repository.comments(dataMap.reqString("postId"), dataMap.optString("anecdoteId"))))
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
            anecdoteId = dataMap.optString("anecdoteId"),
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

    post("/api/v1/social/comment/edit") {
        val principal = call.principal<AuthPrincipal>() ?: return@post call.respond(
            HttpStatusCode.Unauthorized,
            ApiResponse.fail<Unit>("Token requerido", "UNAUTHENTICATED"),
        )
        val dataMap = JsonBody.receiveMap(call)
        val comment = repository.editComment(
            commentId = dataMap.reqString("commentId"),
            userId = principal.userId,
            body = dataMap.reqString("body"),
        )
        hub.projectSocialComment(
            comment.postId,
            comment.id,
            WsCodec.json.encodeToString(SocialComment.serializer(), comment),
        )
        call.respond(ApiResponse.ok(comment, "Comentario editado"))
    }

    post("/api/v1/social/comment/delete") {
        val principal = call.principal<AuthPrincipal>() ?: return@post call.respond(
            HttpStatusCode.Unauthorized,
            ApiResponse.fail<Unit>("Token requerido", "UNAUTHENTICATED"),
        )
        val dataMap = JsonBody.receiveMap(call)
        repository.deleteComment(dataMap.reqString("commentId"), principal.userId)
        call.respond(ApiResponse.ok("ok", "Comentario borrado"))
    }

    post("/api/v1/social/anecdote") {
        val principal = call.principal<AuthPrincipal>() ?: return@post call.respond(
            HttpStatusCode.Unauthorized,
            ApiResponse.fail<Unit>("Token requerido", "UNAUTHENTICATED"),
        )
        val dataMap = JsonBody.receiveMap(call)
        val anecdote = repository.addAnecdote(
            postId = dataMap.reqString("postId"),
            userId = principal.userId,
            body = dataMap.reqString("body"),
            sourceUrl = dataMap.optString("sourceUrl"),
        )
        call.respond(ApiResponse.ok(anecdote, "Anécdota sumada al homenaje"))
    }

    post("/api/v1/social/anecdote/edit") {
        val principal = call.principal<AuthPrincipal>() ?: return@post call.respond(
            HttpStatusCode.Unauthorized,
            ApiResponse.fail<Unit>("Token requerido", "UNAUTHENTICATED"),
        )
        val dataMap = JsonBody.receiveMap(call)
        val anecdote = repository.editAnecdote(
            anecdoteId = dataMap.reqString("anecdoteId"),
            userId = principal.userId,
            body = dataMap.reqString("body"),
        )
        call.respond(ApiResponse.ok(anecdote, "Anécdota editada"))
    }

    post("/api/v1/social/anecdote/delete") {
        val principal = call.principal<AuthPrincipal>() ?: return@post call.respond(
            HttpStatusCode.Unauthorized,
            ApiResponse.fail<Unit>("Token requerido", "UNAUTHENTICATED"),
        )
        val dataMap = JsonBody.receiveMap(call)
        repository.deleteAnecdote(dataMap.reqString("anecdoteId"), principal.userId)
        call.respond(ApiResponse.ok("ok", "Anécdota borrada"))
    }

    post("/api/v1/social/anecdote/impact") {
        val principal = call.principal<AuthPrincipal>() ?: return@post call.respond(
            HttpStatusCode.Unauthorized,
            ApiResponse.fail<Unit>("Token requerido", "UNAUTHENTICATED"),
        )
        val dataMap = JsonBody.receiveMap(call)
        val anecdote = repository.clapAnecdote(dataMap.reqString("anecdoteId"), principal.userId)
        repository.countersOf(anecdote.postId)?.let { live ->
            hub.projectSocialPost(live.id, WsCodec.json.encodeToString(SocialLiveCounters.serializer(), live))
        }
        call.respond(ApiResponse.ok(anecdote, "Aplauso registrado"))
    }

    post("/api/v1/social/anecdote/heart") {
        val principal = call.principal<AuthPrincipal>() ?: return@post call.respond(
            HttpStatusCode.Unauthorized,
            ApiResponse.fail<Unit>("Token requerido", "UNAUTHENTICATED"),
        )
        val dataMap = JsonBody.receiveMap(call)
        val anecdote = repository.heartAnecdote(dataMap.reqString("anecdoteId"), principal.userId)
        call.respond(ApiResponse.ok(anecdote, "Corazón registrado"))
    }

    post("/api/v1/social/anecdote/repost") {
        val principal = call.principal<AuthPrincipal>() ?: return@post call.respond(
            HttpStatusCode.Unauthorized,
            ApiResponse.fail<Unit>("Token requerido", "UNAUTHENTICATED"),
        )
        val dataMap = JsonBody.receiveMap(call)
        val saved = repository.repostAnecdote(dataMap.reqString("anecdoteId"), principal.userId)
        hub.projectSocialPost(
            saved.id,
            WsCodec.json.encodeToString(
                SocialLiveCounters.serializer(),
                SocialLiveCounters(
                    saved.id,
                    saved.commentCount,
                    saved.impactCount,
                    authorUserId = saved.authorId,
                    body = saved.body,
                    tag = saved.topic,
                    createdAtEpochMs = saved.createdAtEpochMs,
                    honoreeName = saved.honoreeName,
                ),
            ),
        )
        call.respond(ApiResponse.ok(saved, "Anécdota en tu diario"))
    }

    post("/api/v1/social/post/edit") {
        val principal = call.principal<AuthPrincipal>() ?: return@post call.respond(
            HttpStatusCode.Unauthorized,
            ApiResponse.fail<Unit>("Token requerido", "UNAUTHENTICATED"),
        )
        val objectMapper = JsonBody.objectMapper
        val dataMap = objectMapper.readValue(call.receiveText(), object : com.fasterxml.jackson.core.type.TypeReference<Map<String, Any?>>() {})
        val updated = repository.editPost(
            postId = dataMap.reqString("postId"),
            userId = principal.userId,
            body = dataMap.reqString("body"),
            topic = dataMap.optString("topic"),
        )
        hub.projectSocialPost(
            updated.id,
            WsCodec.json.encodeToString(
                SocialLiveCounters.serializer(),
                SocialLiveCounters(
                    updated.id,
                    updated.commentCount,
                    updated.impactCount,
                    authorUserId = updated.authorId,
                    body = updated.body,
                    tag = updated.topic,
                    createdAtEpochMs = updated.createdAtEpochMs,
                    honoreeName = updated.honoreeName,
                ),
            ),
        )
        call.respond(ApiResponse.ok(updated, "Publicación editada"))
    }

    post("/api/v1/social/post/delete") {
        val principal = call.principal<AuthPrincipal>() ?: return@post call.respond(
            HttpStatusCode.Unauthorized,
            ApiResponse.fail<Unit>("Token requerido", "UNAUTHENTICATED"),
        )
        val objectMapper = JsonBody.objectMapper
        val dataMap = objectMapper.readValue(call.receiveText(), object : com.fasterxml.jackson.core.type.TypeReference<Map<String, Any?>>() {})
        repository.deletePost(dataMap.reqString("postId"), principal.userId)
        call.respond(ApiResponse.ok("ok", "Publicación eliminada"))
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

kind: IMPRESSION | DWELL | CLAP | HEART | COMMENT | PROFILE_TAP | SHARE | HIDE | SKIP

POST {{base}}/api/v1/social/publish
Authorization: Bearer {{jwt}}
{
  "body": "Llevé fruta al comedor.",
  "topic": "Comedor",
  "latitude": -34.6092,
  "longitude": -58.4008,
  "media": [
    { "kind": "IMAGE", "url": "https://upload.wikimedia.org/wikipedia/commons/thumb/6/6e/Golde33443.jpg/640px-Golde33443.jpg" }
  ]
}

POST {{base}}/api/v1/social/publish
Authorization: Bearer {{jwt}}
{
  "body": "Nos enseñó a no pasar de largo si un vecino necesita una mano.",
  "topic": "En vida",
  "honoreeName": "Don Héctor",
  "media": [
    { "kind": "IMAGE", "url": "https://upload.wikimedia.org/wikipedia/commons/thumb/6/6e/Golde33443.jpg/640px-Golde33443.jpg" }
  ]
}

POST {{base}}/api/v1/social/follow
Authorization: Bearer {{jwt}}
{ "userId": "11111111-1111-1111-1111-111111111111" }

POST {{base}}/api/v1/social/promote
Authorization: Bearer {{jwt}}
{ "postId": "55555555-5555-5555-5555-555555555555", "amountCents": 15000 }

POST {{base}}/api/v1/ads/spend
Authorization: Bearer {{adminJwt}}
{ "postId": "66666666-6666-6666-6666-666666666666", "amountCents": 40000, "companyId": "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa" }

El feed hidrata `media` (1 a 10 piezas IMAGE/VIDEO) y `sourceUrl` si el post es una noticia sembrada.
Cada post exige al menos una foto o un video, como Instagram.

POST {{base}}/api/v1/social/comment
Authorization: Bearer {{jwt}}
{
  "postId": "55555555-5555-5555-5555-555555555555",
  "body": "Gracias por sumar",
  "parentCommentId": null,
  "anecdoteId": null
}

POST {{base}}/api/v1/social/comments
Authorization: Bearer {{jwt}}
{
  "postId": "a2000000-0000-4000-8000-000000000040",
  "anecdoteId": "d2000000-0000-4000-8000-000000000041"
}

POST {{base}}/api/v1/social/comment/edit
Authorization: Bearer {{jwt}}
{ "commentId": "11111111-1111-4111-8111-111111111111", "body": "Lo dejo más claro." }

POST {{base}}/api/v1/social/comment/delete
Authorization: Bearer {{jwt}}
{ "commentId": "11111111-1111-4111-8111-111111111111" }

POST {{base}}/api/v1/social/anecdote
Authorization: Bearer {{jwt}}
{
  "postId": "a2000000-0000-4000-8000-000000000040",
  "body": "En el Congreso le devolvieron un sobre con dinero y lo rechazó."
}

POST {{base}}/api/v1/social/anecdote/edit
Authorization: Bearer {{jwt}}
{ "anecdoteId": "d2000000-0000-4000-8000-000000000041", "body": "Texto corregido de la anécdota." }

POST {{base}}/api/v1/social/anecdote/delete
Authorization: Bearer {{jwt}}
{ "anecdoteId": "d2000000-0000-4000-8000-000000000041" }

POST {{base}}/api/v1/social/anecdote/impact
Authorization: Bearer {{jwt}}
{ "anecdoteId": "d2000000-0000-4000-8000-000000000041" }

POST {{base}}/api/v1/social/anecdote/heart
Authorization: Bearer {{jwt}}
{ "anecdoteId": "d2000000-0000-4000-8000-000000000041" }

POST {{base}}/api/v1/social/anecdote/repost
Authorization: Bearer {{jwt}}
{ "anecdoteId": "d2000000-0000-4000-8000-000000000041" }

POST {{base}}/api/v1/social/post/edit
Authorization: Bearer {{jwt}}
{ "postId": "55555555-5555-5555-5555-555555555555", "body": "Llevé fruta y pan al comedor.", "topic": "Comedor" }

POST {{base}}/api/v1/social/post/delete
Authorization: Bearer {{jwt}}
{ "postId": "55555555-5555-5555-5555-555555555555" }

Tras el INSERT, el backend proyecta `ogt/social/posts/{id}` y `.../comments/{commentId}`
al árbol de db-kmp-sdk (Redis `ogt:tree:…`). El cliente observa con OgtRealtime.
*/

@Suppress("UNCHECKED_CAST")
private fun Map<String, Any?>.optMedia(): List<PostMediaItem> {
    val raw = this["media"] as? List<*> ?: return emptyList()
    return raw.mapIndexedNotNull { index, item ->
        val row = item as? Map<*, *> ?: return@mapIndexedNotNull null
        val url = row["url"] as? String ?: return@mapIndexedNotNull null
        PostMediaItem(
            id = (row["id"] as? String) ?: java.util.UUID.randomUUID().toString(),
            kind = runCatching { MediaKind.valueOf((row["kind"] as? String ?: "IMAGE").uppercase()) }
                .getOrDefault(MediaKind.IMAGE),
            url = url,
            posterUrl = row["posterUrl"] as? String,
            sortOrder = (row["sortOrder"] as? Number)?.toInt() ?: index,
            altText = row["altText"] as? String,
        )
    }
}
