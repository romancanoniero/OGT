package com.onlygoodthings.backend.auth

import com.onlygoodthings.backend.http.JsonBody
import com.onlygoodthings.backend.http.optBooleanOrNull
import com.onlygoodthings.backend.http.optDouble
import com.onlygoodthings.backend.http.optString
import com.onlygoodthings.backend.http.reqDouble
import com.onlygoodthings.backend.http.reqString
import com.onlygoodthings.backend.infra.IdentityStore
import com.onlygoodthings.backend.infra.ProfileSettingsPatch
import com.onlygoodthings.backend.realtime.RealtimeHub
import com.onlygoodthings.shared.domain.ApiResponse
import com.onlygoodthings.shared.domain.AuthMe
import com.onlygoodthings.shared.domain.ProfileSettings
import com.onlygoodthings.shared.domain.communityLevelLabel
import com.onlygoodthings.shared.domain.toLive
import com.onlygoodthings.shared.protocol.WsCodec
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

fun Route.protectedAuthRoutes(identity: IdentityStore, hub: RealtimeHub) {
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
                    settings = profile?.settings ?: ProfileSettings(),
                ),
            ),
        )
    }

    post("/api/v1/users/profile/update") {
        val principal = call.principal<AuthPrincipal>() ?: return@post call.respond(
            HttpStatusCode.Unauthorized,
            ApiResponse.fail<Unit>("Token requerido", "UNAUTHENTICATED"),
        )
        val dataMap = JsonBody.receiveMap(call)
        val updated = identity.updateProfile(
            userId = principal.userId,
            displayName = dataMap.optString("displayName")?.trim()?.takeIf { it.isNotEmpty() },
            photoUrl = dataMap.optString("photoUrl")?.trim()?.takeIf { it.isNotEmpty() },
            settingsPatch = ProfileSettingsPatch(
                language = dataMap.optString("language")?.trim()?.takeIf { it.isNotEmpty() },
                barrio = dataMap.optString("barrio"),
                publicProfileVisible = dataMap.optBooleanOrNull("publicProfileVisible"),
                showExactMatchLocation = dataMap.optBooleanOrNull("showExactMatchLocation"),
                animalAlertPush = dataMap.optBooleanOrNull("animalAlertPush"),
                skillAlertPush = dataMap.optBooleanOrNull("skillAlertPush"),
                parkingRadarSounds = dataMap.optBooleanOrNull("parkingRadarSounds"),
                radarEnabled = dataMap.optBooleanOrNull("radarEnabled"),
                carbonSaveMode = dataMap.optBooleanOrNull("carbonSaveMode"),
            ),
        )
        hub.projectUser(
            updated.id,
            WsCodec.json.encodeToString(com.onlygoodthings.shared.domain.ProfileLive.serializer(), updated.toLive()),
        )
        call.respond(ApiResponse.ok(updated, "Perfil actualizado"))
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
}

/*
Postman — actualizar perfil (nombre, foto y preferencias de la app)

POST {{base}}/api/v1/users/profile/update
Authorization: Bearer {{jwt}}
{
  "displayName": "Ana Test Vecina",
  "photoUrl": "https://onlygoodthings.lat/media/u/uuid.jpg",
  "barrio": "Palermo",
  "language": "es",
  "publicProfileVisible": true,
  "showExactMatchLocation": false,
  "animalAlertPush": true,
  "skillAlertPush": true,
  "parkingRadarSounds": true,
  "radarEnabled": true,
  "carbonSaveMode": false
}

La foto se sube antes con POST /api/v1/media/upload y se manda `url`.
Todos los campos son opcionales: lo que no viaja no se toca.
*/
