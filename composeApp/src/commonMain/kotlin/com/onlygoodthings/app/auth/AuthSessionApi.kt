package com.onlygoodthings.app.auth

import com.onlygoodthings.shared.data.SessionStore
import com.onlygoodthings.shared.data.createPlatformHttpClient
import com.onlygoodthings.shared.domain.ApiResponse
import com.onlygoodthings.shared.domain.UserProfile
import io.ktor.client.call.body
import io.ktor.client.HttpClient
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.HttpStatusCode
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject

/** Abre sesión de plataforma con el JWT de Firebase. */
class AuthSessionApi(
    private val session: SessionStore,
    private val client: HttpClient = createPlatformHttpClient(),
    private val tokenProvider: suspend () -> String? = { session.firebaseJwt },
) {

    private suspend inline fun <reified T> accountPost(path: String, body: kotlinx.serialization.json.JsonObject = buildJsonObject {}, bearer: String? = null, base: String = session.apiBaseUrl): T {
        val token = bearer ?: tokenProvider() ?: error("Iniciá sesión para continuar")
        val response: ApiResponse<T> = client.post("$base$path") {
            contentType(ContentType.Application.Json)
            header("Authorization", "Bearer $token")
            setBody(body)
        }.body()
        return response.data?.takeIf { response.success } ?: error(response.message ?: "No se pudo completar la operación")
    }

    suspend fun settings(): com.onlygoodthings.shared.domain.AccountSettings = accountPost("/api/v1/me/settings")
    suspend fun communityConsent(): com.onlygoodthings.shared.domain.CommunityConsent = accountPost("/api/v1/me/consent")
    suspend fun acceptCommunityConsent(): com.onlygoodthings.shared.domain.CommunityConsent =
        accountPost("/api/v1/me/consent/accept", buildJsonObject {
            put("version", JsonPrimitive(com.onlygoodthings.shared.domain.COMMUNITY_TERMS_VERSION))
            put("adult", JsonPrimitive(true))
            put("accepted", JsonPrimitive(true))
        })
    suspend fun saveSettings(changes: kotlinx.serialization.json.JsonObject): com.onlygoodthings.shared.domain.AccountSettings =
        accountPost("/api/v1/me/settings/update", changes)
    suspend fun wallet(): com.onlygoodthings.shared.domain.WalletSummary = accountPost("/api/v1/wallet/summary")
    suspend fun timebankBoard(): com.onlygoodthings.shared.domain.TimebankBoard = accountPost("/api/v1/timebank/board")
    suspend fun publishExchange(need: String, give: String, note: String): com.onlygoodthings.shared.domain.TimebankBoard =
        accountPost("/api/v1/timebank/need",buildJsonObject {
            put("label",JsonPrimitive(need));put("give",JsonPrimitive(give));put("note",JsonPrimitive(note))
        })
    suspend fun startConversation(userId: String, tag: String): com.onlygoodthings.shared.domain.TimebankThread =
        accountPost("/api/v1/timebank/start",buildJsonObject { put("userId",JsonPrimitive(userId));put("tagSlug",JsonPrimitive(tag)) })
    suspend fun threads(): List<com.onlygoodthings.shared.domain.TimebankThread> = accountPost("/api/v1/timebank/inbox")
    suspend fun messages(matchId: String): List<com.onlygoodthings.shared.domain.TimebankMessageDto> =
        accountPost("/api/v1/timebank/messages", buildJsonObject { put("matchId", JsonPrimitive(matchId)) })
    suspend fun sendMessage(matchId: String, body: String): com.onlygoodthings.shared.domain.TimebankMessageDto =
        accountPost("/api/v1/timebank/message/send", buildJsonObject { put("matchId", JsonPrimitive(matchId)); put("body", JsonPrimitive(body)) })
    suspend fun exportAccount(): kotlinx.serialization.json.JsonObject = accountPost("/api/v1/account/export")
    suspend fun unregisterNoticeToken(token: String, bearer: String, base: String = session.apiBaseUrl) {
        accountPost<String>("/api/v1/notifications/unregister", buildJsonObject { put("token", JsonPrimitive(token)) },bearer,base)
    }

    suspend fun openSession(token: String, firebaseUid: String): UserProfile = try {
        suspend fun request(base: String) = client.post("$base/api/v1/auth/session") {
            contentType(ContentType.Application.Json)
            header("Authorization", "Bearer $token")
            setBody(buildJsonObject { put("token", JsonPrimitive(token)) })
        }
        var confirmedBase=session.apiBaseUrl
        var reply=request(confirmedBase)
        // Only a transport-level production IP rejection can select the bounded review gateway.
        // Invalid Firebase credentials and application permission errors never trigger a bypass.
        if(reply.status==HttpStatusCode.Forbidden && session.apiBaseUrl==com.onlygoodthings.shared.data.OGT_DEFAULT_API_BASE &&
            reply.headers["Content-Type"]?.contains("json",ignoreCase=true)!=true) {
            val reviewBase=com.onlygoodthings.shared.data.OGT_DEFAULT_API_BASE+"/store-review"
            val reviewReply=request(reviewBase)
            if(reviewReply.status==HttpStatusCode.OK) { reply=reviewReply;confirmedBase=reviewBase }
        }
        val response: ApiResponse<UserProfile> = reply.body()
        confirmedSessionProfile(response, firebaseUid).also { session.apiBaseUrl=confirmedBase }
    } catch (cancel: kotlinx.coroutines.CancellationException) {
        throw cancel
    } catch (failure: AuthException) {
        throw failure
    } catch (failure: Exception) {
        throw AuthException("No pudimos conectar con OGT para abrir tu sesión. Revisá la conexión y volvé a intentarlo.", failure)
    }

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
