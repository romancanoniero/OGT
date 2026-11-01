package com.onlygoodthings.app.auth

import com.onlygoodthings.shared.domain.ApiResponse
import com.onlygoodthings.shared.domain.UserProfile

/** A Firebase credential alone is not an OGT session or a local profile. */
internal fun confirmedSessionProfile(response: ApiResponse<UserProfile>, firebaseUid: String): UserProfile {
    if (!response.success) throw AuthException(when (response.errorCode) {
        "ACCOUNT_UNAVAILABLE" -> "Esta cuenta está deshabilitada o fue eliminada"
        "INVALID_TOKEN", "UNAUTHENTICATED" -> "La verificación venció. Volvé a ingresar."
        else -> "No pudimos abrir tu sesión de OGT. Volvé a intentarlo."
    })
    val profile = response.data ?: throw AuthException("OGT no devolvió tu perfil. Volvé a intentarlo.")
    if (profile.id.isBlank() || firebaseUid.isBlank() || profile.firebaseUid != firebaseUid) {
        throw AuthException("No se pudo confirmar tu cuenta de OGT. Volvé a ingresar.")
    }
    return profile
}
