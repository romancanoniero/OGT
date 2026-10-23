package com.onlygoodthings.backend.crm

import com.onlygoodthings.backend.auth.AuthPrincipal
import com.onlygoodthings.backend.auth.FirebaseTokenVerifier
import com.onlygoodthings.backend.http.JsonBody
import com.onlygoodthings.backend.http.optString
import com.onlygoodthings.backend.http.reqString
import com.onlygoodthings.backend.infra.Database
import com.onlygoodthings.backend.realtime.RealtimeHub
import com.onlygoodthings.shared.domain.ApiResponse
import com.onlygoodthings.shared.domain.SocialLiveCounters
import com.onlygoodthings.shared.protocol.WsCodec
import com.onlygoodthings.shared.realtime.OgtDbPaths
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.call
import io.ktor.server.auth.principal
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import kotlinx.serialization.Serializable
import java.sql.Connection
import java.sql.ResultSet
import java.sql.Timestamp
import java.time.Instant
import java.util.UUID

@Serializable
data class CrmCompanyGrant(val companyId: String, val role: String)

@Serializable
data class CrmAccessResponse(
    val platformRole: String?,
    val companies: List<CrmCompanyGrant>,
    val stepUpValidUntilEpochSeconds: Long,
)

@Serializable
data class CrmPlatformSummary(
    val users: Long, val companies: Long, val pendingCompanies: Long,
    val openReports: Long, val visiblePosts: Long,
)

@Serializable
data class CrmVisiblePost(
    val id: String,
    val authorName: String,
    val body: String,
    val createdAt: String,
    val status: String,
    /** Primera foto IMAGE de `post_media.url`. Vacío si el post no tiene imagen. */
    val imageUrl: String? = null,
)

@Serializable
data class CrmVisiblePostPage(
    val posts: List<CrmVisiblePost>,
    val nextCursor: String? = null,
)

@Serializable
data class CrmPostComment(
    val id: String,
    val authorName: String,
    val body: String,
    val createdAt: String,
)

@Serializable
data class CrmPostReport(
    val id: String,
    val reason: String,
    val status: String,
    val createdAt: String,
)

@Serializable
data class CrmPostDetail(
    val post: CrmVisiblePost,
    val impactCount: Long,
    val comments: List<CrmPostComment>,
    val reports: List<CrmPostReport>,
)

@Serializable
data class CrmPostRemoved(val removed: Boolean = true, val ids: List<String> = emptyList())

/** Nodo que ya escucha el feed: `ogt/social/posts/{id}`. */
internal fun crmRemovedPostLive(postId: String): SocialLiveCounters = SocialLiveCounters(
    id = postId,
    commentCount = 0,
    impactCount = 0,
    moderationStatus = "REMOVED",
)

internal fun crmRemovedPostPath(postId: String): String = OgtDbPaths.socialPost(postId)

@Serializable
data class CrmCompanySummary(
    val companyId: String, val legalName: String, val tradeName: String?,
    val verificationStatus: String, val balanceCents: Long, val impactScore: Double,
    val campaigns: Long, val activeCampaigns: Long, val issuedCoupons: Long,
)

private data class CrmContext(
    val userId: String,
    val platformRole: String?,
    val companies: List<CrmCompanyGrant>,
    val authTimeSeconds: Long,
)

/**
 * One-time OWNER bootstrap. There is intentionally no public "first visitor wins"
 * endpoint. The private server .env must pin the expected verified Firebase email.
 * PostgreSQL advisory lock serializes concurrent requests; any existing staff row
 * permanently disables automatic owner assignment.
 */
internal fun crmBootstrapEmailMatches(
    verifiedEmail: String?,
    emailVerified: Boolean,
    configuredEmail: String?,
): Boolean = emailVerified && !configuredEmail.isNullOrBlank() &&
    !verifiedEmail.isNullOrBlank() &&
    verifiedEmail.trim().equals(configuredEmail.trim(), ignoreCase = true)

