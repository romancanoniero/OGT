package com.onlygoodthings.backend.business

import kotlinx.serialization.Serializable

@Serializable
data class CsrCampaign(val id:String,val companyId:String,val title:String,val description:String,val status:String,
    val socialGoal:Int,val socialProgress:Int,val budgetCents:Long,val reservedCents:Long,val spentCents:Long)
@Serializable
data class CsrActionResult(val id:String,val status:String?=null,val code:String?=null,val amountCents:Long?=null,val checkoutUrl:String?=null,val liveMode:Boolean?=null)
typealias CsrCoupon=com.onlygoodthings.shared.domain.BusinessCoupon
@Serializable
data class CsrLedgerEntry(val id:String,val kind:String,val availableDeltaCents:Long,val reservedDeltaCents:Long,
    val spentDeltaCents:Long,val debtDeltaCents:Long,val note:String,val createdAtEpochMs:Long)
@Serializable
data class CsrFinanceSummary(val companyId:String,val availableCents:Long,val reservedCents:Long,val spentCents:Long,
    val debtCents:Long,val reconciled:Boolean,val entries:List<CsrLedgerEntry>,val impactScore:Double=0.0,val unresolvedPaymentIncidents:Int=0)

@Serializable
data class CsrFundingCandidate(val id:String,val kind:String,val actorName:String,val verifiedAtEpochMs:Long)

internal val campaignTransitions=mapOf(
    "DRAFT" to setOf("ACTIVE","CANCELLED"),
    "ACTIVE" to setOf("PAUSED","COMPLETED","CANCELLED"),
    "PAUSED" to setOf("ACTIVE","COMPLETED","CANCELLED"),
    "COMPLETED" to emptySet(),"CANCELLED" to emptySet(),
)
internal fun validateCampaignTransition(from:String,to:String) {
    require(from==to || to in campaignTransitions[from].orEmpty()) { "Ese cambio de estado de campaña no está permitido" }
}
