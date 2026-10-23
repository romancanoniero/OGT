package com.onlygoodthings.backend.realtime

import com.onlygoodthings.shared.realtime.OgtDbPaths

/**
 * Allowlist del socket `/db`.
 * Un cliente autenticado solo escucha árboles públicos o su propia presencia.
 * El padre `ogt`, `ogt/presence` y cualquier path arbitrario se rechazan.
 */
fun dbListenAllowed(rawPath: String, userId: String): Boolean {
    if (userId.isBlank() || userId.contains('/') || userId.contains('\\')) return false
    val path = rawPath.trim().trim('/')
    if (path.isEmpty()) return false
    if (path.any { it.isISOControl() || it == '\\' || it == '%' }) return false
    val segments = path.split('/')
    if (segments.any { it.isEmpty() || it == "." || it == ".." }) return false
    val publicRoots = listOf(
        OgtDbPaths.PARKING_SPOTS,
        OgtDbPaths.PARKING_TICKS,
        OgtDbPaths.IMPACT_ALERTS,
        OgtDbPaths.SOCIAL_FEED,
        OgtDbPaths.USERS,
    )
    if (publicRoots.any { path == it || path.startsWith("$it/") }) return true
    val ownPresence = OgtDbPaths.presence(userId)
    return path == ownPresence || path.startsWith("$ownPresence/")
}
