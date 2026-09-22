package com.onlygoodthings.shared.domain

import kotlinx.serialization.Serializable

/** Ficha pública de un vecino. El código de invitación solo viaja si es la propia. */
@Serializable
data class NeighborCard(
    val userId: String,
    val displayName: String,
    val photoUrl: String? = null,
    val communityPoints: Int = 0,
    val postsCount: Int = 0,
    val followerCount: Int = 0,
    val followingCount: Int = 0,
    val viewerFollows: Boolean = false,
    val inviteCode: String? = null,
    val role: String = "USER",
    val levelLabel: String = "",
)

@Serializable
data class SearchPage(
    val posts: List<SocialPost> = emptyList(),
    val people: List<NeighborCard> = emptyList(),
)

@Serializable
data class CommunityNotice(
    val id: String,
    val kind: String,
    val title: String,
    val body: String,
    val postId: String? = null,
    val matchId: String? = null,
    val listingId: String? = null,
    val createdAtEpochMs: Long = 0L,
    val read: Boolean = false,
)

@Serializable
data class WalletActivity(
    val id: String,
    val title: String,
    val delta: Int,
    val createdAtEpochMs: Long,
)

@Serializable
data class WalletSummary(
    val points: Int,
    val nextGoal: Int,
    val levelLabel: String,
    val missing: Int,
    val activity: List<WalletActivity> = emptyList(),
    val ranking: List<NeighborCard> = emptyList(),
)

@Serializable
data class TimebankMatchHit(
    val userId: String,
    val displayName: String,
    val tag: String,
    val chatEnabledHint: String = "",
)

@Serializable
data class SkillTagDto(
    val slug: String,
    val label: String,
    val offered: Boolean = false,
    val requested: Boolean = false,
)

@Serializable
data class TimebankThread(
    val matchId: String,
    val peerUserId: String,
    val peerName: String,
    val tag: String,
    val tagLabel: String,
    val lastBody: String = "",
    val lastAtEpochMs: Long = 0L,
    val status: String = "ACTIVE",
)

@Serializable
data class TimebankMessageDto(
    val id: String,
    val matchId: String,
    val senderId: String,
    val senderName: String,
    val body: String,
    val createdAtEpochMs: Long,
    val mine: Boolean = false,
)

fun communityLevelLabel(points: Int): String = when {
    points >= 3000 -> "Héroe de la comunidad"
    points >= 1500 -> "Referente"
    points >= 500 -> "Buena vecindad"
    else -> "Vecino"
}
