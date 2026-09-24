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
    val tagLabel: String = "",
    val chatEnabledHint: String = "",
)

/** Vecino que apoya un pedido de trueque. */
@Serializable
data class TimebankNeedSupport(
    val userId: String,
    val displayName: String,
    val photoUrl: String? = null,
)

/** Pedido de trueque: se ve como un post, sin exigir foto. */
@Serializable
data class TimebankNeedPost(
    val needId: String,
    val postId: String? = null,
    val userId: String,
    val displayName: String,
    val photoUrl: String? = null,
    val tag: String,
    val tagLabel: String,
    val giveLabel: String? = null,
    val giveLabels: List<String> = emptyList(),
    val body: String,
    val note: String? = null,
    val mine: Boolean = false,
    val matchesMyOffer: Boolean = false,
    val supporters: List<TimebankNeedSupport> = emptyList(),
    val supportCount: Int = 0,
    val viewerSupported: Boolean = false,
    val viewerInvited: Boolean = false,
    val createdAtEpochMs: Long = 0L,
)

@Serializable
data class TimebankBoard(
    val tags: List<SkillTagDto> = emptyList(),
    val mine: List<TimebankNeedPost> = emptyList(),
    val seekingMine: List<TimebankNeedPost> = emptyList(),
    val others: List<TimebankNeedPost> = emptyList(),
    val helpers: List<TimebankMatchHit> = emptyList(),
)

@Serializable
data class SkillTagDto(
    val slug: String,
    val label: String,
    val offered: Boolean = false,
    val requested: Boolean = false,
)

/** Sugerencia al pedir o ofrecer un oficio. */
@Serializable
data class SkillSuggestHit(
    val slug: String? = null,
    val label: String,
    val offeredCount: Int = 0,
    val mineOffered: Boolean = false,
    val canonical: Boolean = false,
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
