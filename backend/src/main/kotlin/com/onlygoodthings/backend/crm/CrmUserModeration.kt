package com.onlygoodthings.backend.crm

import com.onlygoodthings.backend.infra.Database
import kotlinx.serialization.Serializable
import java.security.MessageDigest
import java.sql.Connection
import java.util.UUID

/** Only the owner can permanently erase accounts; Trust & Safety may restrict posting. */
internal fun crmCanModerateUsers(role: String?, action: String): Boolean = when (action) {
    "BLOCK", "UNBLOCK" -> role == "OWNER" || role == "TRUST_SAFETY"
    "DELETE" -> role == "OWNER"
    else -> false
}

internal fun crmModerationIds(raw: Any?): List<String>? {
    val values = raw as? List<*> ?: return null
    if (values.isEmpty() || values.size > 25) return null
    val ids = values.map { (it as? String)?.let { value ->
        runCatching { UUID.fromString(value).toString() }.getOrNull()
    } ?: return null }
    return ids.takeIf { it.distinct().size == it.size }
}

internal fun crmModerationReason(raw: Any?): String? =
    (raw as? String)?.trim()?.takeIf { it.length in 10..500 }

internal fun crmFirebaseTombstoneHash(uid: String): String =
    MessageDigest.getInstance("SHA-256").digest(uid.toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it.toInt() and 255) }

@Serializable
internal data class CrmUserMutationResponse(
    val action: String,
    val ids: List<String>,
    val removedPostCount: Int = 0,
    val removedCommentCount: Int = 0,
)

internal data class CrmRemovedUserComment(val id: String, val postId: String, val authorId: String)
internal data class CrmRemovedUserAnecdote(val id: String, val postId: String)
internal data class CrmUserMutation(
    val response: CrmUserMutationResponse,
    val removedPosts: List<String> = emptyList(),
    val removedComments: List<CrmRemovedUserComment> = emptyList(),
    val removedAnecdotes: List<CrmRemovedUserAnecdote> = emptyList(),
    val recountPosts: List<String> = emptyList(),
)

