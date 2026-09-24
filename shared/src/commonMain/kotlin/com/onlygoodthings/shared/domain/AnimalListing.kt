package com.onlygoodthings.shared.domain

import kotlinx.serialization.Serializable

/** Ficha de mascota persistida en Postgres, con el post del feed. */
@Serializable
data class AnimalListingDto(
    val listingId: String,
    val postId: String,
    val reporterUserId: String,
    val reporterFirebaseUid: String,
    val kind: String,
    val species: String,
    val size: String,
    val urgency: String,
    val title: String,
    val description: String,
    val place: String,
    val alertRadiusM: Int,
    val resolved: Boolean,
    val petName: String = "",
    val ageLabel: String = "",
    val sex: String = "",
    val temperament: String = "",
    val vaccinated: Boolean = false,
    val sterilized: Boolean = false,
    val homeNeeds: String = "",
    val marks: String = "",
    val lastSeenPlace: String = "",
    val latitude: Double? = null,
    val longitude: Double? = null,
    val createdAtEpochMs: Long = 0L,
    val media: List<PostMediaItem> = emptyList(),
)

@Serializable
data class AnimalResolveResult(
    val listingId: String,
    val verifiedOutcome: Boolean,
    val message: String? = null,
)
