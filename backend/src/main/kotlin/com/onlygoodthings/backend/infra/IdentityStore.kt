package com.onlygoodthings.backend.infra

import com.onlygoodthings.backend.auth.VerifiedFirebaseUser
import com.onlygoodthings.shared.domain.UserProfile
import com.onlygoodthings.shared.domain.UserRole
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
            connection.prepareStatement(
                "UPDATE users SET email = COALESCE(?, email), phone_e164 = COALESCE(?, phone_e164), display_name = COALESCE(?, display_name) WHERE firebase_uid = ?",
            ).use { stmt ->
                stmt.setString(1, verified.email)
                stmt.setString(2, verified.phone)
                stmt.setString(3, verified.displayName ?: existing.displayName)
                stmt.setString(4, verified.firebaseUid)
                stmt.executeUpdate()
            }
            com.onlygoodthings.backend.social.claimHonorsMatchingContact(
                connection,
                existing.id,
                verified.email,
                verified.phone,
            )
            return@withConnection existing.copy(
                displayName = verified.displayName ?: existing.displayName,
            )
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
}
