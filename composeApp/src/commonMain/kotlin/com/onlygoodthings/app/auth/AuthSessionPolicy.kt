package com.onlygoodthings.app.auth

import com.onlygoodthings.shared.domain.ApiResponse
import com.onlygoodthings.shared.domain.UserProfile
import kotlinx.coroutines.CancellationException

/** Firebase owns persisted credentials; the old optional "keep" preference is no longer consulted. */
internal suspend fun restoreSavedAuthSession(
    explicitlySignedOut: Boolean,
    biometricEnabled: Boolean,
    currentUser: suspend () -> AuthUser?,
    openSession: suspend (AuthUser) -> Unit,
    onboardingDone: () -> Boolean,
    onSessionExpired: suspend () -> Unit,
): AuthRestore {
    if (explicitlySignedOut) return AuthRestore.LoggedOut
    val user = currentUser() ?: return AuthRestore.LoggedOut
    if (biometricEnabled) return AuthRestore.NeedsBiometric
    try {
        openSession(user)
    } catch (cancel: CancellationException) {
        throw cancel
    } catch (expired: AuthSessionExpired) {
        onSessionExpired()
        return AuthRestore.LoginRequired
    }
    // Temporary failures propagate to the startup retry UI, without discarding credentials.
    return if (onboardingDone()) AuthRestore.Home else AuthRestore.FirstRun
}

internal fun profileNeedsCompletion(name: String, barrio: String, firebaseUid: String, photoUrl: String?): Boolean =
    name.trim().length < 2 || name.trim() == firebaseUid || '@' in name ||
        name.trim().lowercase() in setOf("vecino", "alguien de la comunidad") || barrio.isBlank() || photoUrl.isNullOrBlank()

/** A Firebase credential alone is not an OGT session or a local profile. */
internal fun confirmedSessionProfile(response: ApiResponse<UserProfile>, firebaseUid: String): UserProfile {
    if (!response.success && isExpiredSessionCode(response.errorCode)) throw AuthSessionExpired()
    if (!response.success) throw AuthException(when (response.errorCode) {
        "ACCOUNT_UNAVAILABLE" -> "Esta cuenta está deshabilitada o fue eliminada"
        else -> "No pudimos abrir tu sesión de OGT. Volvé a intentarlo."
    })
    val profile = response.data ?: throw AuthException("OGT no devolvió tu perfil. Volvé a intentarlo.")
    if (profile.id.isBlank() || firebaseUid.isBlank() || profile.firebaseUid != firebaseUid) {
        throw AuthException("No se pudo confirmar tu cuenta de OGT. Volvé a ingresar.")
    }
    return profile
}
