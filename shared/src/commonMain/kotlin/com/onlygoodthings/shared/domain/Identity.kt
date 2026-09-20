package com.onlygoodthings.shared.domain

import kotlinx.serialization.Serializable

@Serializable
enum class UserRole {
    USER,
    COMPANY_ADMIN,
    COMMUNITY_MODERATOR,
}

@Serializable
data class UserProfile(
    val id: String,
    val firebaseUid: String,
    val displayName: String,
    val photoUrl: String?,
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
