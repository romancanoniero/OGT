package com.onlygoodthings.app.platform

import com.onlygoodthings.shared.domain.MediaKind

data class PickedVideoInfo(val durationMs: Long, val hasAudio: Boolean)

expect suspend fun inspectPickedVideo(path: String): PickedVideoInfo
internal expect suspend fun removePickedVideoAudio(item: OgtPickedMedia): String

/** Keep the prepared audible segment, so restoring sound never restores an old trim. */
internal suspend fun changeVideoSound(
    item: OgtPickedMedia,
    muted: Boolean,
    inspect: suspend (String) -> PickedVideoInfo = ::inspectPickedVideo,
    removeAudio: suspend (OgtPickedMedia) -> String = ::removePickedVideoAudio,
): OgtPickedMedia {
    require(item.kind == MediaKind.VIDEO && item.fromDevice && item.preparedForUpload) { "Editá y prepará el video primero." }
    if (item.audioMuted == muted) return item
    if (!muted) {
        val audible = item.unmutedPath ?: error("No se encuentra la copia con sonido. Volvé a editar el video.")
        inspect(audible)
        return item.copy(path = audible, audioMuted = false, unmutedPath = null)
    }
    val silent = removeAudio(item)
    val info = inspect(silent)
    require(!info.hasAudio) { "No se pudo quitar el sonido del video." }
    return item.copy(path = silent, audioMuted = true, unmutedPath = item.path)
}

internal fun videoDurationLabel(durationMs: Long): String {
    val tenths = (durationMs.coerceAtLeast(0) + 50) / 100
    val seconds = tenths / 10
    val fraction = tenths % 10
    return "${seconds / 60}:${(seconds % 60).toString().padStart(2, '0')}" + if (fraction == 0L) "" else ",$fraction"
}
