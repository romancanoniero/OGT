package com.onlygoodthings.shared.domain

import kotlinx.datetime.DatePeriod
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime

private val MonthsEs = listOf(
    "ene", "feb", "mar", "abr", "may", "jun",
    "jul", "ago", "sep", "oct", "nov", "dic",
)

private val WeekdaysEs = listOf(
    "Lunes", "Martes", "Miércoles", "Jueves", "Viernes", "Sábado", "Domingo",
)

/** Fecha y hora de la convocatoria, en lenguaje cercano (Hoy, Mañana, Sábado). */
fun formatGatheringWhen(
    eventStartsAtEpochMs: Long,
    nowEpochMs: Long,
    timeZone: TimeZone = TimeZone.currentSystemDefault(),
): String {
    val start = Instant.fromEpochMilliseconds(eventStartsAtEpochMs).toLocalDateTime(timeZone)
    val today = Instant.fromEpochMilliseconds(nowEpochMs).toLocalDateTime(timeZone).date
    val date = start.date
    val clock = "${start.hour.toString().padStart(2, '0')}:${start.minute.toString().padStart(2, '0')}"
    val delta = date.toEpochDays() - today.toEpochDays()
    val day = when (delta) {
        0 -> "Hoy"
        1 -> "Mañana"
        -1 -> "Ayer"
        in 2..6, in -6..-2 -> WeekdaysEs[date.dayOfWeek.ordinal]
        else -> "${date.dayOfMonth} ${MonthsEs[date.monthNumber - 1]}"
    }
    return "$day $clock"
}

/** Línea para anotarse: cuándo · dónde. */
fun gatheringWhenWhere(
    place: String,
    eventStartsAtEpochMs: Long?,
    nowEpochMs: Long,
    timeZone: TimeZone = TimeZone.currentSystemDefault(),
): String? {
    val whenLabel = eventStartsAtEpochMs?.let { formatGatheringWhen(it, nowEpochMs, timeZone) }
    val where = place.trim().takeIf { it.isNotBlank() }
    if (whenLabel == null && where == null) return null
    return listOfNotNull(whenLabel, where).joinToString(" · ")
}
