package com.onlygoodthings.shared.data.remote

import com.onlygoodthings.shared.data.ParkingRepository
import com.onlygoodthings.shared.data.SessionStore
import com.onlygoodthings.shared.domain.ApiResponse
import com.onlygoodthings.shared.domain.GeoPoint
import com.onlygoodthings.shared.domain.ParkingCompleteResult
import com.onlygoodthings.shared.domain.ParkingHandoff
import com.onlygoodthings.shared.domain.ParkingSpot
import com.onlygoodthings.shared.protocol.frames.LocationRole
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

class RestParkingRepository(
    private val client: HttpClient,
    private val session: SessionStore,
) : ParkingRepository {

    override suspend fun publishVacancy(
        location: GeoPoint,
        ttlMinutes: Int,
        notes: String?,
        vehicleLabel: String?,
        ownerLocation: GeoPoint?,
        leftoverNow: Boolean,
    ): ParkingSpot = post("/api/v1/parking/publish", buildMap {
        put("latitude", JsonPrimitive(location.latitude))
        put("longitude", JsonPrimitive(location.longitude))
        put("ttlMinutes", JsonPrimitive(ttlMinutes))
        notes?.let { put("notes", JsonPrimitive(it)) }
        vehicleLabel?.let { put("vehicleLabel", JsonPrimitive(it)) }
        ownerLocation?.let {
            put("ownerLatitude", JsonPrimitive(it.latitude))
            put("ownerLongitude", JsonPrimitive(it.longitude))
        }
        if (leftoverNow) put("leftoverNow", JsonPrimitive(true))
    })

    override suspend fun nearby(location: GeoPoint, radiusMeters: Int): List<ParkingSpot> =
        post("/api/v1/parking/nearby", geo(location) + ("radius" to JsonPrimitive(radiusMeters)))

    override suspend fun expressInterest(spotId: String, location: GeoPoint): ParkingSpot =
        post("/api/v1/parking/interest", geo(location) + ("spotId" to JsonPrimitive(spotId)))

    override suspend fun claim(
        spotId: String,
        expectedVersion: Int,
        location: GeoPoint,
    ): ParkingSpot = post("/api/v1/parking/claim", geo(location) + mapOf(
        "spotId" to JsonPrimitive(spotId),
        "expectedVersion" to JsonPrimitive(expectedVersion),
    ))

    override suspend fun tick(
        spotId: String,
        location: GeoPoint,
        role: LocationRole?,
        speedMps: Double?,
    ): ParkingSpot = post("/api/v1/parking/tick", geo(location) + buildMap {
        put("spotId", JsonPrimitive(spotId))
        role?.let { put("role", JsonPrimitive(it.name)) }
        speedMps?.let { put("speedMps", JsonPrimitive(it)) }
    })

    override suspend fun complete(spotId: String, location: GeoPoint): ParkingCompleteResult =
        post("/api/v1/parking/complete", geo(location) + ("spotId" to JsonPrimitive(spotId)))

    override suspend fun cancel(spotId: String): ParkingSpot =
        post("/api/v1/parking/cancel", mapOf("spotId" to JsonPrimitive(spotId)))

    override suspend fun foundOtherPlace(spotId: String): ParkingSpot =
        post("/api/v1/parking/found-other", mapOf("spotId" to JsonPrimitive(spotId)))

    override suspend fun active(): List<ParkingSpot> = post("/api/v1/parking/active", emptyMap())

    override suspend fun history(): List<ParkingHandoff> = post("/api/v1/parking/history", emptyMap())

    override suspend fun get(spotId: String): ParkingSpot? =
        post("/api/v1/parking/get", mapOf("spotId" to JsonPrimitive(spotId)))

    private fun geo(location: GeoPoint): Map<String, JsonPrimitive> = mapOf(
        "latitude" to JsonPrimitive(location.latitude),
        "longitude" to JsonPrimitive(location.longitude),
    )

    private suspend inline fun <reified T> post(
        path: String,
        body: Map<String, JsonPrimitive>,
    ): T {
        val response: ApiResponse<T> = client.post("${session.apiBaseUrl}$path") {
            contentType(ContentType.Application.Json)
            session.firebaseJwt?.let { header("Authorization", "Bearer $it") }
            setBody(JsonObject(body))
        }.body()
        return response.data ?: error(response.message ?: "Respuesta vacía")
    }
}
