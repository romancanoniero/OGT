package com.onlygoodthings.app.auth

import com.onlygoodthings.shared.data.SessionStore
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.HttpTimeoutCapability
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.runBlocking
import kotlin.test.*

class MediaUploadTimeoutTest {
    @Test fun processingBudgetAppliesOnlyToMediaAndOrdinaryRequestsKeepTheirShortTimeout() = runBlocking {
        val seen=mutableListOf<Pair<String,Long?>>()
        HttpClient(MockEngine { request ->
            seen+=request.url.encodedPath to request.getCapabilityOrNull(HttpTimeoutCapability)?.requestTimeoutMillis
            val data=if(request.url.encodedPath.endsWith("/upload"))
                """{"id":"upload","kind":"VIDEO","url":"https://example.test/video.mp4","filename":"video.mp4"}""" else "{}"
            respond("""{"success":true,"data":$data}""",HttpStatusCode.OK,headersOf(HttpHeaders.ContentType,"application/json"))
        }) {
            install(ContentNegotiation) {json()}
            install(HttpTimeout) {requestTimeoutMillis=5000;socketTimeoutMillis=5000}
        }.use { client ->
            val session=SessionStore().apply {firebaseJwt="synthetic-token";apiBaseUrl="https://example.test"}
            val api=AuthSessionApi(session,client)
            api.uploadMedia("synthetic-token","video.mp4",byteArrayOf(1,2,3))
            api.settings()
            assertEquals("/api/v1/media/upload" to 120000L,seen[0])
            assertEquals("/api/v1/me/settings",seen[1].first)
            assertEquals(5000L,seen[1].second)
        }
    }
}