private fun bootstrapFirstControlOwner(
    db: Database,
    principal: AuthPrincipal,
    verified: com.onlygoodthings.backend.auth.VerifiedFirebaseUser,
) {
    val expected = System.getenv("OGT_CONTROL_BOOTSTRAP_EMAIL")
    if (!crmBootstrapEmailMatches(verified.email, verified.emailVerified, expected)) return
    db.withConnection { conn ->
        val oldAutoCommit = conn.autoCommit
        conn.autoCommit = false
        try {
            conn.createStatement().use { statement ->
                statement.execute("SELECT pg_advisory_xact_lock(26052, 12017)")
            }
            val anyStaff = conn.createStatement().use { statement ->
                statement.executeQuery("SELECT EXISTS (SELECT 1 FROM crm_platform_staff)").use { rs ->
                    rs.next()
                    rs.getBoolean(1)
                }
            }
            if (!anyStaff) {
                val inserted = conn.prepareStatement(
                    """INSERT INTO crm_platform_staff
                       (user_id, staff_role, enabled, approved_by, approved_at)
                       SELECT u.id, 'OWNER', TRUE, u.id, now()
                       FROM users u
                       WHERE u.id = ?::uuid AND u.firebase_uid = ?
                         AND u.status = 'ACTIVE' AND lower(u.email) = lower(?)
                       ON CONFLICT (user_id) DO NOTHING"""
                ).use { stmt ->
                    stmt.setString(1, principal.userId)
                    stmt.setString(2, verified.firebaseUid)
                    stmt.setString(3, expected!!.trim())
                    stmt.executeUpdate()
                }
                if (inserted == 1) {
                    conn.prepareStatement(
                        """INSERT INTO crm_audit_events
                           (actor_user_id, action, target_type, target_id, reason, outcome)
                           VALUES (?::uuid, 'CONTROL_OWNER_BOOTSTRAP',
                                   'platform_staff', ?, 'authorized_first_owner', 'SUCCESS')"""
                    ).use { stmt ->
                        stmt.setString(1, principal.userId)
                        stmt.setString(2, principal.userId)
                        stmt.executeUpdate()
                    }
                }
            }
            conn.commit()
        } catch (error: Exception) {
            conn.rollback()
            throw error
        } finally {
            conn.autoCommit = oldAutoCommit
        }
    }
}

/** Every CRM request must independently pass MFA and current DB permissions. */
private suspend fun crmContext(
    call: ApplicationCall, db: Database, verifier: FirebaseTokenVerifier,
): CrmContext? {
    val principal = call.principal<AuthPrincipal>()
    if (principal == null) {
        call.respond(HttpStatusCode.Unauthorized, ApiResponse.fail<Unit>("Autenticación requerida", "UNAUTHENTICATED"))
        return null
    }
    val bearer = call.request.headers["Authorization"]?.let {
        if (it.startsWith("Bearer ", ignoreCase = true)) it.substring(7).trim() else null
    }?.takeIf { it.isNotBlank() }
    val verified = bearer?.let { runCatching { verifier.verify(it) }.getOrNull() }
    if (verified == null || verified.firebaseUid != principal.firebaseUid) {
        call.respond(HttpStatusCode.Unauthorized, ApiResponse.fail<Unit>("Token inválido", "UNAUTHENTICATED"))
        return null
    }
    val now = Instant.now().epochSecond
    if (!crmStepUpAllowed(verified.emailVerified, verified.secondFactor, verified.authTimeEpochSeconds, now)) {
        call.respond(
            HttpStatusCode.Forbidden,
            ApiResponse.fail<Unit>("Se requiere correo verificado, segundo factor y un nuevo ingreso", "STEP_UP_REQUIRED")
        )
        return null
    }
    bootstrapFirstControlOwner(db, principal, verified)
    val grants = db.withConnection { conn ->
        val staff = conn.prepareStatement(
            """SELECT s.staff_role FROM crm_platform_staff s
               JOIN users u ON u.id = s.user_id
               WHERE s.user_id = ?::uuid AND s.enabled AND s.approved_by IS NOT NULL
                 AND s.approved_at IS NOT NULL AND u.status = 'ACTIVE'"""
        ).use { stmt ->
            stmt.setString(1, principal.userId)
            stmt.executeQuery().use { rs -> if (rs.next()) rs.getString(1) else null }
        }
        val companies = conn.prepareStatement(
            """SELECT m.company_id::text, m.member_role FROM crm_company_members m
               JOIN users u ON u.id = m.user_id
               JOIN companies c ON c.id = m.company_id
               WHERE m.user_id = ?::uuid AND u.status = 'ACTIVE'
                 AND m.status = 'ACTIVE' AND m.accepted_at IS NOT NULL
               ORDER BY m.company_id LIMIT 100"""
        ).use { stmt ->
            stmt.setString(1, principal.userId)
            stmt.executeQuery().use { rs ->
                buildList {
                    while (rs.next()) add(CrmCompanyGrant(rs.getString(1), rs.getString(2)))
                }
            }
        }
        staff to companies
    }
    if (grants.first == null && grants.second.isEmpty()) {
        call.respond(HttpStatusCode.Forbidden, ApiResponse.fail<Unit>("Sin permisos de CRM", "FORBIDDEN"))
        return null
    }
    return CrmContext(principal.userId, grants.first, grants.second, verified.authTimeEpochSeconds!!)
}

