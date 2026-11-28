package com.onlygoodthings.shared.domain

import com.onlygoodthings.shared.data.local.LocalAnimalListing
import com.onlygoodthings.shared.data.local.LocalSighting
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime

/** Origin, grouped day, or individual observation in the notice timeline. */
data class LostTimelineStop(
    val dayStartEpochMs: Long,
    val dayEndEpochMs: Long,
    val label: String,
    val isOrigin: Boolean = false,
    val sightingId: String? = null,
)

private val MonthsEs = listOf(
    "ene", "feb", "mar", "abr", "may", "jun",
    "jul", "ago", "sep", "oct", "nov", "dic",
)

/** Historical reports retain their creation time as a clearly labeled fallback. */
fun sightingEventEpochMs(sighting: LocalSighting): Long =
    sighting.observedAtEpochMs?.takeIf { it > 0 } ?: sighting.createdAtEpochMs

val sightingChronology: Comparator<LocalSighting> =
    compareBy<LocalSighting> { sightingEventEpochMs(it) }.thenBy { it.createdAtEpochMs }.thenBy { it.id }

fun lostTimelineStops(
    listing: LocalAnimalListing,
    sightings: List<LocalSighting>,
    nowEpochMs: Long,
    timeZone: TimeZone = TimeZone.currentSystemDefault(),
): List<LostTimelineStop> {
    val originMs = listing.lastSeenAtEpochMs.takeIf { it > 0 }
        ?: sightings.minOfOrNull { sightingEventEpochMs(it) }
        ?: nowEpochMs
    val origin = LostTimelineStop(
        dayStartEpochMs = dayStartMs(originMs, timeZone),
        dayEndEpochMs = originMs,
        label = if (listing.kind == "FOUND") "Encuentro" else "Pérdida",
        isOrigin = true,
    )
    val days = sightings.map { dayStartMs(sightingEventEpochMs(it), timeZone) }.distinct().sorted()
    return listOf(origin) + days.map { start ->
        LostTimelineStop(
            dayStartEpochMs = start,
            dayEndEpochMs = dayEndMs(start, timeZone),
            label = formatDayLabel(start, nowEpochMs, timeZone),
        )
    }
}

/** The expanded map advances one observation at a time, including same-day reports. */
fun lostTimelineEvents(
    listing: LocalAnimalListing,
    sightings: List<LocalSighting>,
    nowEpochMs: Long,
    timeZone: TimeZone = TimeZone.currentSystemDefault(),
): List<LostTimelineStop> = listOf(lostTimelineStops(listing, sightings, nowEpochMs, timeZone).first()) +
    sightings.sortedWith(sightingChronology).map { row ->
        val at = sightingEventEpochMs(row)
        val local = Instant.fromEpochMilliseconds(at).toLocalDateTime(timeZone)
        val year = Instant.fromEpochMilliseconds(nowEpochMs).toLocalDateTime(timeZone).year
        val day = formatDayLabel(at, nowEpochMs, timeZone) + if (local.year != year) " ${local.year}" else ""
        val time = "${local.hour.toString().padStart(2, '0')}:${local.minute.toString().padStart(2, '0')}"
        LostTimelineStop(dayStartMs(at, timeZone), at,
            "$day\n${if (row.observedAtEpochMs == null) "Carga " else ""}$time", sightingId = row.id)
    }

data class LostTimelineFrame(
    val markers: List<LocalSighting>,
    val routeSightings: List<LocalSighting>,
    val selectedSightingId: String?,
)

/** Selection changes emphasis and the historical route, never marker visibility. */
fun lostTimelineFrame(sightings: List<LocalSighting>, stop: LostTimelineStop): LostTimelineFrame =
    LostTimelineFrame(sightings.sortedWith(sightingChronology), sightingsAtTimelineStop(sightings, stop),
        stop.sightingId?.takeIf { id -> sightings.any { it.id == id } })

fun sightingsUntil(sightings: List<LocalSighting>, untilEpochMs: Long): List<LocalSighting> =
    sightings.filter { sightingEventEpochMs(it) <= untilEpochMs }.sortedWith(sightingChronology)

