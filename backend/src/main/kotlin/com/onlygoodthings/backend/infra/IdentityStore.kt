package com.onlygoodthings.backend.infra

import com.onlygoodthings.backend.auth.AccountExport
import com.onlygoodthings.backend.auth.AccountTextRow
import com.onlygoodthings.backend.auth.VerifiedFirebaseUser
import com.onlygoodthings.shared.domain.UserProfile
import com.onlygoodthings.shared.domain.UserRole
import java.sql.Connection
import java.util.UUID

class IdentityStore(private val db: Database) {

    fun upsertFromFirebase(verified: VerifiedFirebaseUser): UserProfile = db.withConnection { connection ->
        val existing = connection.prepareStatement(
            "SELECT id, firebase_uid, display_name, photo_url, role, community_points, invite_code FROM users WHERE firebase_uid = ?",
        ).use { stmt ->
            stmt.setString(1, verified.firebaseUid)
            stmt.executeQuery().use { rs ->
                if (rs.next()) {
                    UserProfile(
                        id = rs.getString("id"),
                        firebaseUid = rs.getString("firebase_uid"),
                        displayName = rs.getString("display_name"),
                        photoUrl = rs.stringOrNull("photo_url"),
                        role = UserRole.valueOf(rs.getString("role")),
                        communityPoints = rs.getInt("community_points"),
                        inviteCode = rs.getString("invite_code"),
                    )
                } else {
                    null
                }
            }
        }
        if (existing != null) {
            // Firebase seeds a new profile; subsequent logins must preserve edits made in OGT.
            connection.prepareStatement(
                "UPDATE users SET email = COALESCE(?, email), phone_e164 = COALESCE(?, phone_e164) WHERE firebase_uid = ?",
            ).use { stmt ->
                stmt.setString(1, verified.email)
                stmt.setString(2, verified.phone)
                stmt.setString(3, verified.firebaseUid)
                stmt.executeUpdate()
            }
            com.onlygoodthings.backend.social.claimHonorsMatchingContact(
                connection,
                existing.id,
                verified.email,
                verified.phone,
            )
            return@withConnection existing
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
        )
    }

    fun byId(userId: String): UserProfile? = db.withConnection { connection ->
        connection.prepareStatement(
            "SELECT id, firebase_uid, display_name, photo_url, role, community_points, invite_code FROM users WHERE id = ?::uuid",
        ).use { stmt ->
            stmt.setString(1, userId)
            stmt.executeQuery().use { rs ->
                if (!rs.next()) return@use null
                UserProfile(
                    id = rs.getString("id"),
                    firebaseUid = rs.getString("firebase_uid"),
                    displayName = rs.getString("display_name"),
                    photoUrl = rs.stringOrNull("photo_url"),
                    role = UserRole.valueOf(rs.getString("role")),
                    communityPoints = rs.getInt("community_points"),
                    inviteCode = rs.getString("invite_code"),
                )
            }
        }
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

    /** Datos personales del propio usuario. No incluye filas de otras cuentas. */
    fun exportAccount(userId: String): AccountExport? = db.withConnection { connection ->
        val profile = connection.prepareStatement(
            """
            SELECT id::text, email::text, phone_e164, display_name, community_points
            FROM users WHERE id = ?::uuid
            """.trimIndent(),
        ).use { stmt ->
            stmt.setString(1, userId)
            stmt.executeQuery().use { rs ->
                if (!rs.next()) return@withConnection null
                AccountExport(
                    userId = rs.getString("id"),
                    email = rs.stringOrNull("email"),
                    phone = rs.stringOrNull("phone_e164"),
                    displayName = rs.getString("display_name"),
                    communityPoints = rs.getInt("community_points"),
                    posts = emptyList(),
                    comments = emptyList(),
                    messagesSent = emptyList(),
                )
            }
        }
        profile.copy(
            posts = textRows(connection, "SELECT id::text, body, created_at::text FROM social_posts WHERE author_user_id = ?::uuid ORDER BY created_at", userId),
            comments = textRows(connection, "SELECT id::text, body, created_at::text FROM post_comments WHERE author_user_id = ?::uuid ORDER BY created_at", userId),
            messagesSent = textRows(connection, "SELECT id::text, body, created_at::text FROM timebank_messages WHERE sender_id = ?::uuid ORDER BY created_at", userId),
        )
    }

    /**
     * Baja de la propia cuenta. Borra el contenido cuyo autor es el usuario
     * (los posts no pueden quedar con autor nulo) y las entregas de estacionamiento
     * que no tienen ON DELETE. El resto cae por cascada o queda anonimizado.
     */
    fun eraseAccount(userId: String) {
        db.withConnection { connection ->
            val previous = connection.autoCommit
            connection.autoCommit = false
            try {
                connection.prepareStatement(
                    "DELETE FROM parking_handoffs WHERE owner_user_id = ?::uuid OR claimant_user_id = ?::uuid",
                ).use { stmt ->
                    stmt.setString(1, userId)
                    stmt.setString(2, userId)
                    stmt.executeUpdate()
                }
                connection.prepareStatement(
                    "DELETE FROM social_posts WHERE author_user_id = ?::uuid",
                ).use { stmt ->
                    stmt.setString(1, userId)
                    stmt.executeUpdate()
                }
                val deleted = connection.prepareStatement(
                    "DELETE FROM users WHERE id = ?::uuid",
                ).use { stmt ->
                    stmt.setString(1, userId)
                    stmt.executeUpdate()
                }
                if (deleted != 1) error("No se pudo dar de baja la cuenta")
                connection.commit()
            } catch (failure: Exception) {
                connection.rollback()
                throw failure
            } finally {
                connection.autoCommit = previous
            }
        }
    }

    private fun textRows(connection: Connection, sql: String, userId: String): List<AccountTextRow> =
        connection.prepareStatement(sql).use { stmt ->
            stmt.setString(1, userId)
            stmt.executeQuery().use { rs ->
                val rows = ArrayList<AccountTextRow>()
                while (rs.next()) {
                    rows += AccountTextRow(
                        id = rs.getString(1),
                        body = rs.getString(2),
                        createdAt = rs.getString(3),
                    )
                }
                rows
            }
        }
}
