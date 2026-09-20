package com.onlygoodthings.shared.realtime

/**
 * Árbol que escucha db-kmp-sdk contra db-realtime-gateway.
 * El backend relacional es la fuente de verdad; estos paths son la proyección en vivo.
 */
object OgtDbPaths {
    const val PARKING_SPOTS = "ogt/parking/spots"
    const val PARKING_TICKS = "ogt/parking/ticks"
    const val IMPACT_ALERTS = "ogt/alerts"
    const val SOCIAL_FEED = "ogt/social/posts"
    const val PRESENCE = "ogt/presence"

    fun parkingSpot(spotId: String): String = "$PARKING_SPOTS/$spotId"
    fun parkingTick(spotId: String, role: String): String = "$PARKING_TICKS/$spotId/$role"
    fun impactAlert(alertId: String): String = "$IMPACT_ALERTS/$alertId"
    fun socialPost(postId: String): String = "$SOCIAL_FEED/$postId"
    fun socialComments(postId: String): String = "${socialPost(postId)}/comments"
    fun socialComment(postId: String, commentId: String): String = "${socialComments(postId)}/$commentId"
    fun presence(userId: String): String = "$PRESENCE/$userId"
}

/** REST `http(s)://host` → socket `ws(s)://host/db` de db-kmp-sdk. */
fun gatewayEndpointFromApiBase(apiBase: String): String {
    val trimmed = apiBase.trim().trimEnd('/')
    val (scheme, rest) = when {
        trimmed.startsWith("https://", ignoreCase = true) -> "wss" to trimmed.substringAfter("://")
        trimmed.startsWith("http://", ignoreCase = true) -> "ws" to trimmed.substringAfter("://")
        trimmed.startsWith("wss://", ignoreCase = true) -> "wss" to trimmed.substringAfter("://")
        trimmed.startsWith("ws://", ignoreCase = true) -> "ws" to trimmed.substringAfter("://")
        else -> "ws" to trimmed.ifBlank { "127.0.0.1:8080" }
    }
    return "$scheme://${rest.removeSuffix("/db")}/db"
}