/** A single transaction: any unsupported FK, protected account, or missing ID aborts the entire batch. */
internal fun crmModerateUsers(
    db: Database, actorId: String, ids: List<String>, action: String, reason: String,
): CrmUserMutation = db.withConnection { conn ->
    require(ids.isNotEmpty() && ids.size <= 25 && ids.distinct().size == ids.size)
    require(ids.all { runCatching { UUID.fromString(it) }.isSuccess })
    require(reason.length in 10..500)
    require(actorId !in ids) { "No podés moderar tu propia cuenta" }
    require(action in setOf("BLOCK", "UNBLOCK", "DELETE"))
    val oldAutoCommit = conn.autoCommit
    conn.autoCommit = false
    try {
        val rows = conn.prepareStatement(
            """SELECT id::text, firebase_uid, status::text
               FROM users WHERE id = ANY(?::uuid[]) ORDER BY id FOR UPDATE"""
        ).use { stmt ->
            stmt.setArray(1, conn.createArrayOf("uuid", ids.toTypedArray()))
            stmt.executeQuery().use { rs ->
                buildList {
                    while (rs.next()) add(Triple(rs.getString(1), rs.getString(2), rs.getString(3)))
                }
            }
        }
        require(rows.size == ids.size && rows.all { it.third != "DELETED" }) {
            "Uno o varios usuarios ya no existen"
        }
        val privileged = conn.prepareStatement(
            """SELECT EXISTS(SELECT 1 FROM crm_platform_staff
                              WHERE user_id = ANY(?::uuid[]) OR approved_by = ANY(?::uuid[]))
                   OR EXISTS(SELECT 1 FROM crm_company_members
                             WHERE user_id = ANY(?::uuid[]) AND member_role = 'OWNER'
                               AND status = 'ACTIVE')
                   OR EXISTS(SELECT 1 FROM company_admins
                             WHERE user_id = ANY(?::uuid[]) AND is_primary)"""
        ).use { stmt ->
            for (i in 1..4) stmt.setArray(i, conn.createArrayOf("uuid", ids.toTypedArray()))
            stmt.executeQuery().use { rs -> rs.next(); rs.getBoolean(1) }
        }
        require(!privileged) { "No se pueden modificar cuentas administrativas ni titulares de empresas" }

        val removedPosts = mutableListOf<String>()
        val removedComments = mutableListOf<CrmRemovedUserComment>()
        val removedAnecdotes = mutableListOf<CrmRemovedUserAnecdote>()
        val recountPosts = mutableListOf<String>()
        if (action == "DELETE") {
            // Queue media associated with the erased person's profile, posts or
            // animal notices. Files are purged only after DB commit and only if
            // nobody else still references them.
            val localNames = conn.prepareStatement(
                """SELECT source.url FROM (
                     SELECT photo_url AS url FROM users WHERE id = ANY(?::uuid[])
                     UNION ALL SELECT unnest(media_urls) AS url FROM social_posts
                       WHERE author_user_id = ANY(?::uuid[])
                     UNION ALL SELECT unnest(photo_urls) AS url FROM animal_listings
                       WHERE reporter_user_id = ANY(?::uuid[])
                     UNION ALL SELECT m.url FROM post_media m
                       JOIN social_posts p ON p.id = m.post_id
                       WHERE p.author_user_id = ANY(?::uuid[])
                     UNION ALL SELECT m.original_url FROM post_media m
                       JOIN social_posts p ON p.id = m.post_id
                       WHERE p.author_user_id = ANY(?::uuid[])
                     UNION ALL SELECT m.poster_url FROM post_media m
                       JOIN social_posts p ON p.id = m.post_id
                       WHERE p.author_user_id = ANY(?::uuid[])
                   ) source WHERE source.url IS NOT NULL"""
            ).use { stmt ->
                for (n in 1..6) stmt.setArray(n, conn.createArrayOf("uuid", ids.toTypedArray()))
                stmt.executeQuery().use { rs ->
                    buildSet {
                        while (rs.next()) crmLocalMediaName(rs.getString(1))?.let { add(it) }
                    }
                }
            }
            localNames.forEach { name ->
                conn.prepareStatement(
                    """INSERT INTO crm_erased_media(stored_name) VALUES (?)
                       ON CONFLICT (stored_name) DO UPDATE
                       SET processed_at = NULL, outcome = NULL"""
                ).use { stmt ->
                    stmt.setString(1, name)
                    stmt.executeUpdate()
                }
            }
            removedPosts.addAll(conn.queryIds(
                "SELECT id::text FROM social_posts WHERE author_user_id = ANY(?::uuid[])", ids,
            ))
            conn.prepareStatement(
                """SELECT id::text, post_id::text, author_user_id::text
                   FROM post_comments WHERE author_user_id = ANY(?::uuid[])"""
            ).use { stmt ->
                stmt.setArray(1, conn.createArrayOf("uuid", ids.toTypedArray()))
                stmt.executeQuery().use { rs ->
                    while (rs.next()) removedComments.add(CrmRemovedUserComment(
                        rs.getString(1), rs.getString(2), rs.getString(3),
                    ))
                }
            }
            conn.prepareStatement(
                """SELECT id::text, post_id::text FROM post_anecdotes
                   WHERE author_user_id = ANY(?::uuid[])"""
            ).use { stmt ->
                stmt.setArray(1, conn.createArrayOf("uuid", ids.toTypedArray()))
                stmt.executeQuery().use { rs ->
                    while (rs.next()) removedAnecdotes.add(CrmRemovedUserAnecdote(rs.getString(1), rs.getString(2)))
                }
            }
            recountPosts.addAll(conn.queryIds(
                """SELECT DISTINCT post_id::text FROM (
                     SELECT post_id FROM post_impacts WHERE user_id = ANY(?::uuid[])
                     UNION SELECT post_id FROM post_comments WHERE author_user_id = ANY(?::uuid[])
                     UNION SELECT post_id FROM post_anecdotes WHERE author_user_id = ANY(?::uuid[])
                   ) changed""", ids, count = 3,
            ))
            val recountAnecdotes = conn.queryIds(
                """SELECT DISTINCT anecdote_id::text FROM (
                     SELECT anecdote_id FROM anecdote_impacts WHERE user_id = ANY(?::uuid[])
                     UNION SELECT anecdote_id FROM anecdote_hearts WHERE user_id = ANY(?::uuid[])
                     UNION SELECT anecdote_id FROM post_comments WHERE author_user_id = ANY(?::uuid[])
                       AND anecdote_id IS NOT NULL
                   ) changed""", ids, count = 3,
            )
            // The user is barred from reappearing via an old Firebase token or a new session.
            rows.forEach { row ->
                conn.prepareStatement(
                    """INSERT INTO crm_erased_accounts (uid_hash, actor_user_id, reason)
                       VALUES (?, ?::uuid, ?) ON CONFLICT (uid_hash) DO NOTHING"""
                ).use { stmt ->
                    stmt.setString(1, crmFirebaseTombstoneHash(row.second))
                    stmt.setString(2, actorId)
                    stmt.setString(3, reason)
                    stmt.executeUpdate()
                }
            }
            // Preserve other people's replies to deleted comments on posts that remain.
            conn.executeForIds(
                """UPDATE post_comments SET parent_comment_id = NULL
                   WHERE parent_comment_id IN
                     (SELECT id FROM post_comments WHERE author_user_id = ANY(?::uuid[]))
                     AND NOT (author_user_id = ANY(?::uuid[]))""", ids, count = 2,
            )
            conn.executeForIds(
                """DELETE FROM parking_handoffs
                   WHERE owner_user_id = ANY(?::uuid[]) OR claimant_user_id = ANY(?::uuid[])""",
                ids, count = 2,
            )
            conn.executeForIds("DELETE FROM social_posts WHERE author_user_id = ANY(?::uuid[])", ids)
            val deleted = conn.executeForIds("DELETE FROM users WHERE id = ANY(?::uuid[])", ids)
            check(deleted == ids.size) { "La eliminación no fue completa" }
            // Cascades removed comments, impacts, anecdotes and their likes. Repair counters
            // for surviving posts and anecdotes (including posts written by other people).
            if (recountPosts.isNotEmpty()) {
                conn.prepareStatement(
                    """UPDATE social_posts p SET
                       impact_count = (SELECT count(*) FROM post_impacts i WHERE i.post_id = p.id),
                       comment_count = (SELECT count(*) FROM post_comments c
                          WHERE c.post_id = p.id AND c.anecdote_id IS NULL
                            AND c.moderation_status = 'VISIBLE'),
                       updated_at = now()
                       WHERE p.id = ANY(?::uuid[])"""
                ).use { stmt ->
                    stmt.setArray(1, conn.createArrayOf("uuid", recountPosts.toTypedArray()))
                    stmt.executeUpdate()
                }
            }
            if (recountAnecdotes.isNotEmpty()) {
                conn.prepareStatement(
                    """UPDATE post_anecdotes a SET
                       impact_count = (SELECT count(*) FROM anecdote_impacts i WHERE i.anecdote_id = a.id),
                       heart_count = (SELECT count(*) FROM anecdote_hearts h WHERE h.anecdote_id = a.id),
                       comment_count = (SELECT count(*) FROM post_comments c
                          WHERE c.anecdote_id = a.id AND c.moderation_status = 'VISIBLE'),
                       updated_at = now()
                       WHERE a.id = ANY(?::uuid[])"""
                ).use { stmt ->
                    stmt.setArray(1, conn.createArrayOf("uuid", recountAnecdotes.toTypedArray()))
                    stmt.executeUpdate()
                }
            }
        } else {
            conn.executeForIds(
                "UPDATE users SET publishing_blocked = ? , updated_at = now() WHERE id = ANY(?::uuid[])",
                ids, prefixBoolean = (action == "BLOCK"),
            )
        }
        rows.forEach { row ->
            conn.prepareStatement(
                """INSERT INTO crm_audit_events
                   (actor_user_id, action, target_type, target_id, reason, outcome)
                   VALUES (?::uuid, ?, 'user', ?, ?, 'SUCCESS')"""
            ).use { stmt ->
                stmt.setString(1, actorId)
                stmt.setString(2, "USER_" + action)
                stmt.setString(3, row.first)
                stmt.setString(4, reason)
                stmt.executeUpdate()
            }
        }
        conn.commit()
        CrmUserMutation(
            response = CrmUserMutationResponse(action, ids, removedPosts.size, removedComments.size),
            removedPosts = removedPosts, removedComments = removedComments,
            removedAnecdotes = removedAnecdotes, recountPosts = recountPosts,
        )
    } catch (error: Exception) {
        conn.rollback()
        throw error
    } finally {
        conn.autoCommit = oldAutoCommit
    }
}