fun Route.crmRoutes(db: Database, verifier: FirebaseTokenVerifier, hub: RealtimeHub) {
    post("/api/v1/crm/access") {
        val auth = crmContext(call, db, verifier) ?: return@post
        call.respond(ApiResponse.ok(CrmAccessResponse(
            platformRole = auth.platformRole,
            companies = auth.companies,
            stepUpValidUntilEpochSeconds = auth.authTimeSeconds + CRM_STEP_UP_WINDOW_SECONDS,
        )))
    }
    post("/api/v1/crm/platform/summary") {
        val auth = crmContext(call, db, verifier) ?: return@post
        if (auth.platformRole == null) {
            return@post call.respond(HttpStatusCode.Forbidden,
                ApiResponse.fail<Unit>("Solo personal autorizado", "FORBIDDEN"))
        }
        val summary = db.withConnection { conn ->
            val sql = """
                SELECT
                  (SELECT count(*) FROM users WHERE status = 'ACTIVE'),
                  (SELECT count(*) FROM companies),
                  (SELECT count(*) FROM companies WHERE verification_status = 'PENDING_REVIEW'),
                  (SELECT count(*) FROM content_reports WHERE status = 'PENDING_REVIEW'),
                  (SELECT count(*) FROM social_posts WHERE moderation_status = 'VISIBLE')
            """.trimIndent()
            conn.prepareStatement(sql).use { stmt ->
                stmt.executeQuery().use { rs ->
                    check(rs.next())
                    CrmPlatformSummary(rs.getLong(1), rs.getLong(2), rs.getLong(3),
                        rs.getLong(4), rs.getLong(5))
                }
            }
        }
        call.respond(ApiResponse.ok(summary))
    }
    post("/api/v1/crm/company/summary") {
        val auth = crmContext(call, db, verifier) ?: return@post
        val companyId = JsonBody.receiveMap(call).reqString("companyId")
        val validCompanyId = runCatching { UUID.fromString(companyId).toString() }.getOrNull()
            ?: return@post call.respond(HttpStatusCode.BadRequest,
                ApiResponse.fail<Unit>("Empresa inválida", "BAD_REQUEST"))
        if (auth.companies.none { it.companyId == validCompanyId }) {
            return@post call.respond(HttpStatusCode.Forbidden,
                ApiResponse.fail<Unit>("Empresa no autorizada", "FORBIDDEN"))
        }
        val summary = db.withConnection { conn ->
            conn.prepareStatement(
                """SELECT c.id::text, c.legal_name, c.trade_name,
                    c.verification_status::text, c.campaign_balance_cents,
                    c.impact_score,
                    (SELECT count(*) FROM campaigns WHERE company_id = c.id),
                    (SELECT count(*) FROM campaigns WHERE company_id = c.id AND status = 'ACTIVE'),
                    (SELECT count(*) FROM promo_codes WHERE company_id = c.id)
                   FROM companies c WHERE c.id = ?::uuid"""
            ).use { stmt ->
                stmt.setString(1, validCompanyId)
                stmt.executeQuery().use { rs ->
                    if (!rs.next()) null else CrmCompanySummary(
                        companyId = rs.getString(1), legalName = rs.getString(2),
                        tradeName = rs.getString(3), verificationStatus = rs.getString(4),
                        balanceCents = rs.getLong(5), impactScore = rs.getDouble(6),
                        campaigns = rs.getLong(7), activeCampaigns = rs.getLong(8),
                        issuedCoupons = rs.getLong(9),
                    )
                }
            }
        } ?: return@post call.respond(HttpStatusCode.NotFound,
            ApiResponse.fail<Unit>("Empresa no encontrada", "NOT_FOUND"))
        call.respond(ApiResponse.ok(summary))
    }
    post("/api/v1/crm/platform/posts/search") {
        val auth = crmContext(call, db, verifier) ?: return@post
        if (!crmCanManagePosts(auth.platformRole)) {
            return@post call.respond(HttpStatusCode.Forbidden,
                ApiResponse.fail<Unit>("Sin permiso para administrar publicaciones", "FORBIDDEN"))
        }
        val data = JsonBody.receiveMap(call)
        val query = data.optString("query")?.trim().orEmpty()
        if (query.length > 120) {
            return@post call.respond(HttpStatusCode.BadRequest,
                ApiResponse.fail<Unit>("La búsqueda es demasiado larga", "BAD_REQUEST"))
        }
        val cursor = data.optString("cursor")?.trim()?.ifBlank { null }?.let { raw ->
            parseCrmPostCursor(raw) ?: return@post call.respond(HttpStatusCode.BadRequest,
                ApiResponse.fail<Unit>("Cursor inválido", "BAD_REQUEST"))
        }
        val page = db.withConnection { conn -> searchVisiblePosts(conn, query, cursor) }
        call.respond(ApiResponse.ok(page))
    }
    post("/api/v1/crm/platform/posts/detail") {
        val auth = crmContext(call, db, verifier) ?: return@post
        if (!crmCanManagePosts(auth.platformRole)) {
            return@post call.respond(HttpStatusCode.Forbidden,
                ApiResponse.fail<Unit>("Sin permiso para administrar publicaciones", "FORBIDDEN"))
        }
        val data = JsonBody.receiveMap(call)
        val postId = runCatching { UUID.fromString(data.reqString("postId")).toString() }.getOrNull()
            ?: return@post call.respond(HttpStatusCode.BadRequest,
                ApiResponse.fail<Unit>("Publicación inválida", "BAD_REQUEST"))
        val detail = db.withConnection { conn -> loadVisiblePostDetail(conn, postId) }
            ?: return@post call.respond(HttpStatusCode.NotFound,
                ApiResponse.fail<Unit>("Esa publicación ya no está visible", "NOT_FOUND"))
        call.respond(ApiResponse.ok(detail))
    }
    post("/api/v1/crm/platform/posts/remove") {
        val auth = crmContext(call, db, verifier) ?: return@post
        if (!crmCanManagePosts(auth.platformRole)) {
            return@post call.respond(HttpStatusCode.Forbidden,
                ApiResponse.fail<Unit>("Sin permiso para administrar publicaciones", "FORBIDDEN"))
        }
        val data = JsonBody.receiveMap(call)
        val postIds = crmPostIds(data)
        if (postIds.isEmpty() || postIds.size > 50) {
            return@post call.respond(HttpStatusCode.BadRequest,
                ApiResponse.fail<Unit>("Publicación inválida", "BAD_REQUEST"))
        }
        val reason = data.optString("reason")?.trim().orEmpty()
        if (reason.length !in 3..500) {
            return@post call.respond(HttpStatusCode.BadRequest,
                ApiResponse.fail<Unit>("El motivo tiene que explicar la quita", "BAD_REQUEST"))
        }
        val removed = db.withConnection { conn -> removeVisiblePosts(conn, auth.userId, postIds, reason) }
        if (removed.isEmpty()) {
            return@post call.respond(HttpStatusCode.NotFound,
                ApiResponse.fail<Unit>("Esa publicación ya no está visible", "NOT_FOUND"))
        }
        removed.forEach { postId ->
            hub.projectSocialPost(
                postId,
                WsCodec.json.encodeToString(SocialLiveCounters.serializer(), crmRemovedPostLive(postId)),
            )
        }
        call.respond(ApiResponse.ok(CrmPostRemoved(ids = removed)))
    }
}

