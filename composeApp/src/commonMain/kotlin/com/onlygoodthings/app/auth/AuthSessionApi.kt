package com.onlygoodthings.app.auth

import com.onlygoodthings.shared.data.SessionStore
import com.onlygoodthings.shared.data.createPlatformHttpClient
import com.onlygoodthings.shared.domain.ApiResponse
import com.onlygoodthings.shared.domain.NeighborCard
import com.onlygoodthings.shared.domain.UploadedMedia
import com.onlygoodthings.shared.domain.UserProfile
import io.ktor.client.call.body
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlin.io.encoding.Base64
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject

/** Abre sesión de plataforma con el JWT de Firebase. */
class AuthSessionApi(private val session: SessionStore) {
    private val client = createPlatformHttpClient()

    suspend fun openSession(token: String): UserProfile? = runCatching {
        val response: ApiResponse<UserProfile> = client.post("${session.apiBaseUrl}/api/v1/auth/session") {
            contentType(ContentType.Application.Json)
            header("Authorization", "Bearer $token")
            setBody(buildJsonObject { put("token", JsonPrimitive(token)) })
        }.body()
        if (!response.success) return@runCatching null
        response.data
    }.getOrNull()

    /** Ping de presencia: actualiza users.home_location en el servidor. */
    suspend fun updateLocation(latitude: Double, longitude: Double, accuracyMeters: Double?): Boolean = runCatching {
        val token = session.firebaseJwt ?: return false
        val response: ApiResponse<kotlinx.serialization.json.JsonObject> =
            client.post("${session.apiBaseUrl}/api/v1/me/location") {
                contentType(ContentType.Application.Json)
                header("Authorization", "Bearer $token")
                setBody(
                    buildJsonObject {
                        put("latitude", JsonPrimitive(latitude))
                        put("longitude", JsonPrimitive(longitude))
                        accuracyMeters?.let { put("accuracyMeters", JsonPrimitive(it)) }
                    },
                )
            }.body()
        response.success
    }.getOrDefault(false)

    /** Deja el archivo en disco de la API y devuelve la URL pública. */
    suspend fun uploadImage(filename: String, contentType: String, bytes: ByteArray): UploadedMedia {
        val token = session.firebaseJwt ?: error("Tenés que iniciar sesión")
        val response: ApiResponse<UploadedMedia> =
            client.post("${session.apiBaseUrl}/api/v1/media/upload") {
                contentType(ContentType.Application.Json)
                header("Authorization", "Bearer $token")
                setBody(
                    buildJsonObject {
                        put("filename", JsonPrimitive(filename))
                        put("contentType", JsonPrimitive(contentType))
                        put("bytesBase64", JsonPrimitive(Base64.Default.encode(bytes)))
                    },
                )
            }.body()
        if (!response.success) error(response.message ?: "No se pudo subir la foto")
        return response.data ?: error("No se pudo subir la foto")
    }

    /**
     * Actualiza el perfil propio. Hay que reenviar los ajustes actuales:
     * el servidor reescribe metadata con lo que llega en este POST.
     */
    suspend fun updateProfile(
        displayName: String,
        photoUrl: String,
        language: String,
        barrio: String,
        publicProfileVisible: Boolean,
        showExactMatchLocation: Boolean,
        animalAlertPush: Boolean,
        skillAlertPush: Boolean,
        parkingRadarSounds: Boolean,
        radarEnabled: Boolean,
        carbonSaveMode: Boolean,
    ): NeighborCard {
        val token = session.firebaseJwt ?: error("Tenés que iniciar sesión")
        val response: ApiResponse<NeighborCard> =
            client.post("${session.apiBaseUrl}/api/v1/users/profile/update") {
                contentType(ContentType.Application.Json)
                header("Authorization", "Bearer $token")
                setBody(
                    buildJsonObject {
                        put("displayName", JsonPrimitive(displayName))
                        put("photoUrl", JsonPrimitive(photoUrl))
                        put("language", JsonPrimitive(language))
                        put("barrio", JsonPrimitive(barrio))
                        put("publicProfileVisible", JsonPrimitive(publicProfileVisible))
                        put("showExactMatchLocation", JsonPrimitive(showExactMatchLocation))
                        put("animalAlertPush", JsonPrimitive(animalAlertPush))
                        put("skillAlertPush", JsonPrimitive(skillAlertPush))
                        put("parkingRadarSounds", JsonPrimitive(parkingRadarSounds))
                        put("radarEnabled", JsonPrimitive(radarEnabled))
                        put("carbonSaveMode", JsonPrimitive(carbonSaveMode))
                    },
                )
            }.body()
        if (!response.success) error(response.message ?: "No se pudo guardar el perfil")
        return response.data ?: error("No se pudo guardar el perfil")
    }
}
