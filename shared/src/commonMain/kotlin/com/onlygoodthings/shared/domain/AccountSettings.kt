package com.onlygoodthings.shared.domain

import kotlinx.serialization.Serializable

/** Private account preferences. Never included in public user projections. */
@Serializable
data class AccountSettings(
    val publicProfileVisible: Boolean = true,
    val showExactMatchLocation: Boolean = false,
    val animalAlertPush: Boolean = true,
    val skillAlertPush: Boolean = true,
    val parkingRadarSounds: Boolean = true,
    val radarEnabled: Boolean = true,
    val carbonSaveMode: Boolean = false,
    val quietHoursEnabled: Boolean = true,
    val timeZone: String = "America/Argentina/Buenos_Aires",
    val language: String = "es",
    val barrio: String = "",
)

data class NoticePresentation(val deliver: Boolean, val sound: Boolean)

/** Shared by server delivery and foreground presentation, including overnight boundaries. */
fun AccountSettings.noticePresentation(kind: String, hour: Int): NoticePresentation {
    require(hour in 0..23)
    val normalized = kind.uppercase()
    val animal = normalized in setOf("ALERT", "ANIMAL", "ANIMAL_ALERT", "LOST_PET", "SIGHTING")
    val trade = normalized in setOf("MATCH", "TIMEBANK", "TIMEBANK_MESSAGE", "TRADE", "SKILL")
    val parking = normalized.startsWith("PARKING") || normalized in setOf("SPOT", "RADAR")
    val deliver = (!animal || animalAlertPush) && (!trade || skillAlertPush)
    val quiet = quietHoursEnabled && (hour >= 22 || hour < 7)
    return NoticePresentation(deliver, deliver && !quiet && (!parking || parkingRadarSounds))
}

/** Null means the highest currently defined level has already been reached. */
fun nextCommunityGoal(points: Int): Int? = listOf(500, 1500, 3000).firstOrNull { points < it }
