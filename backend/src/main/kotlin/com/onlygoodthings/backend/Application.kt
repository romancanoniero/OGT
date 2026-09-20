package com.onlygoodthings.backend

import com.onlygoodthings.backend.auth.AuthPrincipal
import com.onlygoodthings.backend.auth.CompositeTokenVerifier
import com.onlygoodthings.backend.auth.FirebaseAdminTokenVerifier
import com.onlygoodthings.backend.auth.FirebaseTokenVerifier
import com.onlygoodthings.backend.auth.protectedAuthRoutes
import com.onlygoodthings.backend.auth.publicAuthRoutes
import com.onlygoodthings.backend.infra.Database
import com.onlygoodthings.backend.infra.IdentityStore
import com.onlygoodthings.backend.infra.RedisCache
import com.onlygoodthings.backend.modules.animalRoutes
import com.onlygoodthings.backend.modules.crowdfundingRoutes
import com.onlygoodthings.backend.modules.csrRoutes
import com.onlygoodthings.backend.modules.referralRoutes
import com.onlygoodthings.backend.modules.timebankRoutes
import com.onlygoodthings.backend.parking.ParkingSqlRepository
import com.onlygoodthings.backend.parking.parkingRoutes
import com.onlygoodthings.backend.routing.FootRoutingService
import com.onlygoodthings.backend.realtime.RealtimeHub
import com.onlygoodthings.backend.notify.NoticeSqlRepository
import com.onlygoodthings.backend.notify.noticeRoutes
import com.onlygoodthings.backend.social.HonorSqlRepository
import com.onlygoodthings.backend.social.SocialSqlRepository
import com.onlygoodthings.backend.social.honorRoutes
import com.onlygoodthings.backend.social.publicHonorRoutes
import com.onlygoodthings.backend.social.socialRoutes
import com.onlygoodthings.shared.domain.ApiResponse
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.call
import io.ktor.server.application.install
import io.ktor.server.auth.Authentication
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.bearer
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.calllogging.CallLogging
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.cors.routing.CORS
import io.ktor.server.plugins.doublereceive.DoubleReceive
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.ktor.server.websocket.WebSockets
import io.ktor.server.websocket.pingPeriod
import io.ktor.server.websocket.timeout
import kotlinx.serialization.json.Json
import org.slf4j.event.Level
import kotlin.time.Duration.Companion.seconds

fun main() {
    embeddedServer(Netty, port = System.getenv("PORT")?.toIntOrNull() ?: 8080, module = Application::module)
        .start(wait = true)
}

fun Application.module() {
    val jdbcUrl = env("OGT_JDBC_URL", "jdbc:postgresql://127.0.0.1:5432/onlygoodthings")
    val dbUser = env("OGT_DB_USER", "ogt")
    val dbPassword = env("OGT_DB_PASSWORD", "ogt_dev_change_me")
    val redisUrl = env("OGT_REDIS_URL", "redis://127.0.0.1:6379")
    val firebaseProject = env("OGT_FIREBASE_PROJECT_ID", "goodthings-55612")
    val credentials = env("OGT_FIREBASE_CREDENTIALS", "")
    val allowDev = env("OGT_ALLOW_DEV_TOKENS", "true").toBoolean()
    val parkingProximity = env("OGT_PARKING_PROXIMITY_METERS", "40").toDoubleOrNull()
        ?: com.onlygoodthings.shared.domain.ParkingRules.PROXIMITY_METERS

    val db = Database(jdbcUrl, dbUser, dbPassword)
    val redis = RedisCache(redisUrl)
    val identity = IdentityStore(db)
    val hub = RealtimeHub(redis)
    val verifier: FirebaseTokenVerifier = CompositeTokenVerifier(
        allowDev = allowDev,
        production = if (credentials.isNotBlank()) {
            FirebaseAdminTokenVerifier(firebaseProject, credentials)
        } else {
            null
        },
    )

    install(CallLogging) { level = Level.INFO }
    install(DoubleReceive)
    install(ContentNegotiation) {
        json(
            Json {
                ignoreUnknownKeys = true
                encodeDefaults = true
                explicitNulls = false
            },
        )
    }
    install(CORS) {
        anyHost()
        allowHeader(HttpHeaders.Authorization)
        allowHeader(HttpHeaders.ContentType)
        allowMethod(HttpMethod.Get)
        allowMethod(HttpMethod.Post)
        allowMethod(HttpMethod.Options)
    }
    install(StatusPages) {
        exception<IllegalArgumentException> { call, cause ->
            call.respond(HttpStatusCode.BadRequest, ApiResponse.fail<Unit>(cause.message ?: "Bad request", "BAD_REQUEST"))
        }
        exception<Throwable> { call, cause ->
            call.respond(HttpStatusCode.InternalServerError, ApiResponse.fail<Unit>(cause.message ?: "Error interno", "INTERNAL"))
        }
    }
    install(WebSockets) {
        pingPeriod = 15.seconds
        timeout = 30.seconds
    }
    install(Authentication) {
        bearer("firebase") {
            realm = "OnlyGoodThings"
            authenticate { credential ->
                val verified = verifier.verify(credential.token)
                val profile = identity.upsertFromFirebase(verified)
                AuthPrincipal(
                    userId = profile.id,
                    firebaseUid = profile.firebaseUid,
                    role = profile.role,
                    email = verified.email,
                )
            }
        }
    }

    routing {
        get("/health") {
            call.respond(ApiResponse.ok(mapOf("service" to "ogt-backend", "status" to "up")))
        }
        val honors = HonorSqlRepository(db)
        publicAuthRoutes(verifier, identity)
        publicHonorRoutes(honors)
        authenticate("firebase") {
            protectedAuthRoutes()
            parkingRoutes(ParkingSqlRepository(db), redis, hub, FootRoutingService.fromEnv(), parkingProximity)
            socialRoutes(SocialSqlRepository(db), hub)
            honorRoutes(honors)
            noticeRoutes(NoticeSqlRepository(db))
            referralRoutes(db)
            animalRoutes(db, hub)
            timebankRoutes(db)
            csrRoutes(db)
            crowdfundingRoutes(db)
        }
    }
}

private fun env(name: String, default: String): String = System.getenv(name)?.takeIf { it.isNotBlank() } ?: default
