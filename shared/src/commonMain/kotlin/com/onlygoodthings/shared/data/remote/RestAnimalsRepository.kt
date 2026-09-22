package com.onlygoodthings.shared.data.remote

import com.onlygoodthings.shared.data.SessionStore
import com.onlygoodthings.shared.domain.AnimalListingDto
import com.onlygoodthings.shared.domain.AnimalResolveResult
import com.onlygoodthings.shared.domain.ApiResponse
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject

class RestAnimalsRepository(
    private val client: HttpClient,
    private val session: SessionStore,
) {
    suspend fun publish(body: JsonObject): AnimalListingDto =
        post("/api/v1/animals/publish", body)

    suspend fun update(body: JsonObject): AnimalListingDto =
        post("/api/v1/animals/update", body)

    suspend fun openListings(): List<AnimalListingDto> =
        post("/api/v1/animals/open", buildJsonObject {})

    suspend fun resolve(listingId: String): AnimalResolveResult =
        post("/api/v1/animals/resolve", buildJsonObject {
            put("listingId", kotlinx.serialization.json.JsonPrimitive(listingId))
        })

    private suspend inline fun <reified T> post(path: String, body: JsonObject): T {
        val response: ApiResponse<T> = client.post("${session.apiBaseUrl}$path") {
            contentType(ContentType.Application.Json)
            session.firebaseJwt?.let { header("Authorization", "Bearer $it") }
            setBody(body)
        }.body()
        return response.data ?: error(response.message ?: "Respuesta vacía")
    }
}
