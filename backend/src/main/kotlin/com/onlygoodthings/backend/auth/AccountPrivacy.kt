package com.onlygoodthings.backend.auth

import kotlinx.serialization.Serializable

@Serializable
data class AccountTextRow(
    val id: String,
    val body: String,
    val createdAt: String,
)

/** Exportación de la cuenta que llama. Sin datos de terceros. */
@Serializable
data class AccountExport(
    val userId: String,
    val email: String? = null,
    val phone: String? = null,
    val displayName: String,
    val communityPoints: Int,
    val posts: List<AccountTextRow> = emptyList(),
    val comments: List<AccountTextRow> = emptyList(),
    val messagesSent: List<AccountTextRow> = emptyList(),
    val blockedUsers:List<com.onlygoodthings.shared.domain.BlockedUser> = emptyList(),
    val coupons:List<com.onlygoodthings.shared.domain.BusinessCoupon> = emptyList(),
    val companyTeam:List<com.onlygoodthings.backend.business.CsrPersonalTeamRecord> = emptyList(),
    val companyApplications:List<com.onlygoodthings.backend.business.CsrRegistration> = emptyList(),
)