private fun Connection.queryIds(sql: String, ids: List<String>, count: Int = 1): List<String> =
    prepareStatement(sql).use { stmt ->
        for (n in 1..count) stmt.setArray(n, createArrayOf("uuid", ids.toTypedArray()))
        stmt.executeQuery().use { rs -> buildList { while (rs.next()) add(rs.getString(1)) } }
    }

private fun Connection.executeForIds(
    sql: String, ids: List<String>, count: Int = 1, prefixBoolean: Boolean? = null,
): Int = prepareStatement(sql).use { stmt ->
    var next = 1
    if (prefixBoolean != null) stmt.setBoolean(next++, prefixBoolean)
    for (n in 1..count) stmt.setArray(next++, createArrayOf("uuid", ids.toTypedArray()))
    stmt.executeUpdate()
}

/** All public read routes remain available; the common authentication guard checks this
 * policy before any write route, regardless of whether the client is web, Android or iOS. */
internal val crmContributionPaths = setOf(
    "/api/v1/social/publish", "/api/v1/social/post/edit",
    "/api/v1/social/comment", "/api/v1/social/comment/edit",
    "/api/v1/social/anecdote", "/api/v1/social/anecdote/edit",
    "/api/v1/social/anecdote/repost", "/api/v1/social/impact",
    "/api/v1/social/anecdote/impact", "/api/v1/social/anecdote/heart",
    "/api/v1/social/follow", "/api/v1/social/promote", "/api/v1/ads/spend",
    "/api/v1/media/upload", "/api/v1/honor/issue", "/api/v1/honor/attach",
    "/api/v1/honor/claim", "/api/v1/animals/publish", "/api/v1/animals/update",
    "/api/v1/parking/publish", "/api/v1/timebank/need",
    "/api/v1/timebank/skill", "/api/v1/timebank/tag/create",
    "/api/v1/timebank/need/support", "/api/v1/timebank/need/invite",
    "/api/v1/timebank/start", "/api/v1/timebank/message/send",
    "/api/v1/volunteer/check-in", "/api/v1/timebank/match",
    "/api/v1/causes/progress", "/api/v1/causes/deliver",
    "/api/v1/users/profile/update",
    "/api/v1/users/profile/photo",
    "/api/v1/referrals/link", "/api/v1/referrals/claim",
    "/api/v1/parking/interest", "/api/v1/parking/claim", "/api/v1/parking/tick",
    "/api/v1/notifications/mention", "/api/v1/csr/finance-action",
    "/api/v1/csr/promo/issue",
)
internal fun crmContributionPath(path: String): Boolean = path in crmContributionPaths

