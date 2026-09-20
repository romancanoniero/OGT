package com.onlygoodthings.app.auth

import com.onlygoodthings.shared.domain.UserProfile

class AuthException(message: String, cause: Throwable? = null) : Exception(message, cause)

data class AuthUser(
    val firebaseUid: String,
    val email: String?,
    val phone: String?,
    val displayName: String?,
    val photoUrl: String?,
)

data class PendingPhoneAuth(
    val verificationId: String,
    val phoneE164: String,
    val displayName: String? = null,
    val password: String? = null,
    val barrio: String? = null,
    val isNewUser: Boolean = false,
)

enum class AuthRestore {
    LoggedOut,
    NeedsBiometric,
    FirstRun,
    Home,
}

data class AuthSession(
    val user: AuthUser,
    val profile: UserProfile?,
    val idToken: String,
)

fun parseContact(raw: String): AuthContact {
    val value = raw.trim()
    if (value.isEmpty()) error("Ingresá correo o teléfono")
    if (value.contains('@')) return AuthContact.Email(value.lowercase())
    return AuthContact.Phone(normalizePhone(value))
}

fun normalizePhone(raw: String): String {
    val trimmed = raw.trim().replace(" ", "").replace("-", "")
    if (trimmed.startsWith("+")) return trimmed
    val digits = trimmed.filter { it.isDigit() }
    return when {
        digits.startsWith("54") -> "+$digits"
        digits.startsWith("9") && digits.length >= 10 -> "+54$digits"
        digits.startsWith("11") -> "+549$digits"
        else -> "+$digits"
    }
}

sealed class AuthContact {
    data class Email(val email: String) : AuthContact()
    data class Phone(val e164: String) : AuthContact()
}
