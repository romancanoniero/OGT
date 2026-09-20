package com.onlygoodthings.shared.data

import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logging
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import java.util.concurrent.TimeUnit

actual fun createPlatformHttpClient(): HttpClient = HttpClient(OkHttp) {
    install(WebSockets)
    install(ContentNegotiation) {
        json(Json { ignoreUnknownKeys = true; encodeDefaults = true })
    }
    install(Logging) { level = LogLevel.INFO }
}

actual fun createTileHttpClient(): HttpClient = HttpClient(OkHttp) {
    engine {
        config {
            connectTimeout(3, TimeUnit.SECONDS)
            readTimeout(5, TimeUnit.SECONDS)
            retryOnConnectionFailure(true)
        }
    }
    install(HttpTimeout) {
        requestTimeoutMillis = 5_000
        connectTimeoutMillis = 3_000
        socketTimeoutMillis = 5_000
    }
}
