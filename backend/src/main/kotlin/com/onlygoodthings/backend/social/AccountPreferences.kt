package com.onlygoodthings.backend.social

import com.onlygoodthings.backend.http.JsonBody
import com.onlygoodthings.shared.domain.AccountSettings
import kotlinx.serialization.json.Json
import java.sql.Connection
import java.time.ZoneId

internal val settingBooleanKeys = setOf("publicProfileVisible", "showExactMatchLocation", "animalAlertPush", "skillAlertPush", "parkingRadarSounds", "radarEnabled", "carbonSaveMode", "quietHoursEnabled")

internal fun validateSettings(data: Map<String, Any?>): Map<String, Any?> {
    if(data.containsKey("language"))require(data["language"] in setOf("es","en")) { "Idioma inválido" }
    if(data.containsKey("barrio"))require(data["barrio"] is String && (data["barrio"] as String).length<=80) { "Comunidad inválida" }
    data.filterKeys { it in settingBooleanKeys }.forEach { (key, value) -> require(value is Boolean) { "El ajuste $key debe ser verdadero o falso" } }
    if (data.containsKey("timeZone")) {
        require(data["timeZone"] is String) { "Zona horaria inválida" }
        val zone = data["timeZone"] as String
        require(zone.length <= 100 && runCatching { ZoneId.of(zone) }.isSuccess) { "Zona horaria inválida" }
    }
    return data.filterKeys { it in settingBooleanKeys || it in setOf("timeZone","language","barrio") }
}

internal fun readAccountSettings(connection: Connection, userId: String): AccountSettings =
    connection.prepareStatement("SELECT COALESCE(metadata->'settings','{}'::jsonb)::text FROM users WHERE id=?::uuid").use { q ->
        q.setString(1,userId)
        q.executeQuery().use { r ->
            require(r.next()) { "Cuenta no disponible" }
            Json { ignoreUnknownKeys = true }.decodeFromString<AccountSettings>(r.getString(1))
        }
    }

/** Existing public posts retain their author attribution; profile/story access follows this policy. */
internal fun profileVisibilitySql(viewerId: String, alias: String): String {
    java.util.UUID.fromString(viewerId)
    require(alias.matches(Regex("[a-z_]+")))
    return "($alias.id='$viewerId'::uuid OR COALESCE(($alias.metadata->'settings'->>'publicProfileVisible')::boolean,true) OR EXISTS(SELECT 1 FROM follows vf WHERE vf.follower_id='$viewerId'::uuid AND vf.followed_id=$alias.id))"
}