internal fun crmPostIds(data: Map<String, Any?>): List<String> {
    val raw = mutableListOf<String>()
    data.optString("postId")?.let { raw.add(it) }
    val many = data["postIds"]
    if (many is List<*>) many.forEach { if (it is String) raw.add(it) }
    return raw.mapNotNull { value ->
        runCatching { UUID.fromString(value.trim()).toString() }.getOrNull()
    }.distinct()
}

/** Doce horas de ingreso reciente. El segundo factor sigue siendo obligatorio. */
internal const val CRM_STEP_UP_WINDOW_SECONDS = 12L * 60 * 60

/** Un JWT renovado no es un ingreso nuevo. */
internal fun crmStepUpAllowed(
    verifiedEmail: Boolean,
    secondFactor: Boolean,
    authTimeSeconds: Long?,
    nowSeconds: Long,
): Boolean = verifiedEmail && secondFactor &&
    authTimeSeconds != null &&
    authTimeSeconds in (nowSeconds - CRM_STEP_UP_WINDOW_SECONDS)..(nowSeconds + 60)

internal val crmPostManagers = setOf("OWNER", "OPERATIONS", "TRUST_SAFETY", "SUPPORT")

internal fun crmCanManagePosts(role: String?): Boolean = role != null && role in crmPostManagers

