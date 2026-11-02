package com.onlygoodthings.backend.infra

import com.onlygoodthings.backend.auth.AccountExport
import com.onlygoodthings.backend.business.ownCoupons
import com.onlygoodthings.backend.auth.AccountTextRow
import com.onlygoodthings.backend.auth.VerifiedFirebaseUser
import com.onlygoodthings.shared.domain.UserProfile
import com.onlygoodthings.shared.domain.UserRole
import java.sql.Connection
import java.util.UUID

class IdentityStore(private val db: Database) {

    fun upsertFromFirebase(verified: VerifiedFirebaseUser): UserProfile = db.withConnection { connection ->
        val erased = connection.prepareStatement(
            "SELECT EXISTS(SELECT 1 FROM crm_erased_accounts WHERE uid_hash = ?)",
        ).use { stmt ->
            stmt.setString(1, com.onlygoodthings.backend.crm.crmFirebaseTombstoneHash(verified.firebaseUid))
            stmt.executeQuery().use { rs -> rs.next(); rs.getBoolean(1) }
        }
        check(!erased) { "Cuenta eliminada" }

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

    /** New tokens never restore posting privileges: the database is authoritative. */
    fun canPublish(userId: String): Boolean = db.withConnection { connection ->
        connection.prepareStatement(
            "SELECT status = 'ACTIVE' AND NOT publishing_blocked FROM users WHERE id = ?::uuid",
        ).use { stmt ->
            stmt.setString(1, userId)
            stmt.executeQuery().use { rs -> rs.next() && rs.getBoolean(1) }
        }
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
        val blocks=connection.prepareStatement("SELECT u.id::text,u.display_name,(extract(epoch FROM b.created_at)*1000)::bigint FROM user_blocks b JOIN users u ON u.id=b.blocked_id WHERE b.blocker_id=?::uuid ORDER BY b.created_at").use { q ->
            q.setString(1,userId);q.executeQuery().use { r ->buildList { while(r.next())add(com.onlygoodthings.shared.domain.BlockedUser(r.getString(1),r.getString(2),r.getLong(3))) } }
        }
        profile.copy(
            preferences=com.onlygoodthings.backend.social.readAccountSettings(connection,userId),
            records=mapOf(
                "profile" to exportJsonRows(connection,"SELECT jsonb_build_object('firebaseUid',firebase_uid,'photoUrl',photo_url,'role',role,'createdAt',created_at,'updatedAt',updated_at,'lastSeenAt',last_seen_at,'latitude',ST_Y(home_location),'longitude',ST_X(home_location),'language',metadata->'settings'->'language','barrio',metadata->'settings'->'barrio') FROM users WHERE id=?::uuid",userId),
                "anecdotes" to exportJsonRows(connection,"SELECT to_jsonb(a) FROM post_anecdotes a WHERE author_user_id=?::uuid ORDER BY created_at",userId),
                "uploads" to exportJsonRows(connection,"SELECT to_jsonb(u) FROM uploaded_media_owners u WHERE uploader_user_id=?::uuid ORDER BY created_at",userId),
                "reactions" to exportJsonRows(connection,"SELECT to_jsonb(i) FROM post_impacts i WHERE user_id=?::uuid ORDER BY created_at",userId),
                "volunteerSignups" to exportJsonRows(connection,"SELECT to_jsonb(s)-'check_in_location' || jsonb_build_object('latitude',ST_Y(check_in_location),'longitude',ST_X(check_in_location)) FROM volunteer_signups s WHERE user_id=?::uuid ORDER BY created_at",userId),
                "submittedReports" to exportJsonRows(connection,"SELECT jsonb_build_object('id',id,'postId',post_id,'commentId',comment_id,'reason',reason,'details',details,'createdAt',created_at) FROM content_reports WHERE reporter_id=?::uuid ORDER BY created_at",userId),
                "authoredPostsAndStories" to exportJsonRows(connection,"SELECT to_jsonb(p)-'location' || jsonb_build_object('latitude',ST_Y(location),'longitude',ST_X(location)) FROM social_posts p WHERE author_user_id=?::uuid ORDER BY created_at",userId),
                "media" to exportJsonRows(connection,"SELECT to_jsonb(m) FROM post_media m JOIN social_posts p ON p.id=m.post_id WHERE p.author_user_id=?::uuid ORDER BY m.post_id,m.sort_order",userId),
                "animalListings" to exportJsonRows(connection,"SELECT to_jsonb(a)-'location' || jsonb_build_object('latitude',ST_Y(location),'longitude',ST_X(location)) FROM animal_listings a WHERE reporter_user_id=?::uuid ORDER BY created_at",userId),
                "skills" to exportJsonRows(connection,"SELECT jsonb_build_object('tag',t.slug,'label',t.label,'offered',s.offered,'requested',s.requested) FROM user_skills s JOIN skill_tags t ON t.id=s.tag_id WHERE s.user_id=?::uuid",userId),
                "following" to exportJsonRows(connection,"SELECT jsonb_build_object('userId',followed_id,'createdAt',created_at) FROM follows WHERE follower_id=?::uuid",userId),
                "feedActivity" to exportJsonRows(connection,"SELECT to_jsonb(e) FROM feed_events e WHERE viewer_id=?::uuid ORDER BY created_at",userId),
            ),
            blockedUsers=blocks,
            coupons=com.onlygoodthings.backend.business.CsrRepository(db).ownCoupons(connection,userId),
            companyTeam=com.onlygoodthings.backend.business.CsrTeam(com.onlygoodthings.backend.business.CsrRepository(db)).export(connection,userId),
            companyApplications=com.onlygoodthings.backend.business.CsrRegistrations(db).own(connection,userId),
            posts = textRows(connection, "SELECT id::text, body, created_at::text FROM social_posts WHERE author_user_id = ?::uuid ORDER BY created_at", userId),
            comments = textRows(connection, "SELECT id::text, body, created_at::text FROM post_comments WHERE author_user_id = ?::uuid ORDER BY created_at", userId),
            messagesSent = textRows(connection, "SELECT id::text, body, created_at::text FROM timebank_messages WHERE sender_id = ?::uuid ORDER BY created_at", userId),
        )
    }

    private fun exportJsonRows(connection: java.sql.Connection, sql: String, userId: String): List<kotlinx.serialization.json.JsonObject> =
        connection.prepareStatement(sql).use { q ->
            q.setString(1,userId)
            q.executeQuery().use { r -> buildList {
                while(r.next())add(kotlinx.serialization.json.Json.parseToJsonElement(r.getString(1)) as kotlinx.serialization.json.JsonObject)
            } }
        }

    /**
     * Baja de la propia cuenta. Borra el contenido cuyo autor es el usuario
     * (los posts no pueden quedar con autor nulo) y las entregas de estacionamiento
     * que no tienen ON DELETE. El resto cae por cascada o queda anonimizado.
     */
    fun eraseAccount(userId: String):List<String> = com.onlygoodthings.backend.crm.crmModerateUsers(
        db,userId,listOf(userId),"DELETE","Solicitud de baja del titular",selfDelete=true,
    ).removedPosts

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
