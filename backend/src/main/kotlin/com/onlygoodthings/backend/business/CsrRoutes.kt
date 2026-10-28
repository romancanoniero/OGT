package com.onlygoodthings.backend.business

import com.onlygoodthings.backend.auth.AuthPrincipal
import com.onlygoodthings.backend.auth.FirebaseTokenVerifier
import com.onlygoodthings.backend.crm.crmStepUpAllowed
import com.onlygoodthings.backend.http.JsonBody
import com.onlygoodthings.shared.domain.ApiResponse
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.*
import io.ktor.server.auth.principal
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.*
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

internal fun Map<String,Any?>.text(key:String)= (this[key] as? String)?.trim()?.takeIf{it.isNotEmpty()}
    ?: throw IllegalArgumentException("Campo requerido: $key")
internal fun Map<String,Any?>.uuid(key:String)= UUID.fromString(text(key)).toString()
internal fun Map<String,Any?>.integer(key:String):Long = try {
    val value=this[key];require(value is Number || value is String)
    BigDecimal(value.toString()).longValueExact()
}catch(_:Exception){throw IllegalArgumentException("Campo entero inválido: $key")}
private suspend fun businessActor(call:ApplicationCall,verifier:FirebaseTokenVerifier):String? {
    val actor=call.principal<AuthPrincipal>() ?: return null
    val token=call.request.headers["Authorization"]?.substringAfter(' ',"").orEmpty()
    val verified=runCatching{verifier.verify(token)}.getOrNull()
    if(verified==null || verified.firebaseUid!=actor.firebaseUid ||
        !crmStepUpAllowed(verified.emailVerified,verified.secondFactor,verified.authTimeEpochSeconds,Instant.now().epochSecond)) {
        call.respond(HttpStatusCode.Forbidden,ApiResponse.fail<Unit>("Ingresá al acceso privado con correo verificado y segundo factor","STEP_UP_REQUIRED"));return null
    }
    return actor.userId
}