/** Patrón ILIKE acotado. El texto del operador no se concatena en el SQL. */
internal fun crmLikeContains(raw: String): String {
    val escaped = raw.trim()
        .replace("\\", "\\\\")
        .replace("%", "\\%")
        .replace("_", "\\_")
    return "%$escaped%"
}

internal fun crmPostCursor(createdAtEpochMilli: Long, id: String): String = "$createdAtEpochMilli|$id"

internal fun parseCrmPostCursor(raw: String): Pair<Long, String>? {
    val divider = raw.indexOf('|')
    if (divider <= 0 || divider == raw.lastIndex) return null
    val millis = raw.substring(0, divider).toLongOrNull() ?: return null
    if (millis < 0) return null
    val id = runCatching { UUID.fromString(raw.substring(divider + 1)).toString() }.getOrNull() ?: return null
    return millis to id
}

internal const val CRM_VISIBLE_POST_SEARCH = """
SELECT p.id::text, p.body, p.moderation_status::text, p.created_at,
       COALESCE(NULLIF(u.display_name, ''), NULLIF(c.trade_name, ''), NULLIF(c.legal_name, ''), 'Sin nombre') AS author_name,
       (SELECT m.url FROM post_media m
         WHERE m.post_id = p.id AND m.kind = 'IMAGE'
         ORDER BY m.sort_order LIMIT 1) AS image_url
FROM social_posts p
LEFT JOIN users u ON u.id = p.author_user_id
LEFT JOIN companies c ON c.id = p.author_company_id
WHERE p.moderation_status = 'VISIBLE'
"""

/** La URL que ya guarda `post_media.url`. Sin esquema http no es una foto publicable. */
internal fun crmPostImageUrl(raw: String?): String? {
    val url = raw?.trim().orEmpty()
    if (url.length !in 8..2000) return null
    val lower = url.lowercase()
    if (!lower.startsWith("https://") && !lower.startsWith("http://")) return null
    return url
}

private fun ResultSet.toCrmVisiblePost(): CrmVisiblePost = CrmVisiblePost(
    id = getString("id"),
    authorName = getString("author_name"),
    body = getString("body"),
    createdAt = getTimestamp("created_at").toInstant().toString(),
    status = getString("moderation_status"),
    imageUrl = crmPostImageUrl(getString("image_url")),
)

internal const val CRM_POST_REMOVE = """
UPDATE social_posts
SET moderation_status = 'REMOVED', updated_at = now()
WHERE id = ?::uuid AND moderation_status = 'VISIBLE'
"""

private const val CRM_POST_PAGE = 20

private fun searchVisiblePosts(
    conn: Connection,
    query: String,
    cursor: Pair<Long, String>?,
): CrmVisiblePostPage {
    val sql = buildString {
        append(CRM_VISIBLE_POST_SEARCH.trim())
        if (query.isNotEmpty()) {
            append(
                """
                 AND (
                   p.body ILIKE ? ESCAPE '\'
                   OR COALESCE(u.display_name, '') ILIKE ? ESCAPE '\'
                   OR COALESCE(u.email::text, '') ILIKE ? ESCAPE '\'
                   OR COALESCE(c.trade_name, '') ILIKE ? ESCAPE '\'
                   OR COALESCE(c.legal_name, '') ILIKE ? ESCAPE '\'
                   OR p.id::text = ?
                 )
                """.trimIndent()
            )
        }
        if (cursor != null) append(" AND (p.created_at, p.id) < (?::timestamptz, ?::uuid)")
        append(" ORDER BY p.created_at DESC, p.id DESC LIMIT ${CRM_POST_PAGE + 1}")
    }
    val rows = conn.prepareStatement(sql).use { stmt ->
        var index = 1
        if (query.isNotEmpty()) {
            val pattern = crmLikeContains(query)
            repeat(5) { stmt.setString(index++, pattern) }
            stmt.setString(index++, query)
        }
        if (cursor != null) {
            stmt.setTimestamp(index++, Timestamp.from(Instant.ofEpochMilli(cursor.first)))
            stmt.setString(index, cursor.second)
        }
        stmt.executeQuery().use { rs ->
            buildList {
                while (rs.next()) {
                    val status = rs.getString("moderation_status")
                    if (status != "VISIBLE") continue
                    add(rs.toCrmVisiblePost())
                }
            }
        }
    }
    val page = rows.take(CRM_POST_PAGE)
    val next = if (rows.size > CRM_POST_PAGE && page.isNotEmpty()) {
        val last = page.last()
        crmPostCursor(Instant.parse(last.createdAt).toEpochMilli(), last.id)
    } else null
    return CrmVisiblePostPage(posts = page, nextCursor = next)
}

