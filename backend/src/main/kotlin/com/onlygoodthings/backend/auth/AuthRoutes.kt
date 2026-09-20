package com.onlygoodthings.backend.auth

import com.onlygoodthings.backend.http.JsonBody
import com.onlygoodthings.backend.http.reqString
import com.onlygoodthings.backend.infra.IdentityStore
import com.onlygoodthings.shared.domain.ApiResponse
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.auth.principal
import io.ktor.server.request.receiveText
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.post

fun Route.publicAuthRoutes(verifier: FirebaseTokenVerifier, identity: IdentityStore) {
    post("/api/v1/auth/session") {
        val objectMapper = JsonBody.objectMapper
        val data = call.receiveText()
        val dataMap = objectMapper.readValue(data, object : com.fasterxml.jackson.core.type.TypeReference<Map<String, Any?>>() {})
        val token = dataMap.reqString("token")
        val verified = runCatching { verifier.verify(token) }.getOrElse {
            return@post call.respond(
                HttpStatusCode.Unauthorized,
                ApiResponse.fail<Unit>("Token Firebase inválido", "INVALID_TOKEN"),
            )
        }
        val profile = identity.upsertFromFirebase(verified)
        call.respond(ApiResponse.ok(profile, "Sesión lista"))
    }

}

fun Route.protectedAuthRoutes() {
    post("/api/v1/auth/me") {
        val principal = call.principal<AuthPrincipal>() ?: return@post call.respond(
            HttpStatusCode.Unauthorized,
            ApiResponse.fail<Unit>("Token requerido", "UNAUTHENTICATED"),
        )
        call.respond(
            ApiResponse.ok(
                mapOf(
                    "userId" to principal.userId,
                    "firebaseUid" to principal.firebaseUid,
                    "role" to principal.role.name,
                    "email" to principal.email,
                ),
            ),
        )
    }
}
