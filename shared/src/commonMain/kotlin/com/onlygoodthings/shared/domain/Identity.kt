package com.onlygoodthings.shared.domain

import kotlinx.serialization.Serializable

@Serializable
enum class UserRole {
    USER,
    COMPANY_ADMIN,
    COMMUNITY_MODERATOR,
}

@Serializable
data class AuthMe(
    val userId: String,
    val firebaseUid: String,
    val role: String,
    val email: String? = null,
    val displayName: String,
    val photoUrl: String? = null,
    val communityPoints: Int = 0,
    val inviteCode: String? = null,
    val levelLabel: String = "",
)

@Serializable
data class UserProfile(
    val id: String,
    val firebaseUid: String,
    val displayName: String,
    val photoUrl: String? = null,
    val role: UserRole,
    val communityPoints: Int,
    val inviteCode: String,
)

@Serializable
data class CompanyProfile(
    val id: String,
    val legalName: String,
    val tradeName: String?,
    val verified: Boolean,
    val campaignBalanceCents: Long,
    val impactScore: Double,
)

/** Ficha de la empresa del admin logueado. */
@Serializable
data class CompanyDesk(
    val id: String,
    val legalName: String,
    val tradeName: String? = null,
    val verified: Boolean = false,
    val campaignBalanceCents: Long = 0,
    val impactScore: Double = 0.0,
    val logoUrl: String? = null,
    val taxId: String? = null,
)

/** Campaña RSE para el escritorio de empresa. */
@Serializable
data class CampaignDesk(
    val id: String,
    val title: String,
    val status: String,
    val socialGoal: Int = 0,
    val socialProgress: Int = 0,
    val budgetCents: Long = 0,
    val spentCents: Long = 0,
)

@Serializable
data class PromoIssued(
    val code: String,
)
