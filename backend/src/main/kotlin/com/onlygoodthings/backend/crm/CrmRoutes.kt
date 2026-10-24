package com.onlygoodthings.backend.crm

import com.onlygoodthings.backend.auth.AuthPrincipal
import com.onlygoodthings.backend.auth.FirebaseTokenVerifier
import com.onlygoodthings.backend.http.JsonBody
import com.onlygoodthings.backend.http.optDouble
import com.onlygoodthings.backend.http.optString
import com.onlygoodthings.backend.http.reqString
import com.onlygoodthings.backend.infra.Database
import com.onlygoodthings.backend.media.FrameCrop
import com.onlygoodthings.backend.media.MediaStore
import com.onlygoodthings.backend.realtime.RealtimeHub
import com.onlygoodthings.shared.domain.ApiResponse
import com.onlygoodthings.shared.domain.SocialComment
import com.onlygoodthings.shared.domain.SocialLiveCounters
import com.onlygoodthings.shared.domain.UserLive
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
    val authorUserId: String? = null,
    /** Foto viva del autor. No es una copia guardada en el post. */
    val authorPhotoUrl: String? = null,
    /** Centro del encuadre en la foto, 0..1. Null es el centro del marco 16:9. */
    val frameX: Double? = null,
    val frameY: Double? = null,
    val frameZoom: Double? = null,
    /** Archivo original. Solo lo abre el editor. La fila muestra `imageUrl`. */
    val editorUrl: String? = null,
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
    val authorUserId: String? = null,
    val authorPhotoUrl: String? = null,
)

@Serializable
data class CrmUserPick(
    val id: String,
    val displayName: String,
    val photoUrl: String? = null,
)

@Serializable
data class CrmUserPickPage(val users: List<CrmUserPick>)

@Serializable
data class CrmPostReassigned(
    val id: String,
    val authorUserId: String,
    val authorName: String,
    val authorPhotoUrl: String? = null,
    val commentCount: Int = 0,
    val impactCount: Int = 0,
)

@Serializable
data class CrmPostAnecdote(
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
    val anecdotes: List<CrmPostAnecdote> = emptyList(),
)

@Serializable
data class CrmPostRemoved(val removed: Boolean = true, val ids: List<String> = emptyList())

@Serializable
data class CrmPostFramed(
    val id: String,
    val frameX: Double,
    val frameY: Double,
    val frameZoom: Double,
    val commentCount: Int = 0,
    val impactCount: Int = 0,
    /** JPEG 16:9 que muestran feed, perfil, web y la fila de Control. */
    val imageUrl: String? = null,
    /** Original. El diálogo vuelve a encuadrar esta foto, no el recorte. */
    val editorUrl: String? = null,
)

@Serializable
data class CrmSubnodeRemoved(val removed: Boolean = true, val id: String, val postId: String)

/** Nodo que ya escucha el feed: `ogt/social/posts/{id}`. */
internal fun crmRemovedPostLive(postId: String): SocialLiveCounters = SocialLiveCounters(
    id = postId,
    commentCount = 0,
    impactCount = 0,
    moderationStatus = "REMOVED",
)

internal fun crmRemovedPostPath(postId: String): String = OgtDbPaths.socialPost(postId)

/** Nodo que ya escucha la ficha: `ogt/social/posts/{postId}/comments/{commentId}`. */
internal fun crmRemovedCommentPath(postId: String, commentId: String): String =
    OgtDbPaths.socialComment(postId, commentId)

internal fun crmRemovedCommentLive(
    postId: String,
    commentId: String,
    authorUserId: String,
    body: String,
): SocialComment = SocialComment(
    id = commentId,
    postId = postId,
    authorUserId = authorUserId,
    authorName = "",
    body = body,
    createdAtEpochMs = 0L,
    moderationStatus = "REMOVED",
)

/** La anécdota no tiene nodo propio. Sale por el post que el feed y la ficha ya escuchan. */
internal fun crmRemovedAnecdotePath(postId: String): String = OgtDbPaths.socialPost(postId)

internal fun crmRemovedAnecdoteLive(
    postId: String,
    anecdoteId: String,
    commentCount: Int,
    impactCount: Int,
): SocialLiveCounters = SocialLiveCounters(
    id = postId,
    commentCount = commentCount,
    impactCount = impactCount,
    removedAnecdoteId = anecdoteId,
)

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

