package com.onlygoodthings.backend.modules

import com.onlygoodthings.shared.domain.GeoHit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import java.net.URI
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.charset.StandardCharsets
import java.time.Duration

/** Photon supports search-as-you-type; the public Nominatim service explicitly forbids it. */
internal class PublicationGeocoder(
    baseUrl: String = System.getenv("OGT_PHOTON_URL")?.takeIf { it.isNotBlank() } ?: "https://photon.komoot.io",
    private val minimumIntervalMs: Long = 1_100,
    private val now: () -> Long = System::currentTimeMillis,
) {
    private val base = baseUrl.trimEnd('/')
    private val http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(4)).build()
    private val lock = Mutex()
    private val cache = linkedMapOf<String, Pair<Long, List<GeoHit>>>()
    private var lastCallMs = 0L

    suspend fun search(query: String, language: String = "es"): List<GeoHit> {
        val q = query.trim()
        require(q.length <= 200) { "El lugar admite hasta 200 caracteres" }
        if (q.length < 3) return emptyList()
        val lang = language(language)
        return get("/api/?q=${encode(q)}&lang=$lang&limit=5")
    }

    suspend fun reverse(latitude: Double, longitude: Double, language: String = "es"): GeoHit? {
        require(latitude.isFinite() && latitude in -90.0..90.0 && longitude.isFinite() && longitude in -180.0..180.0) { "Ubicación inválida" }
        return get("/reverse?lat=$latitude&lon=$longitude&lang=${language(language)}&limit=1").firstOrNull()
    }

    // One bounded cache and queue per application, including concurrent requests for the same place.
    private suspend fun get(path: String): List<GeoHit> = lock.withLock {
        val cached = cache[path]
        if (cached != null && now() - cached.first < 10 * 60_000) return@withLock cached.second
        delay((minimumIntervalMs - (now() - lastCallMs)).coerceAtLeast(0))
        lastCallMs = now()
        val body = withContext(Dispatchers.IO) {
            val request = HttpRequest.newBuilder(URI.create(base + path)).timeout(Duration.ofSeconds(8))
                .header("User-Agent", "OnlyGoodThings/1.0 (https://onlygoodthings.lat)")
                .header("Accept", "application/json").GET().build()
            val response = http.send(request, HttpResponse.BodyHandlers.ofString())
            check(response.statusCode() in 200..299) { "Place service unavailable" }
            response.body()
        }
        val hits = parsePhotonPlaces(body)
        cache[path] = now() to hits
        while (cache.size > 1_000) cache.remove(cache.keys.first())
        hits
    }

    // Photon uses the local name for unsupported languages; never interpolate arbitrary URL fragments.
    private fun language(value: String): String = value.lowercase().takeIf { it in setOf("en", "de", "fr") } ?: "default"
    private fun encode(value: String) = URLEncoder.encode(value, StandardCharsets.UTF_8)
}

internal fun parsePhotonPlaces(body: String): List<GeoHit> {
    val root = Json.parseToJsonElement(body).jsonObject
    val features = root["features"]?.jsonArray ?: error("Invalid place service response")
    return features.take(5).mapNotNull { feature ->
        val obj = feature as? JsonObject ?: return@mapNotNull null
        val coordinates = (obj["geometry"] as? JsonObject)?.get("coordinates") as? JsonArray ?: return@mapNotNull null
        val lng = (coordinates.getOrNull(0) as? JsonPrimitive)?.doubleOrNull ?: return@mapNotNull null
        val lat = (coordinates.getOrNull(1) as? JsonPrimitive)?.doubleOrNull ?: return@mapNotNull null
        if (!lat.isFinite() || lat !in -90.0..90.0 || !lng.isFinite() || lng !in -180.0..180.0) return@mapNotNull null
        val properties = obj["properties"] as? JsonObject ?: return@mapNotNull null
        fun text(key: String) = (properties[key] as? JsonPrimitive)?.contentOrNull?.trim()?.takeIf { it.isNotEmpty() }
        val street = listOfNotNull(text("street"), text("housenumber")).joinToString(" ").takeIf { it.isNotBlank() }
        val label = listOfNotNull(text("name"), street, text("district"), text("city") ?: text("county"), text("state"), text("country"))
            .distinct().joinToString(", ").take(200).trimEnd(' ', ',')
        if (label.isBlank()) null else GeoHit(label, lat, lng)
    }.distinctBy { Triple(it.label, it.latitude, it.longitude) }
}
