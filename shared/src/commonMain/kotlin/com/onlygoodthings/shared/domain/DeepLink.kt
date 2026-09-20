package com.onlygoodthings.shared.domain

fun honorDeepLink(token: String): String = honorAppLink(token)

fun postDeepLink(postId: String): String = "ogt://p/$postId"

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
