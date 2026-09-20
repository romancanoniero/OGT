package com.onlygoodthings.backend.notify

import com.onlygoodthings.backend.auth.AuthPrincipal
import com.onlygoodthings.backend.http.JsonBody
import com.onlygoodthings.backend.http.reqString
import com.onlygoodthings.shared.domain.ApiResponse
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.auth.principal
import io.ktor.server.request.receiveText
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.post

fun Route.noticeRoutes(repository: NoticeSqlRepository) {
    post("/api/v1/notifications/register") {
        val principal = call.principal<AuthPrincipal>() ?: return@post call.respond(
            HttpStatusCode.Unauthorized,
            ApiResponse.fail<Unit>("Token requerido", "UNAUTHENTICATED"),
        )
        val objectMapper = JsonBody.objectMapper
        val dataMap = objectMapper.readValue(call.receiveText(), object : com.fasterxml.jackson.core.type.TypeReference<Map<String, Any?>>() {})
        repository.register(principal.userId, dataMap.reqString("token"), dataMap.reqString("platform"))
        call.respond(ApiResponse.ok("ok", "Dispositivo registrado"))
    }

    post("/api/v1/notifications/mention") {
        val principal = call.principal<AuthPrincipal>() ?: return@post call.respond(
            HttpStatusCode.Unauthorized,
            ApiResponse.fail<Unit>("Token requerido", "UNAUTHENTICATED"),
        )
        val objectMapper = JsonBody.objectMapper
        val dataMap = objectMapper.readValue(call.receiveText(), object : com.fasterxml.jackson.core.type.TypeReference<Map<String, Any?>>() {})
        val sent = repository.notifyMention(
            actorId = principal.userId,
            recipientId = dataMap.reqString("recipientUserId"),
            postId = dataMap.reqString("postId"),
        )
        call.respond(ApiResponse.ok(sent, "Aviso de mención"))
    }
}

/*
Postman — notificaciones

POST {{base}}/api/v1/notifications/register
Authorization: Bearer {{jwt}}
{ "token": "fcm-o-apns", "platform": "IOS" }

POST {{base}}/api/v1/notifications/mention
Authorization: Bearer {{jwt}}
{
  "recipientUserId": "22222222-2222-2222-2222-222222222222",
  "postId": "55555555-5555-5555-5555-555555555555",
  "role": "PROTAGONIST"
}
*/
