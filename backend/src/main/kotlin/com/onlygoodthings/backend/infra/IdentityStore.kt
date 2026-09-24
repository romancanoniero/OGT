package com.onlygoodthings.backend.infra

import com.onlygoodthings.backend.auth.VerifiedFirebaseUser
import com.onlygoodthings.shared.domain.ProfileSettings
import com.onlygoodthings.shared.domain.UserProfile
import com.onlygoodthings.shared.domain.UserRole
import com.onlygoodthings.shared.domain.titleCasePersonName
import kotlinx.serialization.json.Json
import java.util.UUID

class IdentityStore(private val db: Database) {

    private val settingsCodec = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    private val profileSelect = """
        SELECT id, firebase_uid, display_name, photo_url, role, community_points, invite_code,
               metadata -> 'settings' AS settings_json
        FROM users
    """.trimIndent()

    fun upsertFromFirebase(verified: VerifiedFirebaseUser): UserProfile = db.withConnection { connection ->
        val existing = connection.prepareStatement("$profileSelect WHERE firebase_uid = ?").use { stmt ->
            stmt.setString(1, verified.firebaseUid)
            stmt.executeQuery().use { rs -> if (rs.next()) rs.toUserProfile() else null }
        }
        if (existing != null) {
            val incomingName = verified.displayName?.trim().orEmpty()
            val labName = "${verified.firebaseUid}@dev.onlygoodthings.test"
            val staleName = existing.displayName.isNullOrBlank() ||
                existing.displayName == verified.firebaseUid ||
                existing.displayName == labName
            val nextName = when {
                staleName && incomingName.isNotBlank() && incomingName != verified.firebaseUid -> incomingName
                staleName -> verified.email ?: existing.displayName
                else -> existing.displayName
            }
            connection.prepareStatement(
                "UPDATE users SET email = COALESCE(?, email), phone_e164 = COALESCE(?, phone_e164), display_name = ? WHERE firebase_uid = ?",
            ).use { stmt ->
                stmt.setString(1, verified.email)
                stmt.setString(2, verified.phone)
                stmt.setString(3, nextName)
                stmt.setString(4, verified.firebaseUid)
                stmt.executeUpdate()
            }
            com.onlygoodthings.backend.social.claimHonorsMatchingContact(
                connection,
                existing.id,
                verified.email,
                verified.phone,
            )
            return@withConnection existing.copy(displayName = nextName)
        }

        val id = UUID.randomUUID().toString()
        val invite = verified.firebaseUid.take(8).uppercase() + "-GOOD"
        val display = verified.displayName ?: verified.email ?: verified.firebaseUid
        connection.prepareStatement(
            """
            INSERT INTO users (id, firebase_uid, email, phone_e164, display_name, role, invite_code)
            VALUES (?::uuid, ?, ?, ?, ?, ?::user_role, ?)
            """.trimIndent(),
        ).use { stmt ->
            stmt.setString(1, id)
            stmt.setString(2, verified.firebaseUid)
            stmt.setString(3, verified.email)
            stmt.setString(4, verified.phone)
            stmt.setString(5, display)
            stmt.setString(6, verified.role.name)
            stmt.setString(7, invite)
            stmt.executeUpdate()
        }
        com.onlygoodthings.backend.social.claimHonorsMatchingContact(connection, id, verified.email, verified.phone)
        UserProfile(
            id = id,
            firebaseUid = verified.firebaseUid,
            displayName = display,
            photoUrl = null,
            role = verified.role,
            communityPoints = 0,
            inviteCode = invite,
            settings = ProfileSettings(),
        )
    }

    fun byId(userId: String): UserProfile? = db.withConnection { connection ->
        connection.prepareStatement("$profileSelect WHERE id = ?::uuid").use { stmt ->
            stmt.setString(1, userId)
            stmt.executeQuery().use { rs -> if (rs.next()) rs.toUserProfile() else null }
        }
    }

