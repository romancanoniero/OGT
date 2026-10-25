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
internal data class CrmUserMutation(
    val response: CrmUserMutationResponse,
    val removedPosts: List<String> = emptyList(),
    val removedComments: List<CrmRemovedUserComment> = emptyList(),
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
                              WHERE user_id = ANY(?::uuid[]))
                   OR EXISTS(SELECT 1 FROM crm_company_members
                             WHERE user_id = ANY(?::uuid[]) AND member_role = 'OWNER'
                               AND status = 'ACTIVE')"""
        ).use { stmt ->
            for (i in 1..2) stmt.setArray(i, conn.createArrayOf("uuid", ids.toTypedArray()))
            stmt.executeQuery().use { rs -> rs.next(); rs.getBoolean(1) }
        }
        require(!privileged) { "No se pueden modificar cuentas administrativas ni titulares de empresas" }

        val removedPosts = mutableListOf<String>()
        val removedComments = mutableListOf<CrmRemovedUserComment>()
        val recountPosts = mutableListOf<String>()
        if (action == "DELETE") {
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
            recountPosts = recountPosts,
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
)
internal fun crmContributionPath(path: String): Boolean = path in crmContributionPaths
