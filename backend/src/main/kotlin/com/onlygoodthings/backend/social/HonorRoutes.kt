package com.onlygoodthings.backend.social

import com.onlygoodthings.backend.auth.AuthPrincipal
import com.onlygoodthings.backend.http.JsonBody
import com.onlygoodthings.backend.http.optString
import com.onlygoodthings.backend.http.reqString
import com.onlygoodthings.shared.domain.ApiResponse
import com.onlygoodthings.shared.domain.HonorChannel
import com.onlygoodthings.shared.domain.HonorStoreLinks
import com.onlygoodthings.shared.domain.OgtAndroidDebugSha256
import com.onlygoodthings.shared.domain.PostPersonRole
import com.onlygoodthings.shared.domain.appleAppSiteAssociationJson
import com.onlygoodthings.shared.domain.assetLinksJson
import com.onlygoodthings.shared.domain.defaultHonorStoreLinks
import com.onlygoodthings.shared.domain.honorLandingHtml
import com.onlygoodthings.shared.domain.honorMissingHtml
import com.onlygoodthings.shared.domain.parseHonorToken
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.auth.principal
import io.ktor.server.request.receiveText
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post

fun Route.publicHonorRoutes(repository: HonorSqlRepository) {
    get("/h/{token}") {
        val token = parseHonorToken(call.parameters["token"].orEmpty())
            ?: return@get call.respondText(honorMissingHtml(), ContentType.Text.Html, HttpStatusCode.NotFound)
        val honor = runCatching { repository.peek(token) }.getOrNull()
        call.respondText(
            honorLandingHtml(honor?.givenName ?: "vos", honor?.issuerName, token, honorStoreLinks(token)),
            ContentType.Text.Html,
        )
    }
    get("/.well-known/apple-app-site-association") {
        call.respondText(appleAppSiteAssociationJson(), ContentType.Application.Json)
    }
    get("/apple-app-site-association") {
        call.respondText(appleAppSiteAssociationJson(), ContentType.Application.Json)
    }
    get("/.well-known/assetlinks.json") {
        call.respondText(assetLinksJson(androidFingerprints()), ContentType.Application.Json)
    }

    post("/api/v1/honor/peek") {
        val objectMapper = JsonBody.objectMapper
        val dataMap = objectMapper.readValue(call.receiveText(), object : com.fasterxml.jackson.core.type.TypeReference<Map<String, Any?>>() {})
        val token = dataMap.reqString("token")
        val honor = repository.peek(token)
            ?: return@post call.respond(HttpStatusCode.NotFound, ApiResponse.fail<Unit>("Invitación inexistente", "NOT_FOUND"))
        call.respond(ApiResponse.ok(honor))
    }
}

fun Route.honorRoutes(repository: HonorSqlRepository) {
    post("/api/v1/honor/issue") {
        val principal = call.principal<AuthPrincipal>() ?: return@post call.respond(
            HttpStatusCode.Unauthorized,
            ApiResponse.fail<Unit>("Token requerido", "UNAUTHENTICATED"),
        )
        val objectMapper = JsonBody.objectMapper
        val dataMap = objectMapper.readValue(call.receiveText(), object : com.fasterxml.jackson.core.type.TypeReference<Map<String, Any?>>() {})
        val channel = runCatching { HonorChannel.valueOf(dataMap.reqString("channel").uppercase()) }.getOrNull()
            ?: return@post call.respond(HttpStatusCode.BadRequest, ApiResponse.fail<Unit>("channel inválido", "BAD_REQUEST"))
        val honor = repository.issue(
            issuerId = principal.userId,
            givenName = dataMap.reqString("givenName"),
            channel = channel,
            contact = dataMap.reqString("contact"),
            claimToken = dataMap.optString("token"),
        )
        call.respond(ApiResponse.ok(honor, "Invitación lista"))
    }

    post("/api/v1/honor/attach") {
        val principal = call.principal<AuthPrincipal>() ?: return@post call.respond(
            HttpStatusCode.Unauthorized,
            ApiResponse.fail<Unit>("Token requerido", "UNAUTHENTICATED"),
        )
        val objectMapper = JsonBody.objectMapper
        val dataMap = objectMapper.readValue(call.receiveText(), object : com.fasterxml.jackson.core.type.TypeReference<Map<String, Any?>>() {})
        val role = runCatching { PostPersonRole.valueOf(dataMap.reqString("role").uppercase()) }.getOrNull()
            ?: return@post call.respond(HttpStatusCode.BadRequest, ApiResponse.fail<Unit>("role inválido", "BAD_REQUEST"))
        val honor = repository.attach(
            issuerId = principal.userId,
            token = dataMap.reqString("token"),
            postId = dataMap.reqString("postId"),
            role = role,
        ) ?: return@post call.respond(HttpStatusCode.NotFound, ApiResponse.fail<Unit>("Invitación inexistente", "NOT_FOUND"))
        call.respond(ApiResponse.ok(honor, "Mención pegada al post"))
    }

    post("/api/v1/honor/claim") {
        val principal = call.principal<AuthPrincipal>() ?: return@post call.respond(
            HttpStatusCode.Unauthorized,
            ApiResponse.fail<Unit>("Token requerido", "UNAUTHENTICATED"),
        )
        val objectMapper = JsonBody.objectMapper
        val dataMap = objectMapper.readValue(call.receiveText(), object : com.fasterxml.jackson.core.type.TypeReference<Map<String, Any?>>() {})
        val honor = repository.claim(dataMap.reqString("token"), principal.userId)
            ?: return@post call.respond(HttpStatusCode.Conflict, ApiResponse.fail<Unit>("Esta mención ya tiene dueña", "ALREADY_CLAIMED"))
        call.respond(ApiResponse.ok(honor, "Crédito reivindicado"))
    }
}

private fun honorStoreLinks(token: String): HonorStoreLinks = defaultHonorStoreLinks(
    token = token,
    iosAppId = envOrNull("OGT_IOS_APP_ID"),
    iosStoreUrl = envOrNull("OGT_IOS_STORE_URL"),
)

private fun androidFingerprints(): List<String> {
    val extra = envOrNull("OGT_ANDROID_SHA256")
        ?.split(',')
        ?.map { it.trim() }
        ?.filter { it.isNotBlank() }
        .orEmpty()
    return (listOf(OgtAndroidDebugSha256) + extra).distinct()
}

private fun envOrNull(name: String): String? = System.getenv(name)?.trim()?.takeIf { it.isNotBlank() }

/*
Postman — mención de honor

POST {{base}}/api/v1/honor/issue
Authorization: Bearer {{jwt}}
{
  "givenName": "Ana Pérez",
  "channel": "WHATSAPP",
  "contact": "+54 11 5555 1234"
}

POST {{base}}/api/v1/honor/peek
{
  "token": "{{claimToken}}"
}

POST {{base}}/api/v1/honor/attach
Authorization: Bearer {{jwt}}
{
  "token": "{{claimToken}}",
  "postId": "55555555-5555-5555-5555-555555555555",
  "role": "PROTAGONIST"
}

POST {{base}}/api/v1/honor/claim
Authorization: Bearer {{jwt}}
{
  "token": "{{claimToken}}"
}

GET {{base}}/h/{{claimToken}}
GET {{base}}/.well-known/apple-app-site-association
GET {{base}}/.well-known/assetlinks.json
*/
