package com.onlygoodthings.app.auth

import com.onlygoodthings.shared.data.SessionStore
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.http.content.TextContent
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.*

class AccountSessionTransportTest {
    private val headers = headersOf(HttpHeaders.ContentType, "application/json")

    @Test fun aMissingConsentRouteCannotBecomeAnAcceptedAgreementOrARawKtorError() = runBlocking {
        var expired = false
        HttpClient(MockEngine { request ->
            assertEquals("/api/v1/me/consent", request.url.encodedPath)
            respond("", HttpStatusCode.NotFound)
        }) { install(ContentNegotiation) { json() } }.use { client ->
            val api = AuthSessionApi(SessionStore(), client, onSessionExpired = { expired = true }) { "mfa-confirmed-token" }
            val failure = assertFailsWith<AuthException> { api.communityConsent() }
            assertFalse(failure is AuthSessionExpired)
            assertFalse(expired)
            assertEquals("Esta función de OGT no está disponible por el momento. Volvé a intentarlo en unos minutos.", failure.message)
        }
    }

    @Test fun nonJsonAndMalformedRepliesNeverExposeTransportInternals() = runBlocking {
        for (body in listOf("<html>Bad gateway</html>", "not-json")) {
            HttpClient(MockEngine { respond(body, HttpStatusCode.BadGateway, headers) }) {
                install(ContentNegotiation) { json() }
            }.use { client ->
                val failure = assertFailsWith<AuthException> { AuthSessionApi(SessionStore(), client) { "valid-token" }.communityConsent() }
                assertEquals("No pudimos completar la operación con OGT. Volvé a intentarlo en unos minutos.", failure.message)
            }
        }
    }

    @Test fun rejectedActiveSessionRequestsLoginEvenWithoutAJsonBody() = runBlocking {
        var expired = 0
        HttpClient(MockEngine { respond("Unauthorized", HttpStatusCode.Unauthorized) }) {
            install(ContentNegotiation) { json() }
        }.use { client ->
            val api = AuthSessionApi(SessionStore(), client, onSessionExpired = { expired++ }) { "old-token" }
            assertFailsWith<AuthSessionExpired> { api.settings() }
            assertEquals(1, expired)
        }
    }

    @Test fun applicationTokenRejectionAlsoRequestsLogin() = runBlocking {
        var expired = 0
        HttpClient(MockEngine { respond("""{"success":false,"errorCode":"INVALID_TOKEN"}""", HttpStatusCode.OK, headers) }) {
            install(ContentNegotiation) { json() }
        }.use { client ->
            val api = AuthSessionApi(SessionStore(), client, onSessionExpired = { expired++ }) { "old-token" }
            assertFailsWith<AuthSessionExpired> { api.settings() }
            assertEquals(1, expired)
        }
    }

    @Test fun permissionDenialsAndOldPushUnregistrationDoNotExpireTheCurrentAccount() = runBlocking {
        var expired = 0
        HttpClient(MockEngine { request ->
            if (request.url.encodedPath.endsWith("unregister"))
                respond("""{"success":false,"message":"Expired old token"}""", HttpStatusCode.Unauthorized, headers)
            else respond("""{"success":false,"message":"Permission denied"}""", HttpStatusCode.Forbidden, headers)
        }) { install(ContentNegotiation) { json() } }.use { client ->
            val api = AuthSessionApi(SessionStore(), client, onSessionExpired = { expired++ }) { "current-token" }
            assertFailsWith<IllegalStateException> { api.settings() }
            assertFailsWith<IllegalStateException> { api.unregisterNoticeToken("push", "previous-token") }
            assertEquals(0, expired)
        }
    }

    @Test fun completingTheProfileSendsOnlyItsFieldsAndUsesTheCurrentGoogleCredential() = runBlocking {
        HttpClient(MockEngine { request ->
            assertEquals("/api/v1/users/profile/update", request.url.encodedPath)
            assertEquals("Bearer google-refreshed", request.headers[HttpHeaders.Authorization])
            val body = Json.parseToJsonElement((request.body as TextContent).text).jsonObject
            assertEquals(setOf("displayName", "barrio", "photoUrl"), body.keys)
            assertEquals("Nombre en OGT", body["displayName"]?.jsonPrimitive?.content)
            assertEquals("Mi comunidad", body["barrio"]?.jsonPrimitive?.content)
            assertEquals("https://example.test/google.jpg", body["photoUrl"]?.jsonPrimitive?.content)
            respond("""{"success":true,"data":{"userId":"existing-id","displayName":"Nombre en OGT"}}""", HttpStatusCode.OK, headers)
        }) { install(ContentNegotiation) { json() } }.use { client ->
            val session = SessionStore().apply { firebaseJwt = "stale-snapshot" }
            val api = AuthSessionApi(session, client) { "google-refreshed" }
            assertEquals("existing-id", api.completeProfile("Nombre en OGT", "Mi comunidad", "https://example.test/google.jpg").userId)
        }
    }
}
