package com.onlygoodthings.app.auth

/** Firebase refreshes an expired cached token; a late refresh must never authorize another owner. */
internal suspend fun ownerScopedAccountToken(owner: () -> String, load: suspend (String) -> String?): String {
    val expected = owner()
    check(expected.isNotBlank()) { "Iniciá sesión para continuar" }
    val token = load(expected) ?: error("No se pudo renovar la sesión")
    check(owner() == expected) { "La sesión cambió" }
    return token
}
