package com.onlygoodthings.backend.routing

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.onlygoodthings.shared.domain.GeoMath
import com.onlygoodthings.shared.domain.GeoPoint
import org.slf4j.LoggerFactory
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

/**
 * ETA a pie: Valhalla (peatón) → OSRM foot → haversine 1.4 m/s.
 * El celular no habla con estos motores.
 */
class FootRoutingService(
    private val valhallaUrl: String,
    private val osrmBaseUrl: String,
    private val http: HttpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofMillis(2_500))
        .build(),
    private val mapper: ObjectMapper = ObjectMapper(),
) {
    private val log = LoggerFactory.getLogger(FootRoutingService::class.java)

    fun walkEtaSeconds(from: GeoPoint, to: GeoPoint): Int {
        val meters = GeoMath.haversineMeters(from, to)
        if (meters < 8.0) return 1
        val routed = valhallaEta(from, to) ?: osrmEta(from, to)
        return routed ?: GeoMath.walkEtaSeconds(from, to)
    }

    private fun valhallaEta(from: GeoPoint, to: GeoPoint): Int? {
        if (valhallaUrl.isBlank()) return null
        val body = """
            {"locations":[{"lat":${from.latitude},"lon":${from.longitude}},{"lat":${to.latitude},"lon":${to.longitude}}],"costing":"pedestrian","directions_options":{"units":"kilometers"}}
        """.trimIndent()
        return runCatching {
            val json = postJson(valhallaUrl, body) ?: return null
            val time = json.path("trip").path("summary").path("time").asDouble(Double.NaN)
            if (time.isNaN() || time <= 0.0) null else time.toInt().coerceAtLeast(1)
        }.onFailure { log.warn("Valhalla no respondió, pruebo OSRM: {}", it.message) }.getOrNull()
    }

    private fun osrmEta(from: GeoPoint, to: GeoPoint): Int? {
        if (osrmBaseUrl.isBlank()) return null
        val url = "${osrmBaseUrl.trimEnd('/')}/route/v1/foot/${from.longitude},${from.latitude};${to.longitude},${to.latitude}?overview=false"
        return runCatching {
            val json = getJson(url) ?: return null
            val duration = json.path("routes").path(0).path("duration").asDouble(Double.NaN)
            if (duration.isNaN() || duration <= 0.0) null else duration.toInt().coerceAtLeast(1)
        }.onFailure { log.warn("OSRM no respondió, uso haversine: {}", it.message) }.getOrNull()
    }

    private fun getJson(url: String): JsonNode? {
        val request = HttpRequest.newBuilder(URI.create(url))
            .timeout(Duration.ofMillis(2_800))
            .GET()
            .header("User-Agent", "OnlyGoodThings/parking")
            .build()
        val response = http.send(request, HttpResponse.BodyHandlers.ofString())
        if (response.statusCode() !in 200..299) return null
        return mapper.readTree(response.body())
    }

    private fun postJson(url: String, body: String): JsonNode? {
        val request = HttpRequest.newBuilder(URI.create(url))
            .timeout(Duration.ofMillis(2_800))
            .header("Content-Type", "application/json")
            .header("User-Agent", "OnlyGoodThings/parking")
            .POST(HttpRequest.BodyPublishers.ofString(body))
            .build()
        val response = http.send(request, HttpResponse.BodyHandlers.ofString())
        if (response.statusCode() !in 200..299) return null
        return mapper.readTree(response.body())
    }

    companion object {
        fun fromEnv(): FootRoutingService = FootRoutingService(
            valhallaUrl = System.getenv("OGT_VALHALLA_URL")?.takeIf { it.isNotBlank() }
                ?: "https://valhalla1.openstreetmap.de/route",
            osrmBaseUrl = System.getenv("OGT_OSRM_URL")?.takeIf { it.isNotBlank() }
                ?: "https://router.project-osrm.org",
        )
    }
}
