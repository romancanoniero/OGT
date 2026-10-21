package com.onlygoodthings.backend.auth

import com.google.auth.oauth2.GoogleCredentials
import com.google.auth.oauth2.AccessToken
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
    val emailVerified: Boolean = false,
    val secondFactor: Boolean = false,
    val authTimeEpochSeconds: Long? = null,
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
            if (!System.getenv("FIREBASE_AUTH_EMULATOR_HOST").isNullOrBlank()) {
                // Application validates a local emulator and a demo project before initialization.
                builder.setCredentials(GoogleCredentials.create(AccessToken("owner", java.util.Date(Long.MAX_VALUE))))
            } else if (!credentialsPath.isNullOrBlank()) {
                FileInputStream(credentialsPath).use { builder.setCredentials(GoogleCredentials.fromStream(it)) }
            } else {
                builder.setCredentials(GoogleCredentials.getApplicationDefault())
            }
            FirebaseApp.initializeApp(builder.build())
        }
    }

    override fun verify(idToken: String): VerifiedFirebaseUser {
        val decoded = FirebaseAuth.getInstance().verifyIdToken(idToken)
        val roleClaim = decoded.claims["role"]?.toString() ?: UserRole.USER.name
        val firebaseClaims = decoded.claims["firebase"] as? Map<*, *>
        val secondFactor = !firebaseClaims?.get("sign_in_second_factor")?.toString().isNullOrBlank()
        return VerifiedFirebaseUser(
            firebaseUid = decoded.uid,
            email = decoded.email,
            phone = decoded.claims["phone_number"]?.toString(),
            displayName = decoded.name,
            role = runCatching { UserRole.valueOf(roleClaim) }.getOrDefault(UserRole.USER),
            emailVerified = decoded.claims["email_verified"] == true,
            secondFactor = secondFactor,
            authTimeEpochSeconds = (decoded.claims["auth_time"] as? Number)?.toLong(),
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
