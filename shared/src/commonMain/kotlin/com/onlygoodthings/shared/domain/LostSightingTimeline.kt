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

/** Un día de la línea de tiempo del aviso. */
data class LostTimelineStop(
    val dayStartEpochMs: Long,
    val dayEndEpochMs: Long,
    val label: String,
)

private val MonthsEs = listOf(
    "ene", "feb", "mar", "abr", "may", "jun",
    "jul", "ago", "sep", "oct", "nov", "dic",
)

fun lostTimelineStops(
    listing: LocalAnimalListing,
    sightings: List<LocalSighting>,
    nowEpochMs: Long,
    timeZone: TimeZone = TimeZone.currentSystemDefault(),
): List<LostTimelineStop> {
    val originMs = listing.lastSeenAtEpochMs.takeIf { it > 0 }
        ?: sightings.minOfOrNull { it.createdAtEpochMs }
        ?: nowEpochMs
    val days = buildSet {
        add(dayStartMs(originMs, timeZone))
        sightings.forEach { add(dayStartMs(it.createdAtEpochMs, timeZone)) }
    }.sorted()
    return days.map { start ->
        LostTimelineStop(
            dayStartEpochMs = start,
            dayEndEpochMs = dayEndMs(start, timeZone),
            label = formatDayLabel(start, nowEpochMs, timeZone),
        )
    }
}

fun sightingsUntil(sightings: List<LocalSighting>, untilEpochMs: Long): List<LocalSighting> =
    sightings.filter { it.createdAtEpochMs <= untilEpochMs }.sortedBy { it.createdAtEpochMs }

fun movementRoute(
    listing: LocalAnimalListing,
    visible: List<LocalSighting>,
): List<GeoPoint> {
    val originLat = listing.latitude ?: return emptyList()
    val originLng = listing.longitude ?: return emptyList()
    return buildList {
        add(GeoPoint(originLat, originLng))
        visible.forEach { add(GeoPoint(it.latitude, it.longitude)) }
    }
}

/** Próximo avistaje oculto: rumbo que habría seguido a partir del día marcado. */
fun headingAfter(
    all: List<LocalSighting>,
    visible: List<LocalSighting>,
): GeoPoint? {
    val last = visible.lastOrNull() ?: return all.minByOrNull { it.createdAtEpochMs }?.let {
        GeoPoint(it.latitude, it.longitude)
    }
    val next = all.filter { it.createdAtEpochMs > last.createdAtEpochMs }.minByOrNull { it.createdAtEpochMs }
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
