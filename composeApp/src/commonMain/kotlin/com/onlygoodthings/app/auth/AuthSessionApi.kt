package com.onlygoodthings.app.auth

import com.onlygoodthings.shared.data.SessionStore
import com.onlygoodthings.shared.data.createPlatformHttpClient
import com.onlygoodthings.shared.domain.ApiResponse
import com.onlygoodthings.shared.domain.UserProfile
import com.onlygoodthings.shared.domain.UploadedMedia
import com.onlygoodthings.app.platform.readLocalFileBytes
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.contentOrNull
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

    suspend fun openSession(token: String): UserProfile {
        val response: ApiResponse<UserProfile> = client.post("${session.apiBaseUrl}/api/v1/auth/session") {
            contentType(ContentType.Application.Json)
            header("Authorization", "Bearer $token")
            setBody(buildJsonObject { put("token", JsonPrimitive(token)) })
        }.body()
        if (!response.success) error(response.message ?: "No se pudo validar la sesión")
        return response.data ?: error("El servidor no devolvió el perfil")
    }

    /**
     * No sustituir la foto por un archivo temporal del picker: subir el JPEG a
     * Contabo y confirmar que users.photo_url se actualizó antes de cambiar
     * el avatar local. El backend conserva la URL entre reinicios.
     */
    @OptIn(ExperimentalEncodingApi::class)
    suspend fun saveProfilePhoto(path: String, displayName: String): String {
        val token = session.firebaseJwt ?: error("Iniciá sesión para guardar tu foto")
        val bytes = readLocalFileBytes(path) ?: error("No se pudo leer la imagen seleccionada")
        require(bytes.isNotEmpty() && bytes.size <= 8 * 1024 * 1024) {
            "La imagen debe tener menos de 8 MB"
        }
        val name = displayName.trim().takeIf { it.length >= 2 } ?: "Mi perfil"
        val upload: ApiResponse<UploadedMedia> = client.post("${session.apiBaseUrl}/api/v1/media/upload") {
            contentType(ContentType.Application.Json)
            header("Authorization", "Bearer $token")
            setBody(buildJsonObject {
                put("filename", JsonPrimitive("avatar.jpg"))
                put("contentType", JsonPrimitive("image/jpeg"))
                put("bytesBase64", JsonPrimitive(Base64.Default.encode(bytes)))
            })
        }.body()
        if (!upload.success) error(upload.message ?: "No se pudo subir la foto")
        val url = upload.data?.let(::canonicalUploadUrl)
            ?: error("El servidor no confirmó el archivo")
        val updated: ApiResponse<kotlinx.serialization.json.JsonObject> =
            client.post("${session.apiBaseUrl}/api/v1/users/profile/update") {
                contentType(ContentType.Application.Json)
                header("Authorization", "Bearer $token")
                setBody(buildJsonObject {
                    put("displayName", JsonPrimitive(name))
                    put("photoUrl", JsonPrimitive(url))
                })
            }.body()
        if (!updated.success) error(updated.message ?: "No se pudo guardar la foto en el perfil")
        val stored = updated.data?.get("photoUrl")?.jsonPrimitive?.contentOrNull
        return stored?.takeIf { it.contains("/media/u/") }
            ?: error("No se confirmó la imagen guardada en el perfil")
    }

    /** Publicaciones solo con imágenes existentes en el servidor, no file://. */
    @OptIn(ExperimentalEncodingApi::class)
    suspend fun uploadPublicationImage(path: String): UploadedMedia {
        val token = session.firebaseJwt ?: error("Iniciá sesión para publicar")
        val bytes = readLocalFileBytes(path) ?: error("No se pudo leer la foto seleccionada")
        require(bytes.isNotEmpty() && bytes.size <= 8 * 1024 * 1024) {
            "La imagen debe tener menos de 8 MB"
        }
        val uploaded: ApiResponse<UploadedMedia> = client.post("${session.apiBaseUrl}/api/v1/media/upload") {
            contentType(ContentType.Application.Json)
            header("Authorization", "Bearer $token")
            setBody(buildJsonObject {
                put("filename", JsonPrimitive("publicacion.jpg"))
                put("contentType", JsonPrimitive("image/jpeg"))
                put("bytesBase64", JsonPrimitive(Base64.Default.encode(bytes)))
            })
        }.body()
        if (!uploaded.success) error(uploaded.message ?: "No se pudo guardar la imagen")
        val saved = uploaded.data ?: error("La imagen no fue confirmada por el servidor")
        return saved.copy(url = canonicalUploadUrl(saved))
    }

    private fun canonicalUploadUrl(saved: UploadedMedia): String {
        // El backend puede usar una URL HTTP interna para los uploads.
        // El móvil siempre consume el mismo archivo por el dominio HTTPS público.
        val safe = Regex("^[0-9a-fA-F-]{36}\\.(jpg|jpeg|png|webp)$")
        require(safe.matches(saved.filename)) { "Nombre de imagen no válido" }
        return session.apiBaseUrl.trimEnd('/') + "/media/u/" + saved.filename
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
