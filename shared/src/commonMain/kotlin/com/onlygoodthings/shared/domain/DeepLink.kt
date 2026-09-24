package com.onlygoodthings.shared.domain

fun honorDeepLink(token: String): String = honorAppLink(token)

fun postDeepLink(postId: String): String = "ogt://p/$postId"

/** Link público del post: el destino de un share siempre es la ficha completa. */
fun postPublicLink(postId: String): String = "https://$OgtPublicWebHost/p/$postId"

/** Texto para WhatsApp / Telegram: titular + cuerpo + link a la ficha. */
fun postShareText(
    headline: String,
    body: String? = null,
    place: String? = null,
    postId: String,
): String = listOfNotNull(
    headline.takeIf { it.isNotBlank() },
    body?.trim()?.takeIf { it.isNotBlank() && it != headline },
    place?.trim()?.takeIf { it.isNotBlank() },
    postPublicLink(postId),
).joinToString("\n\n")

/**
 * Una anécdota se comparte como momento, pero el link abre el homenaje entero.
 * No hay ficha suelta de anécdota: sin el homenaje se pierde a quién se honra.
 */
fun anecdoteShareText(honoree: String, body: String, postId: String): String =
    postShareText(
        headline = "Anécdota del homenaje a $honoree",
        body = body,
        postId = postId,
    )

/** Extrae el post de `ogt://p/{id}` o `onlygoodthings.app/p/{id}`. */
fun parsePostDeepLink(uri: String): String? {
    val src = uri.trim()
    if (src.isEmpty()) return null
    val marker = "/p/"
    val at = src.indexOf(marker, ignoreCase = true)
    if (at < 0) return null
    val token = src.substring(at + marker.length).takeWhile { it.isLetterOrDigit() || it == '-' || it == '_' || it == '.' }
    return token.takeIf { it.length >= 3 }
}