/** Only locally stored media names are eligible. No remote content is modified. */
internal fun crmLocalMediaName(url: String?): String? {
    val candidate = url?.substringBefore('?')?.substringBefore('#') ?: return null
    val name = candidate.substringAfterLast("/media/u/", "")
    if (name.isEmpty() || '/' in name) return null
    return name.takeIf {
        it.matches(Regex("[0-9a-fA-F-]{36}\\.(jpg|jpeg|png|webp|mp4|webm)"))
    }
}

/** Best effort after commit. Journal rows survive failures for a later retry. */
internal fun crmPurgeErasedMedia(db: Database, media: com.onlygoodthings.backend.media.MediaStore) {
    val pending = db.withConnection { conn ->
        conn.createStatement().use { stmt ->
            stmt.executeQuery(
                "SELECT stored_name FROM crm_erased_media WHERE processed_at IS NULL ORDER BY created_at LIMIT 100"
            ).use { rs -> buildList { while (rs.next()) add(rs.getString(1)) } }
        }
    }
    pending.forEach { name ->
        try {
            val shared = db.withConnection { conn ->
                conn.prepareStatement(
                    """SELECT
                       EXISTS(SELECT 1 FROM users WHERE photo_url LIKE ?)
                       OR EXISTS(SELECT 1 FROM social_posts
                         WHERE array_to_string(media_urls, '|') LIKE ?)
                       OR EXISTS(SELECT 1 FROM post_media
                         WHERE url LIKE ? OR original_url LIKE ? OR poster_url LIKE ?)
                       OR EXISTS(SELECT 1 FROM animal_listings
                         WHERE array_to_string(photo_urls, '|') LIKE ?)"""
                ).use { stmt ->
                    for (n in 1..6) stmt.setString(n, "%/media/u/$name%")
                    stmt.executeQuery().use { rs -> rs.next(); rs.getBoolean(1) }
                }
            }
            val outcome = when {
                shared -> "SHARED"
                media.resolvePublic(name) == null -> "UNREFERENCED"
                media.deleteLocal("/media/u/$name") -> "DELETED"
                else -> null
            }
            if (outcome != null) db.withConnection { conn ->
                conn.prepareStatement(
                    """UPDATE crm_erased_media SET processed_at = now(), outcome = ?
                       WHERE stored_name = ? AND processed_at IS NULL"""
                ).use { stmt ->
                    stmt.setString(1, outcome)
                    stmt.setString(2, name)
                    stmt.executeUpdate()
                }
            }
        } catch (_: Exception) {
            // Keep the durable pending record; never pretend the file was purged.
        }
    }
}
