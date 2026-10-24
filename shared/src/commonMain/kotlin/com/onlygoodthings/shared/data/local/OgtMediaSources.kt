package com.onlygoodthings.shared.data.local

/**
 * El webm de Wikimedia de «Limpieza de playa» responde 404.
 * Este MP4 es H.264 (avc1), corto, y lo reproducen iOS y Android.
 */
object OgtMediaSources {
    const val BeachCleanupVideo =
        "https://interactive-examples.mdn.mozilla.net/media/cc0-videos/flower.mp4"

    fun currentUrl(url: String): String =
        if (url.contains("Sea_lion_Ushuaia.webm")) BeachCleanupVideo else url
}
