package com.onlygoodthings.app.auth

import com.onlygoodthings.shared.data.SessionStore
import com.onlygoodthings.shared.data.createPlatformHttpClient
import com.onlygoodthings.shared.domain.ApiResponse
import com.onlygoodthings.shared.domain.UserProfile
import io.ktor.client.call.body
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
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

    suspend fun uploadProfilePhoto(token: String, filename: String, bytes: ByteArray): com.onlygoodthings.shared.domain.UploadedMedia = uploadMedia(token, filename, bytes)

    @OptIn(kotlin.io.encoding.ExperimentalEncodingApi::class)
    suspend fun uploadMedia(token: String, filename: String, bytes: ByteArray): com.onlygoodthings.shared.domain.UploadedMedia {
        val encoded = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
            kotlin.io.encoding.Base64.encode(bytes)
        }
        val response: ApiResponse<com.onlygoodthings.shared.domain.UploadedMedia> =
            client.post("${session.apiBaseUrl}/api/v1/media/upload") {
                contentType(ContentType.Application.Json)
                header("Authorization", "Bearer $token")
                setBody(buildJsonObject {
                    put("filename", JsonPrimitive(filename))
                    put("bytesBase64", JsonPrimitive(encoded))
                })
            }.body()
        return response.data?.takeIf { response.success }
            ?: error(response.message ?: "No se pudo subir el archivo")
    }

    suspend fun updateProfilePhoto(token: String, name: String, url: String): com.onlygoodthings.shared.domain.NeighborCard {
        val response: ApiResponse<com.onlygoodthings.shared.domain.NeighborCard> =
            client.post("${session.apiBaseUrl}/api/v1/users/profile/update") {
                contentType(ContentType.Application.Json)
                header("Authorization", "Bearer $token")
                setBody(buildJsonObject {
                    put("displayName", JsonPrimitive(name))
                    put("photoUrl", JsonPrimitive(url))
                })
            }.body()
        return response.data?.takeIf { response.success }
            ?: error(response.message ?: "No se pudo guardar la foto")
    }

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

    /** Baja de la propia cuenta. El servidor exige confirm = BAJA y borra el contenido de esa autoría. */
    suspend fun deleteAccount() {
        val token = session.firebaseJwt ?: error("Iniciá sesión para borrar la cuenta")
        val response: ApiResponse<kotlinx.serialization.json.JsonObject> =
            client.post("${session.apiBaseUrl}/api/v1/account/delete") {
                contentType(ContentType.Application.Json)
                header("Authorization", "Bearer $token")
                setBody(buildJsonObject { put("confirm", JsonPrimitive("BAJA")) })
            }.body()
        if (!response.success) error(response.message ?: "No se pudo borrar la cuenta")
    }
}