fun Route.crmRoutes(db: Database, verifier: FirebaseTokenVerifier, hub: RealtimeHub, media: MediaStore) {
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
    /**
     * Busca un usuario activo para atribuirle una publicación.
     * Body: `{ "query": "Ana" }`
     */
    post("/api/v1/crm/platform/users/search") {
        val auth = crmContext(call, db, verifier) ?: return@post
        if (!crmCanManagePosts(auth.platformRole)) {
            return@post call.respond(HttpStatusCode.Forbidden,
                ApiResponse.fail<Unit>("Sin permiso para administrar publicaciones", "FORBIDDEN"))
        }
        val data = JsonBody.receiveMap(call)
        val query = data.optString("query")?.trim().orEmpty()
        if (query.length !in 2..120) {
            return@post call.respond(HttpStatusCode.BadRequest,
                ApiResponse.fail<Unit>("Escribí al menos dos letras", "BAD_REQUEST"))
        }
        val users = db.withConnection { conn -> searchActiveUsers(conn, query) }
        call.respond(ApiResponse.ok(CrmUserPickPage(users)))
    }
    /**
     * Pasa la autoría de una publicación visible a otro usuario.
     * Body: `{ "postId": "55555555-5555-5555-5555-555555555555", "userId": "11111111-1111-1111-1111-111111111111" }`
     */
    post("/api/v1/crm/platform/posts/reassign") {
        val auth = crmContext(call, db, verifier) ?: return@post
        if (!crmCanManagePosts(auth.platformRole)) {
            return@post call.respond(HttpStatusCode.Forbidden,
                ApiResponse.fail<Unit>("Sin permiso para administrar publicaciones", "FORBIDDEN"))
        }
        val data = JsonBody.receiveMap(call)
        val postId = runCatching { UUID.fromString(data.reqString("postId")).toString() }.getOrNull()
            ?: return@post call.respond(HttpStatusCode.BadRequest,
                ApiResponse.fail<Unit>("Publicación inválida", "BAD_REQUEST"))
        val userId = runCatching { UUID.fromString(data.reqString("userId")).toString() }.getOrNull()
            ?: return@post call.respond(HttpStatusCode.BadRequest,
                ApiResponse.fail<Unit>("Usuario inválido", "BAD_REQUEST"))
        val moved = db.withConnection { conn -> reassignVisiblePost(conn, auth.userId, postId, userId) }
            ?: return@post call.respond(HttpStatusCode.NotFound,
                ApiResponse.fail<Unit>("No se pudo cambiar la autoría", "NOT_FOUND"))
        hub.projectSocialPost(
            moved.id,
            WsCodec.json.encodeToString(
                SocialLiveCounters.serializer(),
                SocialLiveCounters(
                    id = moved.id,
                    commentCount = moved.commentCount,
                    impactCount = moved.impactCount,
                    authorUserId = moved.authorUserId,
                    authorName = moved.authorName,
                    authorPhotoUrl = moved.authorPhotoUrl,
                ),
            ),
        )
        hub.projectUser(
            moved.authorUserId,
            WsCodec.json.encodeToString(
                UserLive.serializer(),
                UserLive(
                    id = moved.authorUserId,
                    displayName = moved.authorName,
                    photoUrl = moved.authorPhotoUrl,
                ),
            ),
        )
        call.respond(ApiResponse.ok(moved, "La publicación quedó a nombre de ${moved.authorName}"))
    }
    /**
     * Recorta la primera foto al marco 16:9 y avisa `ogt/social/posts/{id}` con la URL nueva.
     * Body: `{ "postId": "55555555-5555-5555-5555-555555555555", "frameX": 0.5, "frameY": 0.35, "frameZoom": 1.2 }`
     * Respuesta: `imageUrl` es el JPEG que leen las pantallas. `editorUrl` es el original, solo para volver a encuadrar.
     */
    post("/api/v1/crm/platform/posts/frame") {
        val auth = crmContext(call, db, verifier) ?: return@post
        if (!crmCanManagePosts(auth.platformRole)) {
            return@post call.respond(HttpStatusCode.Forbidden,
                ApiResponse.fail<Unit>("Sin permiso para administrar publicaciones", "FORBIDDEN"))
        }
        val data = JsonBody.receiveMap(call)
        val postId = runCatching { UUID.fromString(data.reqString("postId")).toString() }.getOrNull()
            ?: return@post call.respond(HttpStatusCode.BadRequest,
                ApiResponse.fail<Unit>("Publicación inválida", "BAD_REQUEST"))
        val frame = crmPostFrame(data.optDouble("frameX"), data.optDouble("frameY"), data.optDouble("frameZoom"))
            ?: return@post call.respond(HttpStatusCode.BadRequest,
                ApiResponse.fail<Unit>("El encuadre no entra en el marco", "BAD_REQUEST"))
        val source = db.withConnection { conn -> frameEditorSource(conn, postId) }
            ?: return@post call.respond(HttpStatusCode.NotFound,
                ApiResponse.fail<Unit>("Esa publicación ya no está visible", "NOT_FOUND"))
        val original = media.readLocal(source.editorUrl)
            ?: return@post call.respond(HttpStatusCode.NotFound,
                ApiResponse.fail<Unit>("La foto original no está en el servidor", "NOT_FOUND"))
        val cropped = runCatching { FrameCrop.jpeg(original, frame.frameX, frame.frameY, frame.frameZoom) }
            .getOrElse {
                return@post call.respond(HttpStatusCode.BadRequest,
                    ApiResponse.fail<Unit>("El encuadre no entra en el marco", "BAD_REQUEST"))
            }
        val stored = runCatching { media.save(cropped, "encuadre.jpg", "image/jpeg") }.getOrElse {
            return@post call.respond(HttpStatusCode.BadRequest,
                ApiResponse.fail<Unit>("No se pudo guardar el recorte", "BAD_REQUEST"))
        }
        val saved = db.withConnection { conn ->
            frameVisiblePost(conn, auth.userId, postId, frame, stored.url, source.editorUrl)
        }
        if (saved == null) {
            media.deleteLocal(stored.url)
            return@post call.respond(HttpStatusCode.NotFound,
                ApiResponse.fail<Unit>("Esa publicación ya no está visible", "NOT_FOUND"))
        }
        if (source.displayUrl != source.editorUrl) media.deleteLocal(source.displayUrl)
        val counts = db.withConnection { conn -> postLiveCounts(conn, saved.id) }
        hub.projectSocialPost(
            saved.id,
            WsCodec.json.encodeToString(
                SocialLiveCounters.serializer(),
                SocialLiveCounters(
                    id = saved.id,
                    commentCount = counts?.commentCount ?: saved.commentCount,
                    impactCount = counts?.impactCount ?: saved.impactCount,
                    frameX = saved.frameX,
                    frameY = saved.frameY,
                    frameZoom = saved.frameZoom,
                    imageUrl = saved.imageUrl,
                ),
            ),
        )
        call.respond(ApiResponse.ok(saved))
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
    post("/api/v1/crm/platform/posts/comments/remove") {
        val auth = crmContext(call, db, verifier) ?: return@post
        if (!crmCanManagePosts(auth.platformRole)) {
            return@post call.respond(HttpStatusCode.Forbidden,
                ApiResponse.fail<Unit>("Sin permiso para administrar publicaciones", "FORBIDDEN"))
        }
        val data = JsonBody.receiveMap(call)
        val commentId = crmOneId(data, "commentId")
            ?: return@post call.respond(HttpStatusCode.BadRequest,
                ApiResponse.fail<Unit>("Comentario inválido", "BAD_REQUEST"))
        val reason = crmActionReason(data)
            ?: return@post call.respond(HttpStatusCode.BadRequest,
                ApiResponse.fail<Unit>("El motivo tiene que explicar la quita", "BAD_REQUEST"))
        val removed = db.withConnection { conn -> removeVisibleComment(conn, auth.userId, commentId, reason) }
            ?: return@post call.respond(HttpStatusCode.NotFound,
                ApiResponse.fail<Unit>("Ese comentario ya no está visible", "NOT_FOUND"))
        hub.projectSocialComment(
            removed.postId,
            removed.id,
            WsCodec.json.encodeToString(
                SocialComment.serializer(),
                crmRemovedCommentLive(removed.postId, removed.id, removed.authorUserId, removed.body),
            ),
        )
        val counts = db.withConnection { conn -> postLiveCounts(conn, removed.postId) }
        if (counts != null) {
            hub.projectSocialPost(
                removed.postId,
                WsCodec.json.encodeToString(SocialLiveCounters.serializer(), counts),
            )
        }
        call.respond(ApiResponse.ok(CrmSubnodeRemoved(id = removed.id, postId = removed.postId)))
    }
    post("/api/v1/crm/platform/posts/anecdotes/remove") {
        val auth = crmContext(call, db, verifier) ?: return@post
        if (!crmCanManagePosts(auth.platformRole)) {
            return@post call.respond(HttpStatusCode.Forbidden,
                ApiResponse.fail<Unit>("Sin permiso para administrar publicaciones", "FORBIDDEN"))
        }
        val data = JsonBody.receiveMap(call)
        val anecdoteId = crmOneId(data, "anecdoteId")
            ?: return@post call.respond(HttpStatusCode.BadRequest,
                ApiResponse.fail<Unit>("Anécdota inválida", "BAD_REQUEST"))
        val reason = crmActionReason(data)
            ?: return@post call.respond(HttpStatusCode.BadRequest,
                ApiResponse.fail<Unit>("El motivo tiene que explicar la quita", "BAD_REQUEST"))
        val removed = db.withConnection { conn -> removeVisibleAnecdote(conn, auth.userId, anecdoteId, reason) }
            ?: return@post call.respond(HttpStatusCode.NotFound,
                ApiResponse.fail<Unit>("Esa anécdota ya no está visible", "NOT_FOUND"))
        removed.comments.forEach { comment ->
            hub.projectSocialComment(
                comment.postId,
                comment.id,
                WsCodec.json.encodeToString(
                    SocialComment.serializer(),
                    crmRemovedCommentLive(comment.postId, comment.id, comment.authorUserId, comment.body),
                ),
            )
        }
        hub.projectSocialPost(
            removed.postId,
            WsCodec.json.encodeToString(
                SocialLiveCounters.serializer(),
                crmRemovedAnecdoteLive(removed.postId, removed.id, removed.commentCount, removed.impactCount),
            ),
        )
        call.respond(ApiResponse.ok(CrmSubnodeRemoved(id = removed.id, postId = removed.postId)))
    }
}

internal fun crmOneId(data: Map<String, Any?>, key: String): String? =
    data.optString(key)?.let { value ->
        runCatching { UUID.fromString(value.trim()).toString() }.getOrNull()
    }

internal fun crmActionReason(data: Map<String, Any?>): String? {
    val reason = data.optString("reason")?.trim().orEmpty()
    return reason.takeIf { it.length in 3..500 }
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
       p.author_user_id::text AS author_user_id,
       u.photo_url AS author_photo,
       photo.url AS image_url,
       photo.original_url AS editor_url,
       photo.frame_x, photo.frame_y, photo.frame_zoom
FROM social_posts p
LEFT JOIN users u ON u.id = p.author_user_id
LEFT JOIN companies c ON c.id = p.author_company_id
LEFT JOIN LATERAL (
  SELECT m.url, m.original_url, m.frame_x, m.frame_y, m.frame_zoom
  FROM post_media m
  WHERE m.post_id = p.id AND m.kind = 'IMAGE'
  ORDER BY m.sort_order
  LIMIT 1
) photo ON true
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
    authorUserId = getString("author_user_id"),
    authorPhotoUrl = getString("author_photo")?.trim()?.takeIf { it.isNotEmpty() },
    frameX = frameUnit("frame_x"),
    frameY = frameUnit("frame_y"),
    frameZoom = frameUnit("frame_zoom"),
    editorUrl = crmPostImageUrl(getString("editor_url")),
)

private fun ResultSet.frameUnit(column: String): Double? {
    val value = (getObject(column) as? Number)?.toDouble() ?: return null
    return value.takeIf { it.isFinite() }
}

/** El marco es 16:9. x/y son el punto de la foto que queda al centro. zoom 1 cubre. */
internal fun crmPostFrame(x: Double?, y: Double?, zoom: Double?): CrmPostFramed? {
    if (x == null || y == null) return null
    val scale = zoom ?: 1.0
    if (!x.isFinite() || !y.isFinite() || !scale.isFinite()) return null
    if (x !in 0.0..1.0 || y !in 0.0..1.0 || scale !in 1.0..6.0) return null
    return CrmPostFramed(id = "", frameX = x, frameY = y, frameZoom = scale)
}

internal const val CRM_POST_REMOVE = """
UPDATE social_posts
SET moderation_status = 'REMOVED', updated_at = now()
WHERE id = ?::uuid AND moderation_status = 'VISIBLE'
"""

/** Nombre, correo o id de una cuenta activa. No devuelve firebase_uid ni el correo. */
internal const val CRM_USER_SEARCH = """
SELECT id::text, display_name, photo_url
FROM users
WHERE status = 'ACTIVE'
  AND (
    display_name ILIKE ? ESCAPE '\'
    OR COALESCE(email::text, '') ILIKE ? ESCAPE '\'
    OR id::text = ?
  )
ORDER BY display_name
LIMIT 12
"""

/**
 * `url` pasa a ser el JPEG recortado. `original_url` conserva el archivo del editor.
 * No hay DELETE: el original sigue en disco.
 */
internal const val CRM_POST_FRAME = """
UPDATE post_media AS m
SET frame_x = ?, frame_y = ?, frame_zoom = ?,
    original_url = COALESCE(m.original_url, m.url),
    url = ?
WHERE m.id = (
  SELECT id FROM post_media
  WHERE post_id = ?::uuid AND kind = 'IMAGE'
  ORDER BY sort_order
  LIMIT 1
)
AND EXISTS (
  SELECT 1 FROM social_posts p
  WHERE p.id = ?::uuid AND p.moderation_status = 'VISIBLE'
)
"""

/** La copia denormalizada que todavía leen algunos clientes. El índice 1 es la primera foto. */
internal const val CRM_POST_DISPLAY_URL = """
UPDATE social_posts
SET media_urls = CASE
      WHEN cardinality(media_urls) < 1 THEN ARRAY[?::text]
      ELSE ARRAY[?::text] || media_urls[2:cardinality(media_urls)]
    END,
    updated_at = now()
WHERE id = ?::uuid AND moderation_status = 'VISIBLE'
"""

/** Cambia el autor. La fila sigue. No es un DELETE. */
internal const val CRM_POST_ATTRIBUTE = """
UPDATE social_posts
SET author_kind = 'USER',
    author_user_id = ?::uuid,
    author_company_id = NULL,
    updated_at = now()
WHERE id = ?::uuid AND moderation_status = 'VISIBLE'
"""

internal const val CRM_COMMENT_REMOVE = """
UPDATE post_comments
SET moderation_status = 'REMOVED'
WHERE id = ?::uuid AND moderation_status = 'VISIBLE'
"""

internal const val CRM_ANECDOTE_REMOVE = """
UPDATE post_anecdotes
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

private fun searchActiveUsers(conn: Connection, query: String): List<CrmUserPick> {
    val pattern = crmLikeContains(query)
    return conn.prepareStatement(CRM_USER_SEARCH.trim()).use { stmt ->
        stmt.setString(1, pattern)
        stmt.setString(2, pattern)
        stmt.setString(3, query)
        stmt.executeQuery().use { rs ->
            buildList {
                while (rs.next()) add(CrmUserPick(
                    id = rs.getString(1),
                    displayName = rs.getString(2) ?: "Vecino",
                    photoUrl = rs.getString(3)?.trim()?.takeIf { it.isNotEmpty() },
                ))
            }
        }
    }
}

/** La fila de users sigue siendo la foto. Acá solo cambia quién firmó el post. */
private fun reassignVisiblePost(conn: Connection, actorId: String, postId: String, userId: String): CrmPostReassigned? {
    val target = conn.prepareStatement(
        """
        SELECT display_name, photo_url
        FROM users
        WHERE id = ?::uuid AND status = 'ACTIVE'
        """.trimIndent()
    ).use { stmt ->
        stmt.setString(1, userId)
        stmt.executeQuery().use { rs ->
            if (!rs.next()) null else (rs.getString(1) ?: "Vecino") to rs.getString(2)?.trim()?.takeIf { it.isNotEmpty() }
        }
    } ?: return null
    conn.autoCommit = false
    try {
        val updated = conn.prepareStatement(CRM_POST_ATTRIBUTE.trim()).use { stmt ->
            stmt.setString(1, userId)
            stmt.setString(2, postId)
            stmt.executeUpdate()
        }
        if (updated == 0) {
            conn.rollback()
            return null
        }
        conn.prepareStatement(
            """
            DELETE FROM post_people
            WHERE post_id = ?::uuid AND role = 'AUTHOR' AND user_id IS DISTINCT FROM ?::uuid
            """.trimIndent()
        ).use { stmt ->
            stmt.setString(1, postId)
            stmt.setString(2, userId)
            stmt.executeUpdate()
        }
        conn.prepareStatement(
            """
            INSERT INTO post_people (post_id, user_id, role)
            SELECT ?::uuid, ?::uuid, 'AUTHOR'
            WHERE NOT EXISTS (
                SELECT 1 FROM post_people
                WHERE post_id = ?::uuid AND user_id = ?::uuid AND role = 'AUTHOR'
            )
            """.trimIndent()
        ).use { stmt ->
            stmt.setString(1, postId)
            stmt.setString(2, userId)
            stmt.setString(3, postId)
            stmt.setString(4, userId)
            stmt.executeUpdate()
        }
        val counts = conn.prepareStatement(
            "SELECT comment_count, impact_count FROM social_posts WHERE id = ?::uuid"
        ).use { stmt ->
            stmt.setString(1, postId)
            stmt.executeQuery().use { rs ->
                if (!rs.next()) 0 to 0 else rs.getInt(1) to rs.getInt(2)
            }
        }
        writeAudit(conn, actorId, "POST_ATTRIBUTE", "social_post", postId, "Atribución a ${target.first}")
        conn.commit()
        return CrmPostReassigned(
            id = postId,
            authorUserId = userId,
            authorName = target.first,
            authorPhotoUrl = target.second,
            commentCount = counts.first,
            impactCount = counts.second,
        )
    } catch (error: Exception) {
        conn.rollback()
        throw error
    } finally {
        conn.autoCommit = true
    }
}

private data class FrameEditorSource(val displayUrl: String, val editorUrl: String)

/** La foto que el editor vuelve a abrir. Si todavía no hay original, es la que está publicada. */
private fun frameEditorSource(conn: Connection, postId: String): FrameEditorSource? =
    conn.prepareStatement(
        """
        SELECT m.url, m.original_url
        FROM post_media m
        JOIN social_posts p ON p.id = m.post_id
        WHERE m.post_id = ?::uuid AND m.kind = 'IMAGE' AND p.moderation_status = 'VISIBLE'
        ORDER BY m.sort_order
        LIMIT 1
        """.trimIndent()
    ).use { stmt ->
        stmt.setString(1, postId)
        stmt.executeQuery().use { rs ->
            if (!rs.next()) return null
            val display = rs.getString("url")?.trim().orEmpty()
            val original = rs.getString("original_url")?.trim().orEmpty().ifEmpty { display }
            if (display.isEmpty() || original.isEmpty()) null else FrameEditorSource(display, original)
        }
    }

/** Guarda el recorte en `url` y deja el original en `original_url`. No borra ese original. */
private fun frameVisiblePost(
    conn: Connection,
    actorId: String,
    postId: String,
    frame: CrmPostFramed,
    displayUrl: String,
    editorUrl: String,
): CrmPostFramed? {
    conn.autoCommit = false
    try {
        val updated = conn.prepareStatement(CRM_POST_FRAME.trim()).use { stmt ->
            stmt.setDouble(1, frame.frameX)
            stmt.setDouble(2, frame.frameY)
            stmt.setDouble(3, frame.frameZoom)
            stmt.setString(4, displayUrl)
            stmt.setString(5, postId)
            stmt.setString(6, postId)
            stmt.executeUpdate()
        }
        if (updated == 0) {
            conn.rollback()
            return null
        }
        conn.prepareStatement(CRM_POST_DISPLAY_URL.trim()).use { stmt ->
            stmt.setString(1, displayUrl)
            stmt.setString(2, displayUrl)
            stmt.setString(3, postId)
            stmt.executeUpdate()
        }
        val counts = conn.prepareStatement(
            "SELECT comment_count, impact_count FROM social_posts WHERE id = ?::uuid"
        ).use { stmt ->
            stmt.setString(1, postId)
            stmt.executeQuery().use { rs ->
                if (!rs.next()) 0 to 0 else rs.getInt(1) to rs.getInt(2)
            }
        }
        writeAudit(conn, actorId, "POST_FRAME", "social_post", postId, "Recorte 16:9")
        conn.commit()
        return frame.copy(
            id = postId,
            commentCount = counts.first,
            impactCount = counts.second,
            imageUrl = displayUrl,
            editorUrl = editorUrl,
        )
    } catch (error: Exception) {
        conn.rollback()
        throw error
    } finally {
        conn.autoCommit = true
    }
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
        SELECT c.id::text, c.author_user_id::text,
               COALESCE(NULLIF(u.display_name, ''), 'Sin nombre'), u.photo_url, c.body, c.created_at
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
                    authorUserId = rs.getString(2),
                    authorName = rs.getString(3),
                    authorPhotoUrl = rs.getString(4)?.trim()?.takeIf { it.isNotEmpty() },
                    body = rs.getString(5),
                    createdAt = rs.getTimestamp(6).toInstant().toString(),
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
    val anecdotes = conn.prepareStatement(
        """
        SELECT a.id::text, COALESCE(NULLIF(u.display_name, ''), 'Sin nombre'), a.body, a.created_at
        FROM post_anecdotes a
        LEFT JOIN users u ON u.id = a.author_user_id
        WHERE a.post_id = ?::uuid AND a.moderation_status = 'VISIBLE'
        ORDER BY a.sort_order, a.created_at
        LIMIT 40
        """.trimIndent()
    ).use { stmt ->
        stmt.setString(1, postId)
        stmt.executeQuery().use { rs ->
            buildList {
                while (rs.next()) add(CrmPostAnecdote(
                    id = rs.getString(1),
                    authorName = rs.getString(2),
                    body = rs.getString(3),
                    createdAt = rs.getTimestamp(4).toInstant().toString(),
                ))
            }
        }
    }
    return CrmPostDetail(post = post, impactCount = impact, comments = comments, reports = reports, anecdotes = anecdotes)
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

private data class CrmHiddenComment(
    val id: String,
    val postId: String,
    val anecdoteId: String?,
    val authorUserId: String,
    val body: String,
)

private data class CrmHiddenAnecdote(
    val id: String,
    val postId: String,
    val comments: List<CrmHiddenComment>,
    val commentCount: Int,
    val impactCount: Int,
)

private fun writeAudit(conn: Connection, actorId: String, action: String, targetType: String, targetId: String, reason: String) {
    conn.prepareStatement(
        """
        INSERT INTO crm_audit_events
        (actor_user_id, action, target_type, target_id, reason, outcome)
        VALUES (?::uuid, ?, ?, ?, ?, 'SUCCESS')
        """.trimIndent()
    ).use { stmt ->
        stmt.setString(1, actorId)
        stmt.setString(2, action)
        stmt.setString(3, targetType)
        stmt.setString(4, targetId)
        stmt.setString(5, reason)
        stmt.executeUpdate()
    }
}

private fun recountVisibleComments(conn: Connection, postId: String, anecdoteId: String?) {
    if (anecdoteId.isNullOrBlank()) {
        conn.prepareStatement(
            """
            UPDATE social_posts
            SET comment_count = (
                SELECT COUNT(*) FROM post_comments
                WHERE post_id = ?::uuid AND anecdote_id IS NULL AND moderation_status = 'VISIBLE'
            ),
            updated_at = now()
            WHERE id = ?::uuid
            """.trimIndent()
        ).use { stmt ->
            stmt.setString(1, postId)
            stmt.setString(2, postId)
            stmt.executeUpdate()
        }
    } else {
        conn.prepareStatement(
            """
            UPDATE post_anecdotes
            SET comment_count = (
                SELECT COUNT(*) FROM post_comments
                WHERE anecdote_id = ?::uuid AND moderation_status = 'VISIBLE'
            ),
            updated_at = now()
            WHERE id = ?::uuid
            """.trimIndent()
        ).use { stmt ->
            stmt.setString(1, anecdoteId)
            stmt.setString(2, anecdoteId)
            stmt.executeUpdate()
        }
    }
}

private fun postLiveCounts(conn: Connection, postId: String): SocialLiveCounters? =
    conn.prepareStatement(
        "SELECT id::text, comment_count, impact_count FROM social_posts WHERE id = ?::uuid"
    ).use { stmt ->
        stmt.setString(1, postId)
        stmt.executeQuery().use { rs ->
            if (!rs.next()) null
            else SocialLiveCounters(
                id = rs.getString(1),
                commentCount = rs.getInt(2),
                impactCount = rs.getInt(3),
            )
        }
    }

private fun readVisibleComment(conn: Connection, commentId: String): CrmHiddenComment? =
    conn.prepareStatement(
        """
        SELECT id::text, post_id::text, anecdote_id::text, author_user_id::text, body
        FROM post_comments
        WHERE id = ?::uuid AND moderation_status = 'VISIBLE'
        """.trimIndent()
    ).use { stmt ->
        stmt.setString(1, commentId)
        stmt.executeQuery().use { rs ->
            if (!rs.next()) null
            else CrmHiddenComment(
                id = rs.getString(1),
                postId = rs.getString(2),
                anecdoteId = rs.getString(3),
                authorUserId = rs.getString(4),
                body = rs.getString(5),
            )
        }
    }

private fun removeVisibleComment(conn: Connection, actorId: String, commentId: String, reason: String): CrmHiddenComment? {
    val previous = conn.autoCommit
    conn.autoCommit = false
    try {
        val row = readVisibleComment(conn, commentId)
        if (row == null) {
            conn.rollback()
            return null
        }
        val updated = conn.prepareStatement(CRM_COMMENT_REMOVE.trim()).use { stmt ->
            stmt.setString(1, commentId)
            stmt.executeUpdate()
        }
        if (updated != 1) {
            conn.rollback()
            return null
        }
        recountVisibleComments(conn, row.postId, row.anecdoteId)
        writeAudit(conn, actorId, "COMMENT_REMOVE", "post_comment", row.id, reason)
        conn.commit()
        return row
    } catch (error: Exception) {
        conn.rollback()
        throw error
    } finally {
        conn.autoCommit = previous
    }
}

private fun removeVisibleAnecdote(conn: Connection, actorId: String, anecdoteId: String, reason: String): CrmHiddenAnecdote? {
    val previous = conn.autoCommit
    conn.autoCommit = false
    try {
        val postId = conn.prepareStatement(
            """
            SELECT post_id::text FROM post_anecdotes
            WHERE id = ?::uuid AND moderation_status = 'VISIBLE'
            """.trimIndent()
        ).use { stmt ->
            stmt.setString(1, anecdoteId)
            stmt.executeQuery().use { rs -> if (rs.next()) rs.getString(1) else null }
        }
        if (postId == null) {
            conn.rollback()
            return null
        }
        val updated = conn.prepareStatement(CRM_ANECDOTE_REMOVE.trim()).use { stmt ->
            stmt.setString(1, anecdoteId)
            stmt.executeUpdate()
        }
        if (updated != 1) {
            conn.rollback()
            return null
        }
        val comments = conn.prepareStatement(
            """
            SELECT id::text, post_id::text, anecdote_id::text, author_user_id::text, body
            FROM post_comments
            WHERE anecdote_id = ?::uuid AND moderation_status = 'VISIBLE'
            """.trimIndent()
        ).use { stmt ->
            stmt.setString(1, anecdoteId)
            stmt.executeQuery().use { rs ->
                buildList {
                    while (rs.next()) add(CrmHiddenComment(
                        id = rs.getString(1),
                        postId = rs.getString(2),
                        anecdoteId = rs.getString(3),
                        authorUserId = rs.getString(4),
                        body = rs.getString(5),
                    ))
                }
            }
        }
        comments.forEach { comment ->
            conn.prepareStatement(CRM_COMMENT_REMOVE.trim()).use { stmt ->
                stmt.setString(1, comment.id)
                stmt.executeUpdate()
            }
            writeAudit(conn, actorId, "COMMENT_REMOVE", "post_comment", comment.id, reason)
        }
        writeAudit(conn, actorId, "ANECDOTE_REMOVE", "post_anecdote", anecdoteId, reason)
        val counts = postLiveCounts(conn, postId)
        conn.commit()
        return CrmHiddenAnecdote(
            id = anecdoteId,
            postId = postId,
            comments = comments,
            commentCount = counts?.commentCount ?: 0,
            impactCount = counts?.impactCount ?: 0,
        )
    } catch (error: Exception) {
        conn.rollback()
        throw error
    } finally {
        conn.autoCommit = previous
    }
}
