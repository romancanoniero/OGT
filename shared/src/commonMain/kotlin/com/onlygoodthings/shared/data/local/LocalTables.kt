package com.onlygoodthings.shared.data.local

import com.onlygoodthings.shared.domain.AuthorKind
import com.onlygoodthings.shared.domain.FeedEventKind
import com.onlygoodthings.shared.domain.HonorChannel
import com.onlygoodthings.shared.domain.HonorStatus
import com.onlygoodthings.shared.domain.MediaKind
import com.onlygoodthings.shared.domain.ParkingStatus
import com.onlygoodthings.shared.domain.PostPersonRole
import com.onlygoodthings.shared.domain.UserRole
import kotlinx.serialization.Serializable

/**
 * Filas locales que espejan `database/migrations/01_init.sql`.
 * Sin PostGIS: lat/lng en Double. Sustituible luego por SQLDelight o REST.
 */
@Serializable
data class LocalUser(
    val id: String,
    val firebaseUid: String,
    val email: String?,
    val phoneE164: String?,
    val displayName: String,
    val photoUrl: String?,
    val role: UserRole,
    val communityPoints: Int,
    val inviteCode: String,
    /** Zona o comunidad local de referencia; la app no se limita a un barrio. */
    val barrio: String,
    val levelLabel: String,
    val honorTag: String,
    val latitude: Double,
    val longitude: Double,
) {
    /** Id local, UID de Firebase u otro alias con el que se grabó una ficha. */
    fun aliases(): Set<String> = setOf(id, firebaseUid).filter { it.isNotBlank() }.toSet()

    fun owns(otherId: String?): Boolean = otherId != null && otherId in aliases()
}

@Serializable
data class LocalCompany(
    val id: String,
    val legalName: String,
    val tradeName: String?,
    val verified: Boolean,
    val campaignBalanceCents: Long,
    val impactScore: Double,
)

@Serializable
data class LocalParkingSpot(
    val id: String,
    val ownerUserId: String,
    val claimedByUserId: String?,
    val latitude: Double,
    val longitude: Double,
    val status: ParkingStatus,
    val version: Int,
    val address: String,
    val vehicleLabel: String,
    val etaSeconds: Int?,
    val distanceMeters: Double?,
    val rewardPoints: Int,
    val notes: String?,
    val expiresAtEpochMs: Long = 0L,
    val ownerLastLat: Double? = null,
    val ownerLastLng: Double? = null,
    val claimantLastLat: Double? = null,
    val claimantLastLng: Double? = null,
    val ownerEtaSeconds: Int? = null,
    val interestClosesAtEpochMs: Long = 0L,
    val ownerWaitDeadlineAtEpochMs: Long = 0L,
    /** Las plazas seed ya cerraron la ventana: FCFS leftover. */
    val leftoverOpen: Boolean = true,
    val matchingResolved: Boolean = true,
)

@Serializable
/** Interés de un buscador durante la ventana. El sorteo vive en [com.onlygoodthings.shared.domain.ParkingMatch]. */
data class LocalParkingInterest(
    val spotId: String,
    val userId: String,
    val etaSeconds: Int,
    val communityPoints: Int,
    val expressedAtEpochMs: Long,
)

data class LocalParkingHandoff(
    val id: String,
    val parkingSpotId: String,
    val ownerUserId: String,
    val claimantUserId: String,
    val proximityMeters: Double,
    val verified: Boolean,
    val pointsAwarded: Int,
    val completedAtEpochMs: Long,
)

@Serializable
data class LocalSocialPost(
    val id: String,
    val authorKind: AuthorKind,
    val authorUserId: String?,
    val authorCompanyId: String?,
    val place: String,
    val timeLabel: String,
    val tag: String,
    val body: String,
    val impactCount: Int,
    val commentCount: Int,
    val isStory: Boolean,
    val storyLabel: String?,
    val createdAtEpochMs: Long = 0L,
    val sourceUrl: String? = null,
    /** Si apunta a un aviso vivo, el feed usa la card operativa (perdido / adopción). */
    val listingId: String? = null,
    /** Inicio real de la convocatoria. Sin esto, o si ya pasó, no hay Asistiré. */
    val eventStartsAtEpochMs: Long? = null,
    /** Nombre del homenajeado cuando no es usuaria ni invitación. */
    val honoreeName: String? = null,
    /** Corazones en un homenaje. El aplauso sigue en [impactCount]. */
    val heartCount: Int = 0,
)

@Serializable
data class LocalPostMedia(
    val id: String,
    val postId: String,
    val kind: MediaKind,
    val url: String,
    val posterUrl: String? = null,
    val sortOrder: Int = 0,
    val assetKey: String,
    val altText: String? = null,
    val durationMs: Int? = null,
)

@Serializable
data class LocalFollow(
    val followerId: String,
    val followedId: String,
)

@Serializable
data class LocalPostPerson(
    val postId: String,
    val userId: String,
    val role: PostPersonRole,
)

/**
 * Mención de honor: alguien que aún no es usuaria.
 * No crea `users` fantasma; el crédito se reivindica con [claimToken].
 */
@Serializable
data class LocalHonorMention(
    val id: String,
    val issuerUserId: String,
    val givenName: String,
    val channel: HonorChannel,
    val contact: String,
    val claimToken: String,
    val status: HonorStatus = HonorStatus.PENDING,
    val claimedUserId: String? = null,
    val postId: String? = null,
    val role: PostPersonRole? = null,
    val createdAtEpochMs: Long,
    val claimedAtEpochMs: Long = 0L,
    val issuerName: String? = null,
)

