package com.onlygoodthings.backend.crm

import com.onlygoodthings.backend.auth.AuthPrincipal
import com.onlygoodthings.backend.auth.FirebaseTokenVerifier
import com.onlygoodthings.backend.infra.Database
import com.onlygoodthings.shared.domain.ApiResponse
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.auth.principal
import io.ktor.server.request.receiveText
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import java.time.Instant

/**
 * An independent privileged gate; ordinary Firebase auth or COMPANY_ADMIN is insufficient.
 * All grants come from the database, never from untrusted client-supplied role/tenant IDs.
 */
fun Route.crmRoutes(db: Database, verifier: FirebaseTokenVerifier) {
    post("/api/v1/crm/access") {
        val principal = call.principal<AuthPrincipal>() ?: return@post call.respond(
            HttpStatusCode.Unauthorized, ApiResponse.fail<Unit>("Autenticación requerida", "UNAUTHENTICATED")
        )
        // Never store/log this token; the existing Ktor bearer authenticator has already verified it.
        val bearer = call.request.headers["Authorization"]?.removePrefix("Bearer ")?.takeIf { it.isNotBlank() }
            ?: return@post call.respond(HttpStatusCode.Unauthorized, ApiResponse.fail<Unit>("Token requerido", "UNAUTHENTICATED"))
        val verified = runCatching { verifier.verify(bearer) }.getOrNull()
            ?: return@post call.respond(HttpStatusCode.Unauthorized, ApiResponse.fail<Unit>("Token inválido", "UNAUTHENTICATED"))
        val now = Instant.now().epochSecond
        // Sensitive administrative sessions need email verification, MFA and a recent login.
        if (!crmStepUpAllowed(verified.emailVerified, verified.secondFactor, verified.authTimeEpochSeconds, now)) {
            return@post call.respond(HttpStatusCode.Forbidden,
                ApiResponse.fail<Unit>("Se requiere correo verificado, segundo factor y autenticación reciente", "STEP_UP_REQUIRED"))
        }
        val permissions = db.withConnection { conn ->
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
                    buildList { while (rs.next()) add(mapOf("companyId" to rs.getString(1), "role" to rs.getString(2))) }
                }
            }
            staff to companies
        }
        if (permissions.first == null && permissions.second.isEmpty()) {
            return@post call.respond(HttpStatusCode.Forbidden, ApiResponse.fail<Unit>("Sin permisos de CRM", "FORBIDDEN"))
        }
        call.respond(ApiResponse.ok(mapOf(
            "platformRole" to permissions.first,
            "companies" to permissions.second,
            "stepUpValidUntilEpochSeconds" to (verified.authTimeEpochSeconds!! + 900)
        )))
    }
}

/** Pure security predicate: no privilege is granted for missing/old/future claims. */
internal fun crmStepUpAllowed(
    verifiedEmail: Boolean,
    secondFactor: Boolean,
    authTimeSeconds: Long?,
    nowSeconds: Long,
): Boolean = verifiedEmail && secondFactor &&
    authTimeSeconds != null && authTimeSeconds in (nowSeconds - 900)..(nowSeconds + 60)