private fun loadVisiblePostDetail(conn: Connection, postId: String): CrmPostDetail? {
    val post = conn.prepareStatement(
        """
        ${CRM_VISIBLE_POST_SEARCH.trim()}
        AND p.id = ?::uuid
        """.trimIndent()
    ).use { stmt ->
        stmt.setString(1, postId)
        stmt.executeQuery().use { rs ->
            if (!rs.next() || rs.getString("moderation_status") != "VISIBLE") null
            else rs.toCrmVisiblePost()
        }
    } ?: return null
    val impact = conn.prepareStatement(
        "SELECT impact_count FROM social_posts WHERE id = ?::uuid"
    ).use { stmt ->
        stmt.setString(1, postId)
        stmt.executeQuery().use { rs -> if (rs.next()) rs.getLong(1) else 0L }
    }
    val comments = conn.prepareStatement(
        """
        SELECT c.id::text, COALESCE(NULLIF(u.display_name, ''), 'Sin nombre'), c.body, c.created_at
        FROM post_comments c
        LEFT JOIN users u ON u.id = c.author_user_id
        WHERE c.post_id = ?::uuid AND c.moderation_status = 'VISIBLE'
        ORDER BY c.created_at
        LIMIT 40
        """.trimIndent()
    ).use { stmt ->
        stmt.setString(1, postId)
        stmt.executeQuery().use { rs ->
            buildList {
                while (rs.next()) add(CrmPostComment(
                    id = rs.getString(1),
                    authorName = rs.getString(2),
                    body = rs.getString(3),
                    createdAt = rs.getTimestamp(4).toInstant().toString(),
                ))
            }
        }
    }
    val reports = conn.prepareStatement(
        """
        SELECT id::text, reason, status::text, created_at
        FROM content_reports
        WHERE post_id = ?::uuid
        ORDER BY created_at DESC
        LIMIT 40
        """.trimIndent()
    ).use { stmt ->
        stmt.setString(1, postId)
        stmt.executeQuery().use { rs ->
            buildList {
                while (rs.next()) add(CrmPostReport(
                    id = rs.getString(1),
                    reason = rs.getString(2),
                    status = rs.getString(3),
                    createdAt = rs.getTimestamp(4).toInstant().toString(),
                ))
            }
        }
    }
    return CrmPostDetail(post = post, impactCount = impact, comments = comments, reports = reports)
}

private fun removeVisiblePosts(conn: Connection, actorId: String, postIds: List<String>, reason: String): List<String> {
    val previous = conn.autoCommit
    conn.autoCommit = false
    try {
        val removed = buildList {
            for (postId in postIds) {
                val updated = conn.prepareStatement(CRM_POST_REMOVE.trim()).use { stmt ->
                    stmt.setString(1, postId)
                    stmt.executeUpdate()
                }
                if (updated != 1) continue
                conn.prepareStatement(
                    """
                    INSERT INTO crm_audit_events
                    (actor_user_id, action, target_type, target_id, reason, outcome)
                    VALUES (?::uuid, 'POST_REMOVE', 'social_post', ?, ?, 'SUCCESS')
                    """.trimIndent()
                ).use { stmt ->
                    stmt.setString(1, actorId)
                    stmt.setString(2, postId)
                    stmt.setString(3, reason)
                    stmt.executeUpdate()
                }
                add(postId)
            }
        }
        conn.commit()
        return removed
    } catch (error: Exception) {
        conn.rollback()
        throw error
    } finally {
        conn.autoCommit = previous
    }
}
