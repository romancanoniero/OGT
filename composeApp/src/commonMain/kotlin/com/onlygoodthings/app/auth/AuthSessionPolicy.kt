package com.onlygoodthings.app.auth

import com.onlygoodthings.shared.domain.ApiResponse
import com.onlygoodthings.shared.domain.UserProfile

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
