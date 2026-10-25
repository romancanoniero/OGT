package com.onlygoodthings.backend.crm

import kotlinx.serialization.Serializable
import java.sql.Connection
import java.time.Instant

/**
 * Perfil operativo: datos del perfil público y actividad pública.
 * Nunca contiene email, teléfono, Firebase UID, mensajes privados o ubicación.
 */
@Serializable
internal data class CrmUserProfile(
    val id: String,
    val displayName: String,
    val photoUrl: String? = null,
    val status: String,
    val role: String,
    val createdAt: String,
    val lastSeenAt: String? = null,
    val communityPoints: Int,
    val visiblePostCount: Long,
    val receivedReportCount: Long,
    val reportedContent: List<CrmUserReport> = emptyList(),
    val canReviewReports: Boolean = false,
    val conversationsRestricted: Boolean = true,
)

@Serializable
internal data class CrmUserReport(
    val id: String,
    val reason: String,
    val status: String,
    val createdAt: String,
    val postId: String? = null,
)

@Serializable
internal data class CrmUserPost(
    val id: String,
    val body: String,
    val topic: String,
    val createdAt: String,
    val imageUrl: String? = null,
)

@Serializable
internal data class CrmUserPostsPage(
    val posts: List<CrmUserPost>,
    val nextCursor: String? = null,
)

internal fun crmCanReviewUserReports(role: String?): Boolean =
    role in setOf("OWNER", "TRUST_SAFETY")

internal fun crmReadUserProfile(
    conn: Connection, userId: String, canReviewReports: Boolean,
): CrmUserProfile? {
    val profile = conn.prepareStatement(
        """
        SELECT u.id::text, u.display_name, u.photo_url, u.status::text, u.role::text,
               u.created_at, u.last_seen_at, u.community_points,
               (SELECT COUNT(*) FROM social_posts p
                WHERE p.author_user_id = u.id AND p.moderation_status = 'VISIBLE') AS post_count,
               (SELECT COUNT(DISTINCT r.id)
                FROM content_reports r
                LEFT JOIN social_posts p ON p.id = r.post_id
                LEFT JOIN post_comments c ON c.id = r.comment_id
                WHERE p.author_user_id = u.id OR c.author_user_id = u.id) AS reports_count
        FROM users u WHERE u.id = ?::uuid
        """.trimIndent(),
    ).use { stmt ->
        stmt.setString(1, userId)
        stmt.executeQuery().use { rs ->
            if (!rs.next()) null else CrmUserProfile(
                id = rs.getString(1),
                displayName = rs.getString(2),
                photoUrl = rs.getString(3)?.trim()?.takeIf {
                    it.startsWith("https://") || it.startsWith("http://") || it.startsWith("/media/")
                },
                status = rs.getString(4),
                role = rs.getString(5),
                createdAt = rs.getTimestamp(6).toInstant().toString(),
                lastSeenAt = rs.getTimestamp(7)?.toInstant()?.toString(),
                communityPoints = rs.getInt(8),
                visiblePostCount = rs.getLong(9),
                receivedReportCount = if (canReviewReports) rs.getLong(10) else 0,
                canReviewReports = canReviewReports,
            )
        }
    } ?: return null
    if (!canReviewReports) return profile
    val reports = conn.prepareStatement(
        """
        SELECT r.id::text, r.reason, r.status::text, r.created_at, r.post_id::text
        FROM content_reports r
        LEFT JOIN social_posts p ON p.id = r.post_id
        LEFT JOIN post_comments c ON c.id = r.comment_id
        WHERE p.author_user_id = ?::uuid OR c.author_user_id = ?::uuid
        ORDER BY r.created_at DESC, r.id DESC
        LIMIT 10
        """.trimIndent(),
    ).use { stmt ->
        stmt.setString(1, userId)
        stmt.setString(2, userId)
        stmt.executeQuery().use { rs ->
            buildList {
                while (rs.next()) add(CrmUserReport(
                    id = rs.getString(1),
                    reason = rs.getString(2),
                    status = rs.getString(3),
                    createdAt = rs.getTimestamp(4).toInstant().toString(),
                    postId = rs.getString(5),
                ))
            }
        }
    }
    return profile.copy(reportedContent = reports)
}

/**
 * Las publicaciones son las de la misma tabla que el feed de web / iOS / Android.
 * Cursor keyset estricto: created_at e id. La paginación nunca devuelve mensajes.
 */
internal fun crmReadUserPosts(conn: Connection, userId: String, cursor: String?): CrmUserPostsPage {
    val key = cursor?.let { crmParsePostPageCursor(it) }
    val sql = buildString {
        append("""
            SELECT p.id::text, p.body, COALESCE(p.topic, '') AS topic, p.created_at,
                   (SELECT m.url FROM post_media m
                    WHERE m.post_id = p.id AND m.kind = 'IMAGE'
                    ORDER BY m.sort_order LIMIT 1) AS image_url
            FROM social_posts p
            WHERE p.author_user_id = ?::uuid AND p.moderation_status = 'VISIBLE'
        """.trimIndent())
        if (key != null) append(" AND (p.created_at, p.id) < (?::timestamptz, ?::uuid)")
        append(" ORDER BY p.created_at DESC, p.id DESC LIMIT 21")
    }
    val results = conn.prepareStatement(sql).use { stmt ->
        stmt.setString(1, userId)
        if (key != null) {
            stmt.setTimestamp(2, java.sql.Timestamp.from(Instant.parse(key.createdAt)))
            stmt.setString(3, key.id)
        }
        stmt.executeQuery().use { rs ->
            buildList {
                while (rs.next()) add(CrmUserPost(
                    id = rs.getString(1),
                    body = rs.getString(2) ?: "",
                    topic = rs.getString(3),
                    createdAt = rs.getTimestamp(4).toInstant().toString(),
                    imageUrl = crmPostImageUrl(rs.getString(5)),
                ))
            }
        }
    }
    val next = if (results.size > 20) {
        val last = results[19]
        crmEncodePostPageCursor(CrmPostPageCursor("createdAt", "desc", "", last.createdAt, last.id))
    } else null
    return CrmUserPostsPage(results.take(20), next)
}

internal fun crmParseUserPostsCursor(raw: String?): String? {
    if (raw.isNullOrBlank()) return null
    val parsed = parseCrmPostPageCursor(raw) ?: return null
    return raw.takeIf { parsed.sortBy == "createdAt" && parsed.sortDirection == "desc" && parsed.sortValue == "" }
}
