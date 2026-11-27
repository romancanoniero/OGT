package com.onlygoodthings.app.auth

import com.onlygoodthings.shared.domain.ApiResponse
import com.onlygoodthings.shared.domain.UserProfile
import kotlinx.coroutines.CancellationException

/** Firebase owns persisted credentials; the old optional "keep" preference is no longer consulted. */
internal suspend fun restoreSavedAuthSession(
    explicitlySignedOut: Boolean,
    appLockEnabled: Boolean,
    currentUser: suspend () -> AuthUser?,
    openSession: suspend (AuthUser) -> Unit,
    onboardingDone: () -> Boolean,
    onSessionExpired: suspend () -> Unit,
): AuthRestore {
    if (explicitlySignedOut) return AuthRestore.LoggedOut
    val user = currentUser() ?: return AuthRestore.LoggedOut
    if (appLockEnabled) return AuthRestore.NeedsBiometric
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

/** Only an explicit setting can enable app lock; cancellation never saves a partial choice. */
internal suspend fun updateSavedSessionLock(
    enabled: Boolean,
    currentOwner: suspend () -> String?,
    canAuthenticate: suspend () -> Boolean,
    authenticate: suspend () -> Boolean,
    save: (Boolean) -> Unit,
) {
    val owner = currentOwner()?.takeIf { it.isNotBlank() }
        ?: throw AuthException("Iniciá sesión para cambiar el bloqueo de OGT")
    if (enabled) {
        if (!canAuthenticate()) throw AuthException("Configurá la biometría del dispositivo para activar el bloqueo")
        if (!authenticate()) throw AuthException("No se activó el bloqueo porque no se confirmó tu identidad")
    }
    if (currentOwner() != owner) throw AuthException("La sesión cambió")
    save(enabled)
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
