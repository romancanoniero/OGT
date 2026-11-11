package com.onlygoodthings.backend.auth

import com.onlygoodthings.shared.domain.UserRole
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.http.HttpStatusCode
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class StoreReviewAuthorizationRouteTest {
    @Test fun onlyTheExactAuthorizedOrdinaryAccountCanPassTheProxyCheck() = testApplication {
        val now = Instant.parse("2026-11-09T12:00:00Z")
        val policy = StoreReviewAccess(setOf("reviewer"), now.plusSeconds(60), Clock.fixed(now, ZoneOffset.UTC))
        val verifier = object : FirebaseTokenVerifier {
            override fun verify(idToken: String): VerifiedFirebaseUser = when (idToken) {
                "review-token" -> VerifiedFirebaseUser("reviewer", null, null, null, UserRole.USER)
                "other-token" -> VerifiedFirebaseUser("other", null, null, null, UserRole.USER)
                "staff-token" -> VerifiedFirebaseUser("reviewer", null, null, null, UserRole.COMMUNITY_MODERATOR)
                else -> error("invalid fixture")
            }
        }
        application { routing { storeReviewAuthorization(verifier, policy) } }
        for (authorization in listOf(null, "review-token", "Basic review-token", "Bearer invalid", "Bearer other-token", "Bearer staff-token")) {
            val result = client.get("/internal/store-review/authorize") {
                if (authorization != null) header("Authorization", authorization)
                header("X-Forwarded-User", "reviewer")
            }
            assertEquals(HttpStatusCode.Unauthorized, result.status)
        }
        assertEquals(HttpStatusCode.NoContent, client.get("/internal/store-review/authorize") { header("Authorization", "Bearer review-token") }.status)
    }
}
