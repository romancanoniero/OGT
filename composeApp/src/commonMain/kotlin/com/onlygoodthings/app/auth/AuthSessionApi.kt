package com.onlygoodthings.app.auth

import com.onlygoodthings.shared.data.SessionStore
import com.onlygoodthings.shared.data.createPlatformHttpClient
import com.onlygoodthings.shared.domain.ApiResponse
import com.onlygoodthings.shared.domain.AuthMe
import com.onlygoodthings.shared.domain.ProfileSettings
import com.onlygoodthings.shared.domain.UploadedMedia
import com.onlygoodthings.shared.domain.UserProfile
import io.ktor.client.call.body
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

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

    suspend fun fetchMe(): AuthMe? = runCatching {
        val token = session.firebaseJwt ?: return null
        val response: ApiResponse<AuthMe> = client.post("${session.apiBaseUrl}/api/v1/auth/me") {
            contentType(ContentType.Application.Json)
            header("Authorization", "Bearer $token")
            setBody(buildJsonObject {})
        }.body()
        if (!response.success) return@runCatching null
        response.data
    }.getOrNull()

    @OptIn(ExperimentalEncodingApi::class)
    suspend fun uploadBytes(filename: String, contentType: String, bytes: ByteArray): UploadedMedia? = runCatching {
        val token = session.firebaseJwt ?: return null
        val response: ApiResponse<UploadedMedia> = client.post("${session.apiBaseUrl}/api/v1/media/upload") {
            this.contentType(ContentType.Application.Json)
            header("Authorization", "Bearer $token")
            setBody(
                buildJsonObject {
                    put("filename", filename)
                    put("contentType", contentType)
                    put("bytesBase64", Base64.encode(bytes))
                },
            )
        }.body()
        if (!response.success) return@runCatching null
        response.data
    }.getOrNull()

    suspend fun updateProfile(
        displayName: String? = null,
        photoUrl: String? = null,
        settings: ProfileSettings? = null,
    ): UserProfile? = runCatching {
        val token = session.firebaseJwt ?: return null
        val response: ApiResponse<UserProfile> = client.post("${session.apiBaseUrl}/api/v1/users/profile/update") {
            contentType(ContentType.Application.Json)
            header("Authorization", "Bearer $token")
            setBody(
                buildJsonObject {
                    displayName?.takeIf { it.isNotBlank() }?.let { put("displayName", it) }
                    photoUrl?.takeIf { it.isNotBlank() }?.let { put("photoUrl", it) }
                    settings?.let {
                        put("language", it.language)
                        it.barrio?.let { barrio -> put("barrio", barrio) }
                        put("publicProfileVisible", it.publicProfileVisible)
                        put("showExactMatchLocation", it.showExactMatchLocation)
                        put("animalAlertPush", it.animalAlertPush)
                        put("skillAlertPush", it.skillAlertPush)
                        put("parkingRadarSounds", it.parkingRadarSounds)
                        put("radarEnabled", it.radarEnabled)
                        put("carbonSaveMode", it.carbonSaveMode)
                    }
                },
            )
        }.body()
        if (!response.success) return@runCatching null
        response.data
    }.getOrNull()
}
