package com.onlygoodthings.app.map

import com.onlygoodthings.shared.data.createPlatformHttpClient
import com.onlygoodthings.shared.domain.GeoPoint
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

data class AddressHit(
    val label: String,
    val point: GeoPoint,
)

private val geoClient by lazy { createPlatformHttpClient() }
private val geoJson = Json { ignoreUnknownKeys = true }

/** Geocoder OSM Nominatim: sin clave de Google. */
suspend fun searchAddress(query: String, lang: String = "es"): List<AddressHit> {
    val q = query.trim()
    if (q.length < 3) return emptyList()
    val response = geoClient.get("https://nominatim.openstreetmap.org/search") {
        parameter("format", "json")
        parameter("limit", 5)
        parameter("q", q)
        parameter("addressdetails", 1)
        parameter("accept-language", lang)
        header("User-Agent", "OnlyGoodThings/0.1 (Compose Multiplatform; parking)")
    }
    if (!response.status.isSuccess()) return emptyList()
    val root = geoJson.parseToJsonElement(response.bodyAsText()).jsonArray
    return root.mapNotNull { el -> parseHit(el.jsonObject) }
}

/** Lat/lng del GPS → dirección normalizada (calle, número, zona). */
suspend fun reverseGeocode(point: GeoPoint, lang: String = "es"): AddressHit? {
    val response = geoClient.get("https://nominatim.openstreetmap.org/reverse") {
        parameter("format", "json")
        parameter("lat", point.latitude)
        parameter("lon", point.longitude)
        parameter("addressdetails", 1)
        parameter("zoom", 18)
        parameter("accept-language", lang)
        header("User-Agent", "OnlyGoodThings/0.1 (Compose Multiplatform; parking)")
    }
    if (!response.status.isSuccess()) return null
    return parseHit(geoJson.parseToJsonElement(response.bodyAsText()).jsonObject)
}

private fun parseHit(obj: JsonObject): AddressHit? {
    val lat = obj["lat"]?.jsonPrimitive?.content?.toDoubleOrNull() ?: return null
    val lng = obj["lon"]?.jsonPrimitive?.content?.toDoubleOrNull() ?: return null
    val raw = obj["display_name"]?.jsonPrimitive?.content.orEmpty()
    val address = obj["address"]?.jsonObject
    val label = normalizeAddress(address, raw)
    if (label.isBlank()) return null
    return AddressHit(label, GeoPoint(lat, lng))
}

/** Calle y número primero; zona y ciudad después. Sin el párrafo largo de Nominatim. */
internal fun normalizeAddress(address: JsonObject?, fallback: String): String {
    if (address == null) return fallback.trim()
    val road = firstOf(address, "road", "pedestrian", "footway", "path", "residential")
    val number = firstOf(address, "house_number")
    val street = listOfNotNull(road, number).joinToString(" ").ifBlank { null }
    val barrio = firstOf(address, "neighbourhood", "suburb", "quarter", "city_district")
    val city = firstOf(address, "city", "town", "village", "municipality")
    val state = firstOf(address, "state")
    val parts = listOfNotNull(street, barrio, city, state).distinct()
    return parts.joinToString(", ").ifBlank { fallback.trim() }
}

private fun firstOf(obj: JsonObject, vararg keys: String): String? {
    keys.forEach { key ->
        val value = obj[key]?.jsonPrimitive?.content?.trim().orEmpty()
        if (value.isNotBlank()) return value
    }
    return null
}
