package com.onlygoodthings.app.auth

import com.onlygoodthings.shared.data.SessionStore
import com.onlygoodthings.shared.data.createPlatformHttpClient
import com.onlygoodthings.shared.domain.ApiResponse
import com.onlygoodthings.shared.domain.UserProfile
import com.onlygoodthings.shared.domain.UploadedMedia
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
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

    /** Sube imagen con JWT y la asocia al usuario actual; no declara éxito sin DB. */
    @OptIn(ExperimentalEncodingApi::class)
    suspend fun uploadAndSaveProfilePhoto(bytes: ByteArray, filename: String): UserProfile {
        require(bytes.isNotEmpty() && bytes.size <= 8 * 1024 * 1024) { "Foto vacía o demasiado grande" }
        val token = session.firebaseJwt ?: error("Iniciá sesión para cambiar tu foto")
        val type = when (filename.substringAfterLast('.', "").lowercase()) {
            "png" -> "image/png"
            "webp" -> "image/webp"
            else -> "image/jpeg"
        }
        val upload: ApiResponse<UploadedMedia> = client.post(
            "${session.apiBaseUrl}/api/v1/media/upload"
        ) {
            contentType(ContentType.Application.Json)
            header("Authorization", "Bearer $token")
            setBody(buildJsonObject {
                put("filename", JsonPrimitive(filename))
                put("contentType", JsonPrimitive(type))
                put("bytesBase64", JsonPrimitive(Base64.encode(bytes)))
            })
        }.body()
        val saved = upload.data?.takeIf { upload.success }
            ?: error(upload.message ?: "No se pudo subir la imagen")
        val response: ApiResponse<UserProfile> = client.post(
            "${session.apiBaseUrl}/api/v1/users/profile/photo"
        ) {
            contentType(ContentType.Application.Json)
            header("Authorization", "Bearer $token")
            setBody(buildJsonObject { put("photoUrl", JsonPrimitive(saved.url)) })
        }.body()
        return response.data?.takeIf { response.success }
            ?: error(response.message ?: "No se pudo guardar la foto del perfil")
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
