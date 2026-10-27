package com.onlygoodthings.backend.crm

import com.onlygoodthings.backend.auth.AuthPrincipal
import com.onlygoodthings.backend.auth.FirebaseTokenVerifier
import com.onlygoodthings.backend.http.*
import com.onlygoodthings.backend.infra.Database
import com.onlygoodthings.backend.realtime.RealtimeHub
import com.onlygoodthings.backend.stories.StorySqlRepository
import com.onlygoodthings.shared.domain.ApiResponse
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

/** Same step-up and approved staff grants as the existing CRM; no role shortcuts. */
fun Route.crmStoryRoutes(db: Database, verifier: FirebaseTokenVerifier, repository: StorySqlRepository, hub: RealtimeHub) {
    suspend fun authorized(call: ApplicationCall): AuthPrincipal? {
        val principal=call.principal<AuthPrincipal>() ?: return null.also { call.respond(HttpStatusCode.Unauthorized) }
        val token=call.request.headers["Authorization"]?.removePrefix("Bearer ").orEmpty()
        val verified=runCatching { verifier.verify(token) }.getOrNull()
        if(verified==null || verified.firebaseUid!=principal.firebaseUid || !crmStepUpAllowed(verified.emailVerified,
            verified.secondFactor,verified.authTimeEpochSeconds,System.currentTimeMillis()/1000)) {
            call.respond(HttpStatusCode.Forbidden,ApiResponse.fail<Unit>("Se requiere un nuevo ingreso con segundo factor","STEP_UP_REQUIRED"));return null
        }
        val role=db.withConnection { c->c.prepareStatement("""SELECT s.staff_role FROM crm_platform_staff s JOIN users u ON u.id=s.user_id
            WHERE s.user_id=?::uuid AND s.enabled AND s.approved_by IS NOT NULL AND s.approved_at IS NOT NULL AND u.status='ACTIVE'""").use { s->s.setString(1,principal.userId);s.executeQuery().use { if(it.next()) it.getString(1) else null } } }
        if(!crmCanManagePosts(role)) { call.respond(HttpStatusCode.Forbidden,ApiResponse.fail<Unit>("Sin permiso para administrar historias","FORBIDDEN"));return null }
        return principal
    }
    post("/api/v1/crm/platform/stories/search") {
        val auth=authorized(call) ?: return@post
        val data=JsonBody.receiveMap(call)
        call.respond(ApiResponse.ok(repository.list(auth.userId,data.optString("cursor"),true,data.optString("query").orEmpty())))
    }
    post("/api/v1/crm/platform/stories/remove") {
        val auth=authorized(call) ?: return@post
        val data=JsonBody.receiveMap(call)
        val reason=data.reqString("reason").trim()
        require(reason.length in 3..500) { "Indicá el motivo de la retirada" }
        repository.remove(data.reqString("storyId"),auth.userId,true,reason)
        hub.projectStoryChange()
        call.respond(ApiResponse.ok("ok"))
    }
}
