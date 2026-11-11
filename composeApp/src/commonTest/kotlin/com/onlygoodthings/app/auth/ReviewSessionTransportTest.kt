package com.onlygoodthings.app.auth

import com.onlygoodthings.shared.data.OGT_DEFAULT_API_BASE
import com.onlygoodthings.shared.data.SessionStore
import com.onlygoodthings.shared.domain.ApiResponse
import com.onlygoodthings.shared.domain.UserProfile
import com.onlygoodthings.shared.domain.UserRole
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlin.test.*

class ReviewSessionTransportTest {
    private val profile = UserProfile("account-id", "firebase-uid", "QA", null, UserRole.USER, 0, "qa")
    private fun body(profile: UserProfile = this.profile) = Json.encodeToString(ApiResponse.serializer(UserProfile.serializer()), ApiResponse.ok(profile))
    private val jsonHeaders = headersOf(HttpHeaders.ContentType, "application/json")
    private val review = "$OGT_DEFAULT_API_BASE/store-review"

    @Test fun transportRejectionSelectsReviewOnlyAfterTheSameIdentityIsConfirmed() = runBlocking {
        val session = SessionStore()
        val urls = mutableListOf<String>()
        val engine = MockEngine { request ->
            urls += request.url.toString()
            assertEquals("Bearer fixture", request.headers[HttpHeaders.Authorization])
            if (urls.size == 1) respond("Forbidden", HttpStatusCode.Forbidden, headersOf(HttpHeaders.ContentType, "text/plain"))
            else respond(body(), HttpStatusCode.OK, jsonHeaders)
        }
        HttpClient(engine) { install(ContentNegotiation) { json() } }.use { client ->
            assertEquals(profile, AuthSessionApi(session, client).openSession("fixture", profile.firebaseUid))
            assertEquals(listOf("$OGT_DEFAULT_API_BASE/api/v1/auth/session", "$review/api/v1/auth/session"), urls)
            assertEquals(review, session.apiBaseUrl)
        }
    }

    @Test fun applicationDenialsAndOtherServersNeverTriggerReviewFallback() = runBlocking {
        for (base in listOf(OGT_DEFAULT_API_BASE, "http://127.0.0.1:19083")) {
            val session = SessionStore().apply { apiBaseUrl = base }
            var calls = 0
            HttpClient(MockEngine {
                calls++
                if (base == OGT_DEFAULT_API_BASE) respond("""{"success":false,"errorCode":"ACCOUNT_UNAVAILABLE"}""", HttpStatusCode.Forbidden, jsonHeaders)
                else respond("Forbidden", HttpStatusCode.Forbidden)
            }) { install(ContentNegotiation) { json() } }.use { client ->
                assertFailsWith<AuthException> { AuthSessionApi(session, client).openSession("fixture", profile.firebaseUid) }
                assertEquals(1, calls)
                assertEquals(base, session.apiBaseUrl)
            }
        }
    }

    @Test fun wrongIdentityOrFailedReviewLeavesTheOriginalTransportUnchanged() = runBlocking {
        for (wrongIdentity in listOf(true, false)) {
            val session = SessionStore()
            var calls = 0
            HttpClient(MockEngine {
                calls++
                if (calls == 1) respond("Forbidden", HttpStatusCode.Forbidden)
                else if (wrongIdentity) respond(body(profile.copy(firebaseUid = "other-identity")), HttpStatusCode.OK, jsonHeaders)
                else respond("""{"success":false,"errorCode":"UNAUTHENTICATED"}""", HttpStatusCode.Unauthorized, jsonHeaders)
            }) { install(ContentNegotiation) { json() } }.use { client ->
                assertFailsWith<AuthException> { AuthSessionApi(session, client).openSession("fixture", profile.firebaseUid) }
                assertEquals(2, calls)
                assertEquals(OGT_DEFAULT_API_BASE, session.apiBaseUrl)
            }
        }
    }

    @Test fun ordinarySuccessKeepsTheOriginalTransport() = runBlocking {
        val session = SessionStore()
        var calls = 0
        HttpClient(MockEngine { calls++; respond(body(), HttpStatusCode.OK, jsonHeaders) }) {
            install(ContentNegotiation) { json() }
        }.use { client ->
            assertEquals(profile, AuthSessionApi(session, client).openSession("fixture", profile.firebaseUid))
            assertEquals(1, calls)
            assertEquals(OGT_DEFAULT_API_BASE, session.apiBaseUrl)
        }
    }

    @Test fun signingOutCanUnregisterItsPushTokenOnThePreviousReviewTransport() = runBlocking {
        val session = SessionStore().apply { apiBaseUrl = review }
        val previousBase = session.apiBaseUrl
        session.apiBaseUrl = OGT_DEFAULT_API_BASE
        var calls = 0
        HttpClient(MockEngine { request ->
            calls++
            assertEquals("$review/api/v1/notifications/unregister", request.url.toString())
            assertEquals("Bearer previous-account", request.headers[HttpHeaders.Authorization])
            respond("""{"success":true,"data":"unregistered"}""", HttpStatusCode.OK, jsonHeaders)
        }) { install(ContentNegotiation) { json() } }.use { client ->
            AuthSessionApi(session, client).unregisterNoticeToken("push-fixture", "previous-account", previousBase)
            assertEquals(1, calls)
            assertEquals(OGT_DEFAULT_API_BASE, session.apiBaseUrl)
        }
    }
}
