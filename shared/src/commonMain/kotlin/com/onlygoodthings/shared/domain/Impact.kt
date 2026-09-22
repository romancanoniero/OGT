package com.onlygoodthings.shared.domain

import kotlinx.serialization.Serializable

/** Cómo aparece un post en el feed. La plata nunca entra al score orgánico. */
@Serializable
enum class PostPlacement {
    ORGANIC,
    PROMOTED,
    AD,
}

@Serializable
enum class ActionOutcomeKind {
    PARKING_HANDOFF,
    ANIMAL_RESOLVED,
    VOLUNTEER_CHECKED_IN,
    TIMEBANK_CLOSED,
    CAUSE_DELIVERED,
}

@Serializable
enum class VerificationMethod {
    GEO,
    WITNESS,
    ORGANIZER,
    PAYMENT_SETTLED,
    SERVER,
}

/** Hecho concretado y verificado. Es la única fuente de puntos y de “se logró”. */
@Serializable
data class ActionOutcome(
    val id: String,
    val kind: ActionOutcomeKind,
    val verificationMethod: VerificationMethod,
    val actorUserId: String,
    val beneficiaryUserId: String? = null,
    val postId: String? = null,
    val companyId: String? = null,
    val campaignId: String? = null,
    val pointsAwarded: Int,
    val verifiedAtEpochMs: Long,
)

/** Métricas públicas de una empresa que financió hechos, no avisos. */
@Serializable
data class CompanyImpactCard(
    val companyId: String,
    val tradeName: String,
    val verifiedOutcomes: Int,
    val impactScore: Double,
    val empresaQueSuma: Boolean,
)

@Serializable
enum class MoneyLedgerKind {
    PROMOTED_REACH,
    AD_IMPRESSION,
    ACTION_FINANCE,
}