/** El origen no incluye avistamientos, aunque hayan ocurrido el mismo día. */
fun sightingsAtTimelineStop(sightings: List<LocalSighting>, stop: LostTimelineStop): List<LocalSighting> {
    if (stop.isOrigin) return emptyList()
    if (stop.sightingId == null) return sightingsUntil(sightings, stop.dayEndEpochMs)
    val ordered = sightings.sortedWith(sightingChronology)
    val index = ordered.indexOfFirst { it.id == stop.sightingId }
    return if (index >= 0) ordered.take(index + 1) else emptyList()
}

/** Observation time when supplied, otherwise the stored report time. UI labels the fallback. */
fun sightingMomentLabel(sighting: LocalSighting, nowEpochMs: Long, timeZone: TimeZone = TimeZone.currentSystemDefault()): String? {
    return sightingMomentLabelAt(sightingEventEpochMs(sighting), nowEpochMs, timeZone)
}

fun sightingReportMomentLabel(sighting: LocalSighting, nowEpochMs: Long, timeZone: TimeZone = TimeZone.currentSystemDefault()): String? =
    sightingMomentLabelAt(sighting.createdAtEpochMs, nowEpochMs, timeZone)

fun sightingMomentLabelAt(epochMs: Long, nowEpochMs: Long, timeZone: TimeZone = TimeZone.currentSystemDefault()): String? {
    if (epochMs <= 0) return null
    val date = Instant.fromEpochMilliseconds(epochMs).toLocalDateTime(timeZone)
    val year = Instant.fromEpochMilliseconds(nowEpochMs).toLocalDateTime(timeZone).year
    val yearLabel = if (date.year == year) "" else " ${date.year}"
    return "${date.dayOfMonth} ${MonthsEs[date.monthNumber - 1]}$yearLabel · ${date.hour.toString().padStart(2, '0')}:${date.minute.toString().padStart(2, '0')}"
}

fun movementRoute(
    listing: LocalAnimalListing,
    visible: List<LocalSighting>,
): List<GeoPoint> {
    val originLat = listing.latitude ?: return emptyList()
    val originLng = listing.longitude ?: return emptyList()
    return buildList {
        add(GeoPoint(originLat, originLng))
        visible.sortedWith(sightingChronology).forEach { add(GeoPoint(it.latitude, it.longitude)) }
    }
}

/** Próximo avistaje oculto: rumbo que habría seguido a partir del día marcado. */
fun headingAfter(
    all: List<LocalSighting>,
    visible: List<LocalSighting>,
): GeoPoint? {
    val last = visible.maxWithOrNull(sightingChronology) ?: return all.minWithOrNull(sightingChronology)?.let {
        GeoPoint(it.latitude, it.longitude)
    }
    val next = all.filter { sightingChronology.compare(it,last)>0 }.minWithOrNull(sightingChronology)
    return next?.let { GeoPoint(it.latitude, it.longitude) }
}

internal fun dayStartMs(epochMs: Long, timeZone: TimeZone): Long {
    val date = Instant.fromEpochMilliseconds(epochMs).toLocalDateTime(timeZone).date
    return date.atStartOfDayIn(timeZone).toEpochMilliseconds()
}

private fun dayEndMs(dayStartEpochMs: Long, timeZone: TimeZone): Long {
    val date = Instant.fromEpochMilliseconds(dayStartEpochMs).toLocalDateTime(timeZone).date
    return date.plus(DatePeriod(days = 1)).atStartOfDayIn(timeZone).toEpochMilliseconds() - 1
}

private fun formatDayLabel(dayStartEpochMs: Long, nowEpochMs: Long, timeZone: TimeZone): String {
    val date = Instant.fromEpochMilliseconds(dayStartEpochMs).toLocalDateTime(timeZone).date
    val today = Instant.fromEpochMilliseconds(nowEpochMs).toLocalDateTime(timeZone).date
    return when (date) {
        today -> "Hoy"
        today.minus(DatePeriod(days = 1)) -> "Ayer"
        else -> "${date.dayOfMonth} ${MonthsEs[date.monthNumber - 1]}"
    }
}
