package com.onlygoodthings.shared.domain

/** Etiqueta corta para el encabezado del post. */
fun relativeTimeLabel(createdAtEpochMs: Long, nowEpochMs: Long): String {
    if (createdAtEpochMs <= 0L) return ""
    val delta = (nowEpochMs - createdAtEpochMs).coerceAtLeast(0L)
    val minutes = delta / 60_000L
    val hours = delta / 3_600_000L
    val days = delta / 86_400_000L
    return when {
        minutes < 1L -> "Ahora"
        minutes < 60L -> "Hace $minutes min"
        hours < 24L -> "Hace $hours h"
        hours < 48L -> "Ayer"
        days < 14L -> "Hace $days d"
        else -> "Hace ${days / 7L} sem"
    }
}

/** Lugar y cuándo, sin puntos sueltos si falta uno. */
fun postMetaLine(
    place: String,
    timeLabel: String,
    createdAtEpochMs: Long,
    nowEpochMs: Long,
): String {
    val whenText = timeLabel.trim().ifBlank { relativeTimeLabel(createdAtEpochMs, nowEpochMs) }
    return listOfNotNull(
        place.trim().takeIf { it.isNotEmpty() },
        whenText.takeIf { it.isNotEmpty() },
    ).joinToString(" · ")
}