/** The same tenant and step-up boundary protects both corporate interfaces. */
fun Route.businessRoutes(repository:CsrRepository,verifier:FirebaseTokenVerifier,payments:CsrPayments?) {
    val registrations=CsrRegistrations(repository.db)
    val team=CsrTeam(repository)
    fun endpoint(path:String,action:(String,Map<String,Any?>)->Any) {
        post("/api/v1/csr/$path") {
            val actor=businessActor(call,verifier) ?: return@post
            val data=JsonBody.receiveMap(call)
            if(path in setOf("finance-action","promo/issue") && data["requestId"] !is String){
                call.respond(HttpStatusCode.UpgradeRequired,ApiResponse.fail<Unit>("Actualizá esta versión para operar saldo o cupones de forma segura.","UPDATE_REQUIRED"));return@post
            }
            // Jackson is used for this heterogeneous legacy envelope only; DTOs remain typed.
            val result=kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO){action(actor,data)}
            call.respondText(JsonBody.objectMapper.writeValueAsString(mapOf("success" to true,"data" to result)),io.ktor.http.ContentType.Application.Json)
        }
    }
    endpoint("team/inbox") {actor,_ ->team.inbox(actor)}
    endpoint("team/list") {actor,d ->team.desk(actor,d.uuid("companyId"))}
    endpoint("team/invite") {actor,d ->team.invite(actor,d.uuid("companyId"),d.uuid("requestId"),d.text("email"),d.text("role"))}
    endpoint("team/role") {actor,d ->team.member(actor,d.uuid("companyId"),d.uuid("requestId"),d.uuid("userId"),d.text("role"))}
    endpoint("team/remove") {actor,d ->team.member(actor,d.uuid("companyId"),d.uuid("requestId"),d.uuid("userId"),null)}
    endpoint("team/leave") {actor,d ->team.member(actor,d.uuid("companyId"),d.uuid("requestId"),actor,null,leave=true)}
    endpoint("team/transfer") {actor,d ->team.propose(actor,d.uuid("companyId"),d.uuid("requestId"),d.uuid("userId"))}
    endpoint("team/decide") {actor,d ->
        val kind=d.text("kind");require(kind in setOf("INVITATION","TRANSFER")){"Tipo de invitación inválido"}
        team.decide(actor,d.uuid("requestId"),d.uuid("id"),kind=="TRANSFER",d.text("decision"))
    }
    endpoint("campaigns/manage") {actor,d ->repository.campaigns(actor,d.uuid("companyId"))}
    endpoint("registration/mine") {actor,_ ->registrations.own(actor)}
    endpoint("registration/submit") {actor,d ->registrations.submit(actor,d.uuid("requestId"),d.text("legalName"),(d["tradeName"] as? String).orEmpty(),d.text("taxId"),(d["websiteUrl"] as? String).orEmpty(),d.text("note"))}
    endpoint("registration/withdraw") {actor,d ->registrations.withdraw(actor,d.uuid("applicationId"))}
    endpoint("registration/pending") {actor,_ ->registrations.pending(actor)}
    endpoint("registration/review") {actor,d ->
        val decision=d.text("decision");require(decision in setOf("APPROVE","REJECT")){"Decisión de revisión inválida"}
        registrations.review(actor,d.uuid("requestId"),d.uuid("applicationId"),decision=="APPROVE",d.text("reason"))
    }
    endpoint("campaigns/save") {actor,d ->repository.saveCampaign(actor,d.uuid("companyId"),d.uuid("requestId"),
        (d["id"] as? String)?.let{UUID.fromString(it).toString()},d.text("title"),d.text("description"),
        d.integer("socialGoal").also{require(it in 0..1_000_000)}.toInt(),d.integer("budgetCents"))}
    endpoint("campaigns/state") {actor,d ->repository.transition(actor,d.uuid("companyId"),d.uuid("requestId"),d.uuid("campaignId"),d.text("status"))}
    endpoint("finance/summary") {actor,d ->repository.finance(actor,d.uuid("companyId"))}
    endpoint("finance/outcomes") {actor,d ->repository.fundingCandidates(actor,d.uuid("companyId"))}
    endpoint("finance/reserve") {actor,d ->repository.reserve(actor,d.uuid("companyId"),d.uuid("requestId"),d.uuid("campaignId"),d.integer("amountCents"))}
    endpoint("finance/release") {actor,d ->repository.reserve(actor,d.uuid("companyId"),d.uuid("requestId"),d.uuid("campaignId"),d.integer("amountCents"),true)}
    endpoint("finance/fund-outcome") {actor,d ->repository.fundOutcome(actor,d.uuid("companyId"),d.uuid("requestId"),d.uuid("campaignId"),d.uuid("outcomeId"),d.integer("amountCents"))}
    endpoint("payments/create") {actor,d ->requireNotNull(payments){"Mercado Pago todavía no está configurado"}.checkout(actor,d.uuid("companyId"),d.uuid("requestId"),d.integer("amountCents"))}
    endpoint("payments/list") {actor,d ->repository.paymentOrders(actor,d.uuid("companyId"))}
    endpoint("promo/list") {actor,d ->repository.companyCoupons(actor,d.uuid("companyId"))}
    endpoint("promo/create") {actor,d ->repository.issueCoupon(actor,d.uuid("companyId"),d.uuid("requestId"),d.uuid("campaignId"),
        d.integer("discountBps").also{require(it in 100..10000)}.toInt(),(d["issuedToUser"] as? String)?.takeIf{it.isNotBlank()},(d["conditionNote"] as? String).orEmpty())}
    endpoint("promo/redeem") {actor,d ->repository.changeCoupon(actor,d.uuid("companyId"),d.uuid("requestId"),d.text("code"),true)}
    endpoint("promo/revoke") {actor,d ->repository.changeCoupon(actor,d.uuid("companyId"),d.uuid("requestId"),d.text("code"),false)}
    // Existing clients retain their endpoint and response shape; unkeyed writes are unsafe.
    endpoint("finance-action") {actor,d ->
        repository.reserve(actor,repository.companyForCampaign(actor,d.uuid("campaignId")),d.uuid("requestId"),d.uuid("campaignId"),d.integer("amountCents"));"ok"
    }
    endpoint("promo/issue") {actor,d ->
        val result=repository.issueCoupon(actor,repository.companyForCampaign(actor,d.uuid("campaignId")),d.uuid("requestId"),d.uuid("campaignId"),
            (d["discountBps"] as? Number)?.toInt() ?:1000,(d["issuedToUser"] as? String)?.takeIf{it.isNotBlank()},(d["conditionNote"] as? String).orEmpty())
        mapOf("code" to result.code)
    }
    post("/api/v1/coupons/mine") {
        val actor=call.principal<AuthPrincipal>() ?: return@post
        call.respond(ApiResponse.ok(repository.ownCoupons(actor.userId)))
    }
    post("/api/v1/coupons/claim") {
        val actor=call.principal<AuthPrincipal>() ?: return@post
        call.respond(ApiResponse.ok(repository.claimCoupon(actor.userId,JsonBody.receiveMap(call).text("code"))))
    }
}

private fun CsrRepository.companyForCampaign(actor:String,campaign:String):String = db.withConnection {c ->
    val company=c.prepareStatement("SELECT company_id::text FROM campaigns WHERE id=?::uuid").use{q ->q.setString(1,campaign);q.executeQuery().use{r ->if(!r.next())throw BusinessAccessDenied();r.getString(1)}}
    access(c,actor,company,campaignRoles+financeRoles);company
}
