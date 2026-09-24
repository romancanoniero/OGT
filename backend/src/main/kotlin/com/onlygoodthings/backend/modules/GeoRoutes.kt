package com.onlygoodthings.backend.modules

import com.onlygoodthings.backend.http.JsonBody
import com.onlygoodthings.backend.http.reqDouble
import com.onlygoodthings.backend.http.reqString
import com.onlygoodthings.shared.domain.ApiResponse
import com.onlygoodthings.shared.domain.GeoHit
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.net.URI
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.charset.StandardCharsets
import java.time.Duration
import java.util.concurrent.ConcurrentHashMap

private const val NominatimUa = "OnlyGoodThings/0.1 (web; https://onlygoodthings.lat)"
private const val NominatimBase = "https://nominatim.openstreetmap.org"
private val geoJson = Json { ignoreUnknownKeys = true }

/**
 * Proxy a Nominatim. El navegador no puede mandar el User-Agent de producto;
 * acá sí, y cacheamos para no golpear el 1 req/s.
 */
fun Route.geoRoutes() {
    val nominatim = NominatimClient()

    post("/api/v1/geo/search") {
        requireUser(call) ?: return@post
        val dataMap = JsonBody.receiveMap(call)
        val query = dataMap.reqString("query").trim()
        if (query.length < 3) {
            return@post call.respond(ApiResponse.ok(emptyList<GeoHit>()))
        }
        val lang = dataMap["lang"] as? String ?: "es"
        val hits = runCatching { nominatim.search(query, lang) }.getOrElse {
            return@post call.respond(
                HttpStatusCode.BadGateway,
                ApiResponse.fail<Unit>("No se pudo buscar la calle", "GEO_UPSTREAM"),
            )
        }
        call.respond(ApiResponse.ok(hits))
    }

    post("/api/v1/geo/reverse") {
        requireUser(call) ?: return@post
        val dataMap = JsonBody.receiveMap(call)
        val lat = dataMap.reqDouble("latitude")
        val lng = dataMap.reqDouble("longitude")
        val lang = dataMap["lang"] as? String ?: "es"
        val hit = runCatching { nominatim.reverse(lat, lng, lang) }.getOrElse {
            return@post call.respond(
                HttpStatusCode.BadGateway,
                ApiResponse.fail<Unit>("No se pudo leer esa esquina", "GEO_UPSTREAM"),
            )
        }
        if (hit == null) {
            return@post call.respond(HttpStatusCode.NotFound, ApiResponse.fail<Unit>("Sin dirección", "NOT_FOUND"))
        }
        call.respond(ApiResponse.ok(hit))
    }
}

private class NominatimClient {
    private val http = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(8))
        .build()
    private val cache = ConcurrentHashMap<String, Pair<Long, List<GeoHit>>>()
    private val lock = Any()
    @Volatile private var lastCallMs = 0L

    fun search(query: String, lang: String): List<GeoHit> {
        val key = "s:${lang}:${query.lowercase()}"
        cached(key)?.let { return it }
        val encoded = URLEncoder.encode(query, StandardCharsets.UTF_8)
        val uri = URI.create(
            "$NominatimBase/search?format=json&limit=5&addressdetails=1&accept-language=$lang&q=$encoded",
        )
        val root = geoJson.parseToJsonElement(get(uri)).jsonArray
        val hits = root.mapNotNull { parseHit(it.jsonObject) }
        remember(key, hits)
        return hits
    }

    fun reverse(lat: Double, lng: Double, lang: String): GeoHit? {
        val key = "r:${lang}:${"%.5f".format(lat)},${"%.5f".format(lng)}"
        cached(key)?.firstOrNull()?.let { return it }
        val uri = URI.create(
            "$NominatimBase/reverse?format=json&addressdetails=1&zoom=18&accept-language=$lang&lat=$lat&lon=$lng",
        )
        val hit = parseHit(geoJson.parseToJsonElement(get(uri)).jsonObject)
        remember(key, listOfNotNull(hit))
        return hit
    }

    private fun cached(key: String): List<GeoHit>? {
        val row = cache[key] ?: return null
        if (System.currentTimeMillis() - row.first > 10 * 60_000) {
            cache.remove(key)
            return null
        }
        return row.second
    }

    private fun remember(key: String, hits: List<GeoHit>) {
        cache[key] = System.currentTimeMillis() to hits
    }

    private fun get(uri: URI): String {
        throttle()
        val request = HttpRequest.newBuilder(uri)
            .timeout(Duration.ofSeconds(10))
            .header("User-Agent", NominatimUa)
            .header("Accept", "application/json")
            .GET()
            .build()
        val response = http.send(request, HttpResponse.BodyHandlers.ofString())
        check(response.statusCode() in 200..299) { "Nominatim ${response.statusCode()}" }
        return response.body()
    }

    private fun throttle() {
        synchronized(lock) {
            val wait = 1_100L - (System.currentTimeMillis() - lastCallMs)
            if (wait > 0) Thread.sleep(wait)
            lastCallMs = System.currentTimeMillis()
        }
    }
}

private fun parseHit(obj: JsonObject): GeoHit? {
    val lat = obj["lat"]?.jsonPrimitive?.content?.toDoubleOrNull() ?: return null
    val lng = obj["lon"]?.jsonPrimitive?.content?.toDoubleOrNull() ?: return null
    val raw = obj["display_name"]?.jsonPrimitive?.content.orEmpty()
    val address = obj["address"]?.jsonObject
    val label = normalizeAddress(address, raw)
    if (label.isBlank()) return null
    return GeoHit(label, lat, lng)
}

/** Calle y número primero; barrio y ciudad después. Igual que en la app. */
private fun normalizeAddress(address: JsonObject?, fallback: String): String {
    if (address == null) return fallback.trim()
    val road = firstOf(address, "road", "pedestrian", "footway", "path", "residential")
    val number = firstOf(address, "house_number")
    val street = listOfNotNull(road, number).joinToString(" ").ifBlank { null }
    val barrio = firstOf(address, "neighbourhood", "suburb", "quarter", "city_district")
    val city = firstOf(address, "city", "town", "village", "municipality")
    val state = firstOf(address, "state")
    return listOfNotNull(street, barrio, city, state).distinct().joinToString(", ").ifBlank { fallback.trim() }
}

private fun firstOf(obj: JsonObject, vararg keys: String): String? {
    keys.forEach { key ->
        val value = obj[key]?.jsonPrimitive?.content?.trim().orEmpty()
        if (value.isNotBlank()) return value
    }
    return null
}
