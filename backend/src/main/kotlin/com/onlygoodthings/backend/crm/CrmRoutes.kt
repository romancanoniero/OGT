package com.onlygoodthings.backend.crm

import com.onlygoodthings.backend.auth.AuthPrincipal
import com.onlygoodthings.backend.auth.FirebaseTokenVerifier
import com.onlygoodthings.backend.http.JsonBody
import com.onlygoodthings.backend.http.reqString
import com.onlygoodthings.backend.infra.Database
import com.onlygoodthings.shared.domain.ApiResponse
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.call
import io.ktor.server.auth.principal
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import kotlinx.serialization.Serializable
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

fun Route.crmRoutes(db: Database, verifier: FirebaseTokenVerifier) {
    post("/api/v1/crm/access") {
        val auth = crmContext(call, db, verifier) ?: return@post
        call.respond(ApiResponse.ok(CrmAccessResponse(
            platformRole = auth.platformRole,
            companies = auth.companies,
            stepUpValidUntilEpochSeconds = auth.authTimeSeconds + 900,
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
}

/** A refreshed JWT is NOT a freshly authenticated session. */
internal fun crmStepUpAllowed(
    verifiedEmail: Boolean,
    secondFactor: Boolean,
    authTimeSeconds: Long?,
    nowSeconds: Long,
): Boolean = verifiedEmail && secondFactor &&
    authTimeSeconds != null && authTimeSeconds in (nowSeconds - 900)..(nowSeconds + 60)
