package com.onlygoodthings.shared.domain

import kotlinx.serialization.Serializable

/** No birth date or identity document is collected. Acceptance belongs to one account. */
const val COMMUNITY_TERMS_VERSION = "2026-11-09"

@Serializable
data class CommunityConsent(
    val version: String = "",
    val adult: Boolean = false,
    val acceptedAtEpochMs: Long = 0L,
) {
    val current: Boolean get() = version == COMMUNITY_TERMS_VERSION && adult && acceptedAtEpochMs > 0
}
