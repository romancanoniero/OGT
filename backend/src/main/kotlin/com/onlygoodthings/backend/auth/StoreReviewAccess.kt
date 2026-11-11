package com.onlygoodthings.backend.auth

import com.onlygoodthings.shared.domain.UserRole
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Clock
import java.time.Instant
import java.time.Duration

/** An empty, invalid or expired configuration is closed. Reviewers never gain CRM roles. */
class StoreReviewAccess(
    private val uids: Set<String>,
    private val expiresAt: Instant?,
    private val clock: Clock = Clock.systemUTC(),
) {
    fun allows(user: VerifiedFirebaseUser): Boolean = user.role == UserRole.USER &&
        user.firebaseUid in uids && expiresAt?.isAfter(clock.instant()) == true

    fun remainingMillis(): Long = expiresAt?.let { Duration.between(clock.instant(), it).toMillis().coerceAtLeast(0) } ?: 0

    companion object {
        fun from(env: (String) -> String?): StoreReviewAccess {
            val uids=env("OGT_STORE_REVIEW_UIDS").orEmpty().split(',').map(String::trim).filter(String::isNotEmpty).toSet()
            require(uids.size<=10 && uids.all { it.length<=128 && it.matches(Regex("[A-Za-z0-9:_-]+")) }) { "Cuentas de revisión inválidas" }
            val expiry=env("OGT_STORE_REVIEW_EXPIRES_AT")?.takeIf(String::isNotBlank)?.let(Instant::parse)
            require(uids.isEmpty() || expiry!=null) { "El acceso de revisión requiere vencimiento" }
            return StoreReviewAccess(uids,expiry)
        }
    }
}

fun Route.storeReviewAuthorization(verifier: FirebaseTokenVerifier, policy: StoreReviewAccess) {
    // Called internally by the reverse proxy. No credentials or identities in the response.
    get("/internal/store-review/authorize") {
        val authorization=call.request.headers["Authorization"].orEmpty()
        val allowed=if(!authorization.startsWith("Bearer ")) false else withContext(Dispatchers.IO) {
            runCatching { policy.allows(verifier.verify(authorization.removePrefix("Bearer "))) }.getOrDefault(false)
        }
        call.respond(if(allowed)HttpStatusCode.NoContent else HttpStatusCode.Unauthorized)
    }
}
