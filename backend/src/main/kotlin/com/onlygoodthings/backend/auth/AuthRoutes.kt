package com.onlygoodthings.backend.auth

import com.onlygoodthings.backend.http.JsonBody
import com.onlygoodthings.backend.http.optDouble
import com.onlygoodthings.backend.http.optString
import com.onlygoodthings.backend.http.reqDouble
import com.onlygoodthings.backend.http.reqString
import com.onlygoodthings.backend.infra.IdentityStore
import com.onlygoodthings.shared.domain.ApiResponse
import com.onlygoodthings.shared.domain.AuthMe
import com.onlygoodthings.shared.domain.communityLevelLabel
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
        val profile = runCatching { identity.upsertFromFirebase(verified) }.getOrElse {
            return@post call.respond(HttpStatusCode.Forbidden,
                ApiResponse.fail<Unit>("Cuenta deshabilitada o eliminada", "ACCOUNT_UNAVAILABLE"))
        }
        call.respond(ApiResponse.ok(profile, "Sesión lista"))
    }

}

fun Route.protectedAuthRoutes(identity: IdentityStore) {
    post("/api/v1/auth/me") {
        val principal = call.principal<AuthPrincipal>() ?: return@post call.respond(
            HttpStatusCode.Unauthorized,
            ApiResponse.fail<Unit>("Token requerido", "UNAUTHENTICATED"),
        )
        val profile = identity.byId(principal.userId)
        call.respond(
            ApiResponse.ok(
                AuthMe(
                    userId = principal.userId,
                    firebaseUid = principal.firebaseUid,
                    role = principal.role.name,
                    email = principal.email,
                    displayName = profile?.displayName ?: principal.email ?: "Vecino",
                    photoUrl = profile?.photoUrl,
                    communityPoints = profile?.communityPoints ?: 0,
                    inviteCode = profile?.inviteCode,
                    levelLabel = communityLevelLabel(profile?.communityPoints ?: 0),
                ),
            ),
        )
    }

    post("/api/v1/me/location") {
        val principal = call.principal<AuthPrincipal>() ?: return@post call.respond(
            HttpStatusCode.Unauthorized,
            ApiResponse.fail<Unit>("Token requerido", "UNAUTHENTICATED"),
        )
        val objectMapper = JsonBody.objectMapper
        val data = call.receiveText()
        val dataMap = objectMapper.readValue(data, object : com.fasterxml.jackson.core.type.TypeReference<Map<String, Any?>>() {})
        val latitude = dataMap.reqDouble("latitude")
        val longitude = dataMap.reqDouble("longitude")
        val accuracyMeters = dataMap.optDouble("accuracyMeters")
        identity.updateHomeLocation(principal.userId, latitude, longitude, accuracyMeters)
        call.respond(
            ApiResponse.ok(
                mapOf(
                    "updated" to true,
                    "latitude" to latitude,
                    "longitude" to longitude,
                ),
                "Ubicación actualizada",
            ),
        )
    }

    post("/api/v1/account/export") {
        val principal = call.principal<AuthPrincipal>() ?: return@post call.respond(
            HttpStatusCode.Unauthorized,
            ApiResponse.fail<Unit>("Token requerido", "UNAUTHENTICATED"),
        )
        call.receiveText()
        val exported = identity.exportAccount(principal.userId) ?: return@post call.respond(
            HttpStatusCode.NotFound,
            ApiResponse.fail<Unit>("Cuenta no encontrada", "NOT_FOUND"),
        )
        call.respond(ApiResponse.ok(exported, "Exportación lista"))
    }

    post("/api/v1/account/delete") {
        val principal = call.principal<AuthPrincipal>() ?: return@post call.respond(
            HttpStatusCode.Unauthorized,
            ApiResponse.fail<Unit>("Token requerido", "UNAUTHENTICATED"),
        )
        val dataMap = JsonBody.parse(call.receiveText())
        if (dataMap.optString("confirm") != "BAJA") {
            return@post call.respond(
                HttpStatusCode.BadRequest,
                ApiResponse.fail<Unit>("Confirmación requerida", "CONFIRM_REQUIRED"),
            )
        }
        identity.eraseAccount(principal.userId)
        call.respond(ApiResponse.ok(mapOf("deleted" to true), "Cuenta dada de baja"))
    }
}