@Serializable
data class LocalFeedEvent(
    val id: String,
    val viewerId: String,
    val postId: String,
    val kind: FeedEventKind,
    val createdAtEpochMs: Long,
    val dwellMs: Int? = null,
)

@Serializable
data class LocalComment(
    val id: String,
    val postId: String,
    val authorUserId: String,
    val body: String,
    val timeLabel: String,
    val parentCommentId: String?,
    val clapCount: Int = 0,
    val clapUserIds: List<String> = emptyList(),
)

@Serializable
data class LocalAdoptRequest(
    val id: String,
    val postId: String,
    val listingId: String?,
    val applicantUserId: String,
    val applicantName: String,
    val phone: String,
    val address: String,
    val homeKind: String,
    val otherPets: String,
    val otherSpecies: String,
    val household: String,
    val hoursAway: String,
    val experience: String,
    val motive: String,
    val matchId: String,
    val createdAtEpochMs: Long,
)

@Serializable
data class LocalAnimalListing(
    val id: String,
    val reporterUserId: String,
    val kind: String,
    val species: String,
    val size: String?,
    val urgency: String,
    val title: String,
    val description: String,
    val place: String,
    val alertRadiusM: Int,
    val neighborsAlerted: Int,
    val resolved: Boolean,
    val petName: String = "",
    val marks: String = "",
    val lastSeenPlace: String = "",
    val latitude: Double? = null,
    val longitude: Double? = null,
    val lastSeenAtEpochMs: Long = 0L,
    /** Edad en texto libre para la ficha de adopción (`6 meses`). */
    val ageLabel: String = "",
    /** `HEMBRA` / `MACHO`. */
    val sex: String = "",
    val temperament: String = "",
    val vaccinated: Boolean = false,
    val sterilized: Boolean = false,
    /** Qué necesita el hogar (patio, sin niñes, otra mascota, etc.). */
    val homeNeeds: String = "",
    val createdAtEpochMs: Long = 0L,
) {
    /** Nombre para título de ficha: mascota, no el lema del aviso. */
    fun displayPetName(): String =
        petName.ifBlank { title.substringBefore("·").trim() }.ifBlank { title }
}

@Serializable
data class PublishedAnimalsSnapshot(
    val listings: List<LocalAnimalListing> = emptyList(),
    val posts: List<LocalSocialPost> = emptyList(),
    val media: List<LocalPostMedia> = emptyList(),
)

@Serializable
data class LocalSighting(
    val id: String,
    val listingId: String,
    val postId: String,
    val userId: String,
    val note: String,
    val latitude: Double,
    val longitude: Double,
    val status: String,
    val matchId: String,
    val timeLabel: String,
    val createdAtEpochMs: Long,
) {
    /** Dejó un mensaje además de marcar que lo vio. */
    fun wroteNote(): Boolean = note.isNotBlank()
}

@Serializable
data class LocalCause(
    val id: String,
    val organizerUserId: String?,
    val title: String,
    val description: String,
    val goalCents: Long,
    val raisedCents: Long,
    val donors: Int,
    val verified: Boolean,
)

@Serializable
data class LocalSkillTag(
    val id: String,
    val slug: String,
    val label: String,
)

@Serializable
data class LocalUserSkill(
    val userId: String,
    val tagId: String,
    val offered: Boolean,
    val requested: Boolean,
)

@Serializable
data class LocalHelpExchange(
    val id: String,
    val authorUserId: String,
    val need: String,
    val give: String,
    val note: String? = null,
    val createdAtEpochMs: Long = 0L,
)

@Serializable
data class LocalTimebankMatch(
    val id: String,
    val requesterId: String,
    val providerId: String,
    val offeredLabel: String,
    val requestedLabel: String,
    val matchPercent: Int,
    val distanceLabel: String,
    val quote: String?,
    val chatEnabled: Boolean,
)

@Serializable
data class LocalMessage(
    val id: String,
    val matchId: String,
    val senderId: String,
    val body: String,
    val timeLabel: String,
    val adoptRequestId: String? = null,
    val sightingId: String? = null,
)

@Serializable
data class LocalCampaign(
    val id: String,
    val companyId: String,
    val title: String,
    val description: String,
    val socialGoal: Int,
    val socialProgress: Int,
)

@Serializable
data class LocalNotification(
    val id: String,
    val kind: String,
    val title: String,
    val body: String,
    val timeLabel: String,
    val urgent: Boolean,
    val recipientUserId: String? = null,
    val postId: String? = null,
    val deepLink: String? = null,
    val read: Boolean = false,
    val createdAtEpochMs: Long = 0L,
)

@Serializable
data class LocalKarmaEntry(
    val id: String,
    val userId: String,
    val title: String,
    val place: String,
    val delta: Int,
    val timeLabel: String,
)

@Serializable
data class LocalReward(
    val id: String,
    val badge: String,
    val costPoints: Int,
    val place: String,
    val detail: String,
)

@Serializable
data class LocalMapPin(
    val id: String,
    val kind: String,
    val title: String,
    val subtitle: String,
    val latitude: Double,
    val longitude: Double,
)

@Serializable
data class LocalStory(
    val id: String,
    val label: String,
)

@Serializable
data class LocalInboxThread(
    val id: String,
    val peerUserId: String?,
    val title: String,
    val tag: String,
    val preview: String,
    val timeLabel: String,
    val matchId: String?,
    val group: Boolean,
)

@Serializable
data class LocalInviteContact(
    val name: String,
    val detail: String,
)

@Serializable
data class LocalSponsor(
    val id: String,
    val badge: String?,
    val company: String,
    val title: String?,
    val body: String,
    val cta: String?,
)
