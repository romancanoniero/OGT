package com.onlygoodthings.backend.auth

import com.onlygoodthings.shared.domain.UserRole
import io.ktor.server.auth.Principal

data class AuthPrincipal(
    val userId: String,
    val firebaseUid: String,
    val role: UserRole,
    val email: String?,
) : Principal