    /** Nombre, foto y preferencias que la app y la web comparten. */
    fun updateProfile(
        userId: String,
        displayName: String?,
        photoUrl: String?,
        settingsPatch: ProfileSettingsPatch,
    ): UserProfile = db.withConnection { connection ->
        val current = connection.prepareStatement("$profileSelect WHERE id = ?::uuid").use { stmt ->
            stmt.setString(1, userId)
            stmt.executeQuery().use { rs ->
                if (!rs.next()) error("Vecino inexistente")
                rs.toUserProfile()
            }
        }
        val nextName = displayName?.let { sanitizeDisplayName(it) } ?: current.displayName
        val nextPhoto = photoUrl?.let { sanitizePhotoUrl(it) } ?: current.photoUrl
        val nextSettings = current.settings.merge(settingsPatch)
        val settingsRaw = settingsCodec.encodeToString(ProfileSettings.serializer(), nextSettings)
        connection.prepareStatement(
            """
            UPDATE users
            SET display_name = ?,
                photo_url = ?,
                metadata = metadata || jsonb_build_object('settings', ?::jsonb),
                updated_at = now()
            WHERE id = ?::uuid
            """.trimIndent(),
        ).use { stmt ->
            stmt.setString(1, nextName)
            stmt.setString(2, nextPhoto)
            stmt.setString(3, settingsRaw)
            stmt.setString(4, userId)
            stmt.executeUpdate()
        }
        current.copy(displayName = nextName, photoUrl = nextPhoto, settings = nextSettings)
    }

    private fun java.sql.ResultSet.toUserProfile(): UserProfile = UserProfile(
        id = getString("id"),
        firebaseUid = getString("firebase_uid"),
        displayName = getString("display_name"),
        photoUrl = stringOrNull("photo_url"),
        role = UserRole.valueOf(getString("role")),
        communityPoints = getInt("community_points"),
        inviteCode = getString("invite_code"),
        settings = parseSettings(stringOrNull("settings_json")),
    )

    private fun parseSettings(raw: String?): ProfileSettings {
        if (raw.isNullOrBlank() || raw == "null") return ProfileSettings()
        return runCatching { settingsCodec.decodeFromString(ProfileSettings.serializer(), raw) }
            .getOrDefault(ProfileSettings())
    }

    private fun sanitizeDisplayName(raw: String): String {
        val named = titleCasePersonName(raw.trim()).replace(Regex("\\s+"), " ")
        require(named.length in 2..80) { "El nombre debe tener entre 2 y 80 caracteres" }
        return named
    }

    private fun sanitizePhotoUrl(raw: String): String {
        val url = raw.trim()
        require(url.length <= 500) { "URL de foto demasiado larga" }
        val ok = url.startsWith("https://") || url.startsWith("http://") || url.startsWith("/media/")
        require(ok) { "La foto tiene que ser una URL de media" }
        return url
    }

    /** Última posición conocida del vecino. No es historial de ruta. */
    fun updateHomeLocation(
        userId: String,
        latitude: Double,
        longitude: Double,
        accuracyMeters: Double?,
    ) {
        require(latitude in -90.0..90.0) { "latitude fuera de rango" }
        require(longitude in -180.0..180.0) { "longitude fuera de rango" }
        db.withConnection { connection ->
            connection.prepareStatement(
                """
                UPDATE users
                SET home_location = ST_SetSRID(ST_MakePoint(?, ?), 4326),
                    last_seen_at = now(),
                    updated_at = now(),
                    metadata = metadata || jsonb_build_object('last_accuracy_m', ?)
                WHERE id = ?::uuid
                """.trimIndent(),
            ).use { stmt ->
                stmt.setDouble(1, longitude)
                stmt.setDouble(2, latitude)
                if (accuracyMeters != null) stmt.setDouble(3, accuracyMeters) else stmt.setObject(3, null)
                stmt.setString(4, userId)
                stmt.executeUpdate()
            }
        }
    }
}

/** Campos opcionales: si vienen nulos, se deja el valor actual. */
data class ProfileSettingsPatch(
    val language: String? = null,
    val barrio: String? = null,
    val publicProfileVisible: Boolean? = null,
    val showExactMatchLocation: Boolean? = null,
    val animalAlertPush: Boolean? = null,
    val skillAlertPush: Boolean? = null,
    val parkingRadarSounds: Boolean? = null,
    val radarEnabled: Boolean? = null,
    val carbonSaveMode: Boolean? = null,
)

private fun ProfileSettings.merge(patch: ProfileSettingsPatch): ProfileSettings = copy(
    language = patch.language?.let { lang ->
        require(lang == "es" || lang == "en") { "Idioma no soportado" }
        lang
    } ?: language,
    barrio = patch.barrio?.trim()?.take(80)?.ifBlank { null } ?: barrio,
    publicProfileVisible = patch.publicProfileVisible ?: publicProfileVisible,
    showExactMatchLocation = patch.showExactMatchLocation ?: showExactMatchLocation,
    animalAlertPush = patch.animalAlertPush ?: animalAlertPush,
    skillAlertPush = patch.skillAlertPush ?: skillAlertPush,
    parkingRadarSounds = patch.parkingRadarSounds ?: parkingRadarSounds,
    radarEnabled = patch.radarEnabled ?: radarEnabled,
    carbonSaveMode = patch.carbonSaveMode ?: carbonSaveMode,
)
