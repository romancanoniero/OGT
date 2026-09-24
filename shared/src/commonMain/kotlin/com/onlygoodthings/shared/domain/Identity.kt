package com.onlygoodthings.shared.domain

import kotlinx.serialization.Serializable

@Serializable
enum class UserRole {
    USER,
    COMPANY_ADMIN,
    COMMUNITY_MODERATOR,
}

/** Preferencias de cuenta que la app y la web leen del servidor. */
@Serializable
data class ProfileSettings(
    val language: String = "es",
    val barrio: String? = null,
    val publicProfileVisible: Boolean = true,
    val showExactMatchLocation: Boolean = false,
    val animalAlertPush: Boolean = true,
    val skillAlertPush: Boolean = true,
    val parkingRadarSounds: Boolean = true,
    val radarEnabled: Boolean = true,
    val carbonSaveMode: Boolean = false,
)

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
    val settings: ProfileSettings = ProfileSettings(),
)

/** Nodo vivo `ogt/users/{id}`: nombre, foto y prefs que app y web pintan al toque. */
@Serializable
data class ProfileLive(
    val id: String,
    val displayName: String,
    val photoUrl: String? = null,
    val communityPoints: Int = 0,
    val inviteCode: String? = null,
    val language: String = "es",
    val barrio: String? = null,
    val publicProfileVisible: Boolean = true,
    val showExactMatchLocation: Boolean = false,
    val animalAlertPush: Boolean = true,
    val skillAlertPush: Boolean = true,
    val parkingRadarSounds: Boolean = true,
    val radarEnabled: Boolean = true,
    val carbonSaveMode: Boolean = false,
) {
    fun toSettings(): ProfileSettings = ProfileSettings(
        language = language,
        barrio = barrio,
        publicProfileVisible = publicProfileVisible,
        showExactMatchLocation = showExactMatchLocation,
        animalAlertPush = animalAlertPush,
        skillAlertPush = skillAlertPush,
        parkingRadarSounds = parkingRadarSounds,
        radarEnabled = radarEnabled,
        carbonSaveMode = carbonSaveMode,
    )
}

fun UserProfile.toLive(): ProfileLive = ProfileLive(
    id = id,
    displayName = displayName,
    photoUrl = photoUrl,
    communityPoints = communityPoints,
    inviteCode = inviteCode,
    language = settings.language,
    barrio = settings.barrio,
    publicProfileVisible = settings.publicProfileVisible,
    showExactMatchLocation = settings.showExactMatchLocation,
    animalAlertPush = settings.animalAlertPush,
    skillAlertPush = settings.skillAlertPush,
    parkingRadarSounds = settings.parkingRadarSounds,
    radarEnabled = settings.radarEnabled,
    carbonSaveMode = settings.carbonSaveMode,
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
    val settings: ProfileSettings = ProfileSettings(),
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
