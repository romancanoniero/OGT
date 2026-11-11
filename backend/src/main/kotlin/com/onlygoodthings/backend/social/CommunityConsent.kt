package com.onlygoodthings.backend.social

import com.onlygoodthings.shared.domain.COMMUNITY_TERMS_VERSION
import com.onlygoodthings.shared.domain.CommunityConsent
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.sql.Connection

internal fun validateCommunityConsent(data: Map<String, Any?>) {
    require(data.keys == setOf("version", "adult", "accepted")) { "Confirmación inválida" }
    require(data["version"] == COMMUNITY_TERMS_VERSION) { "Leé la versión actual de los términos" }
    require(data["adult"] == true) { "Only Good Things es para mayores de 18 años" }
    require(data["accepted"] == true) { "Aceptá los términos y las normas de la comunidad" }
}

internal fun readCommunityConsent(connection: Connection, userId: String): CommunityConsent =
    connection.prepareStatement("SELECT COALESCE(metadata->'communityConsent','{}'::jsonb)::text FROM users WHERE id=?::uuid").use { query ->
        query.setString(1, userId)
        query.executeQuery().use { rows ->
            require(rows.next()) { "Cuenta no disponible" }
            Json { ignoreUnknownKeys = true }.decodeFromString<CommunityConsent>(rows.getString(1))
        }
    }

internal fun saveCommunityConsent(connection: Connection, userId: String, data: Map<String, Any?>): CommunityConsent {
    validateCommunityConsent(data)
    val consent = CommunityConsent(COMMUNITY_TERMS_VERSION, true, System.currentTimeMillis())
    connection.prepareStatement("UPDATE users SET metadata=COALESCE(metadata,'{}'::jsonb) || jsonb_build_object('communityConsent',?::jsonb) WHERE id=?::uuid").use { query ->
        query.setString(1, Json.encodeToString(consent))
        query.setString(2, userId)
        require(query.executeUpdate() == 1) { "Cuenta no disponible" }
    }
    return consent
}
