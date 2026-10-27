package com.onlygoodthings.app.platform

/** Uses platform monotonic time. Repeated callbacks alone do not imply progress. */
class VideoPreparationWatchdog(
    private val startedAtMs: Long,
    private val stallAfterMs: Long = 90_000,
    private val totalAfterMs: Long = 600_000,
) {
    private var lastAdvanceMs = startedAtMs
    private var stage: String? = null
    private var highestProgress = -1
    private var pausedAtMs: Long? = null
    private var pausedDurationMs = 0L

    init { require(stallAfterMs > 0 && totalAfterMs >= stallAfterMs) }

    fun observe(stage: String, progressPermille: Int, nowMs: Long) {
        val progress = progressPermille.coerceIn(-1, 1000)
        if (this.stage != stage) {
            this.stage = stage
            highestProgress = progress
            lastAdvanceMs = nowMs
        } else if (progress > highestProgress) {
            highestProgress = progress
            lastAdvanceMs = nowMs
        }
    }

    /** The OS may suspend video work while the app is in the background. */
    fun setPaused(paused: Boolean, nowMs: Long) {
        if (paused && pausedAtMs == null) pausedAtMs = nowMs
        if (!paused) pausedAtMs?.let {
            pausedDurationMs += (nowMs - it).coerceAtLeast(0)
            pausedAtMs = null
            lastAdvanceMs = nowMs
        }
    }

    fun elapsedMs(nowMs: Long): Long = (nowMs - startedAtMs).coerceAtLeast(0)

    fun timeoutMessage(nowMs: Long): String? = when {
        pausedAtMs != null -> null
        elapsedMs(nowMs) - pausedDurationMs >= totalAfterMs -> "El video está tardando demasiado en prepararse. Volvé a intentarlo; el original se conserva."
        nowMs - lastAdvanceMs >= stallAfterMs -> "La preparación dejó de avanzar. Volvé a intentarlo; el original se conserva."
        else -> null
    }
}
