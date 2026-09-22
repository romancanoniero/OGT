package com.onlygoodthings.backend.auth

import com.google.auth.oauth2.GoogleCredentials
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.auth.FirebaseAuth
import com.onlygoodthings.shared.domain.UserRole
import java.io.FileInputStream

data class VerifiedFirebaseUser(
    val firebaseUid: String,
    val email: String?,
    val phone: String?,
    val displayName: String?,
    val role: UserRole,
)

interface FirebaseTokenVerifier {
    fun verify(idToken: String): VerifiedFirebaseUser
}

class FirebaseAdminTokenVerifier(
    projectId: String,
    credentialsPath: String?,
) : FirebaseTokenVerifier {

    init {
        if (FirebaseApp.getApps().isEmpty()) {
            val builder = FirebaseOptions.builder().setProjectId(projectId)
            if (!credentialsPath.isNullOrBlank()) {
                builder.setCredentials(GoogleCredentials.fromStream(FileInputStream(credentialsPath)))
            } else {
                runCatching { builder.setCredentials(GoogleCredentials.getApplicationDefault()) }
            }
            FirebaseApp.initializeApp(builder.build())
        }
    }

    override fun verify(idToken: String): VerifiedFirebaseUser {
        val decoded = FirebaseAuth.getInstance().verifyIdToken(idToken)
        val roleClaim = decoded.claims["role"]?.toString() ?: UserRole.USER.name
        return VerifiedFirebaseUser(
            firebaseUid = decoded.uid,
            email = decoded.email,
            phone = decoded.claims["phone_number"]?.toString(),
            displayName = decoded.name,
            role = runCatching { UserRole.valueOf(roleClaim) }.getOrDefault(UserRole.USER),
        )
    }
}

/**
 * Tokens de laboratorio: `dev.<firebaseUid>.<ROLE>`.
 * Nunca habilitar en producción.
 */
class DevTokenVerifier : FirebaseTokenVerifier {
    override fun verify(idToken: String): VerifiedFirebaseUser {
        val parts = idToken.split('.')
        require(parts.size >= 3 && parts[0] == "dev") { "Token de desarrollo inválido" }
        val role = runCatching { UserRole.valueOf(parts[2]) }.getOrDefault(UserRole.USER)
        return VerifiedFirebaseUser(
            firebaseUid = parts[1],
            email = "${parts[1]}@dev.onlygoodthings.test",
            phone = null,
            displayName = null,
            role = role,
        )
    }
}

class CompositeTokenVerifier(
    private val allowDev: Boolean,
    private val production: FirebaseTokenVerifier?,
) : FirebaseTokenVerifier {
    private val dev = DevTokenVerifier()

    override fun verify(idToken: String): VerifiedFirebaseUser {
        if (allowDev && idToken.startsWith("dev.")) {
            return dev.verify(idToken)
        }
        val delegate = production ?: error("Firebase Admin no configurado")
        return delegate.verify(idToken)
    }
}
