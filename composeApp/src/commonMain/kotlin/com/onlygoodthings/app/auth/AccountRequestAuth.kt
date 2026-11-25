package com.onlygoodthings.app.auth

/** Firebase refreshes an expired cached token; a late refresh must never authorize another owner. */
internal suspend fun ownerScopedAccountToken(owner: () -> String, load: suspend (String) -> String?): String {
    val expected = owner()
    check(expected.isNotBlank()) { "Iniciá sesión para continuar" }
    val token = load(expected) ?: error("No se pudo renovar la sesión")
    check(owner() == expected) { "La sesión cambió" }
    return token
}

/** Firebase may clear currentUser on revocation; a different account must survive a late failure. */
internal suspend fun renewedSessionToken(
    owner: suspend () -> String?,
    load: suspend () -> String?,
    onExpired: suspend () -> Unit,
): String {
    val expected = owner()
    return try {
        val token = load() ?: throw AuthSessionExpired()
        check(owner() == expected) { "La sesión cambió" }
        token
    } catch (expired: AuthSessionExpired) {
        val current = owner()
        check(current == null || current == expected) { "La sesión cambió" }
        onExpired()
        throw expired
    }
}
