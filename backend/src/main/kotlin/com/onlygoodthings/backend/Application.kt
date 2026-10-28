package com.onlygoodthings.backend

import com.onlygoodthings.backend.business.paymentWebhook
import com.onlygoodthings.backend.business.businessRoutes
import com.onlygoodthings.backend.crm.crmRoutes
import com.onlygoodthings.backend.community.userBlockRoutes
import com.onlygoodthings.backend.community.canReceiveTree
import com.onlygoodthings.backend.crm.crmStoryRoutes
import com.onlygoodthings.backend.stories.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import com.onlygoodthings.shared.protocol.WsCodec
import com.onlygoodthings.shared.domain.SocialLiveCounters
import com.onlygoodthings.backend.crm.crmContributionPath
import com.onlygoodthings.backend.auth.AuthPrincipal
import com.onlygoodthings.backend.auth.AuthSettings
import com.onlygoodthings.backend.auth.CompositeTokenVerifier
import com.onlygoodthings.backend.auth.FirebaseAdminTokenVerifier
import com.onlygoodthings.backend.auth.FirebaseTokenVerifier
import com.onlygoodthings.backend.auth.protectedAuthRoutes
import com.onlygoodthings.backend.auth.publicAuthRoutes
import com.onlygoodthings.backend.infra.Database
import com.onlygoodthings.backend.infra.IdentityStore
import com.onlygoodthings.backend.infra.RedisCache
import com.onlygoodthings.backend.modules.animalRoutes
import com.onlygoodthings.backend.modules.geoRoutes
import com.onlygoodthings.backend.modules.crowdfundingRoutes
import com.onlygoodthings.backend.modules.csrRoutes
import com.onlygoodthings.backend.modules.referralRoutes
import com.onlygoodthings.backend.community.neighborRoutes
import com.onlygoodthings.backend.modules.timebankRoutes
import com.onlygoodthings.backend.parking.ParkingSqlRepository
import com.onlygoodthings.backend.parking.parkingRoutes
import com.onlygoodthings.backend.routing.FootRoutingService
import com.onlygoodthings.backend.realtime.RealtimeHub
import com.onlygoodthings.backend.realtime.dbGatewayRoutes
import com.onlygoodthings.backend.notify.NoticeSqlRepository
import com.onlygoodthings.backend.notify.noticeRoutes
import com.onlygoodthings.backend.social.HonorSqlRepository
import com.onlygoodthings.backend.social.SocialSqlRepository
import com.onlygoodthings.backend.social.honorRoutes
import com.onlygoodthings.backend.social.publicHonorRoutes
import com.onlygoodthings.backend.social.socialRoutes
import com.onlygoodthings.backend.media.MediaStore
import com.onlygoodthings.backend.media.mediaRoutes
import com.onlygoodthings.backend.media.publicMediaRoutes
import com.onlygoodthings.shared.domain.ApiResponse
import java.nio.file.Path
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCallPipeline
import io.ktor.server.application.call
import io.ktor.server.application.install
import io.ktor.server.auth.Authentication
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.bearer
import io.ktor.server.auth.principal
import io.ktor.server.request.path
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
    embeddedServer(Netty, host = if(System.getenv("OGT_ENV")=="test")"127.0.0.1" else "0.0.0.0", port = System.getenv("PORT")?.toIntOrNull() ?: 8080, module = Application::module)
        .start(wait = true)
}

fun Application.module() {
    val jdbcUrl = env("OGT_JDBC_URL", "jdbc:postgresql://127.0.0.1:5432/onlygoodthings")
    val dbUser = env("OGT_DB_USER", "ogt")
    val dbPassword = env("OGT_DB_PASSWORD", "ogt_dev_change_me")
    val redisUrl = env("OGT_REDIS_URL", "redis://127.0.0.1:6379")
    val firebaseProject = env("OGT_FIREBASE_PROJECT_ID", "goodthings-55612")
    val credentials = env("OGT_FIREBASE_CREDENTIALS", "")
    val authSettings = AuthSettings.from(System::getenv)
    val allowDev = authSettings.allowDevTokens
    val parkingProximity = env("OGT_PARKING_PROXIMITY_METERS", "40").toDoubleOrNull()
        ?: com.onlygoodthings.shared.domain.ParkingRules.PROXIMITY_METERS
    val mediaStore = MediaStore(
        root = Path.of(env("OGT_MEDIA_DIR", "data/media")),
        publicBase = env("OGT_PUBLIC_BASE", "http://217.216.82.209:19080"),
    )

    val db = Database(jdbcUrl, dbUser, dbPassword)
    val redis = RedisCache(redisUrl)
    val identity = IdentityStore(db)
    val hub = RealtimeHub(redis)
    val stories = StorySqlRepository(db, StoryMediaStore(
        Path.of(env("OGT_MEDIA_DIR", "data/media")), env("OGT_PUBLIC_BASE", "http://217.216.82.209:19080"),
    ))
    launch {
        while (true) {
            runCatching { stories.purgeExpiredMedia() }.onFailure { org.slf4j.LoggerFactory.getLogger("Stories").warn("Story media cleanup deferred", it) }
            delay(15 * 60 * 1000L)
        }
    }
    redis.subscribeTree { channel, payload -> hub.ingestTreeChannel(channel, payload) }
    val verifier: FirebaseTokenVerifier = CompositeTokenVerifier(
        allowDev = allowDev,
        production = if (!allowDev || credentials.isNotBlank()) {
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
        exception<com.onlygoodthings.backend.business.BusinessAccessDenied> { call, _ ->
            call.respond(HttpStatusCode.Forbidden, ApiResponse.fail<Unit>("No tenés permiso para operar esta empresa", "FORBIDDEN"))
        }
        exception<com.onlygoodthings.backend.business.PaymentProviderFailure> { call, _ ->
            call.respond(HttpStatusCode.ServiceUnavailable, ApiResponse.fail<Unit>("Mercado Pago no respondió. Podés reintentar la misma operación.", "PAYMENT_RETRY"))
        }
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
    val csr=com.onlygoodthings.backend.business.CsrRepository(db)
    val paymentMode=System.getenv("OGT_MP_MODE").orEmpty().ifBlank{"disabled"}
    val mercadoPago=runCatching {com.onlygoodthings.backend.business.MercadoPagoConfiguration.fromEnv()}
        .onFailure {org.slf4j.LoggerFactory.getLogger("Payments").error("Corporate payments unavailable: review private configuration")}.getOrNull()
    val payments=mercadoPago?.let{com.onlygoodthings.backend.business.CsrPayments(csr,it.provider)}
    val accountErasure=com.onlygoodthings.backend.auth.AccountErasureService(db)
    launch(kotlinx.coroutines.Dispatchers.IO) {
        while(isActive) {
            runCatching { com.onlygoodthings.backend.crm.crmPurgeErasedMedia(db,mediaStore) }
                .onFailure { org.slf4j.LoggerFactory.getLogger("Privacy").warn("Media deletion retry deferred") }
            runCatching { stories.purgeErasedMedia() }
                .onFailure { org.slf4j.LoggerFactory.getLogger("Privacy").warn("Story deletion retry deferred") }
            runCatching { accountErasure.processPending() }
                .onFailure { org.slf4j.LoggerFactory.getLogger("Privacy").warn("Identity deletion retry deferred") }
            delay(15_000)
        }
    }
    launch(kotlinx.coroutines.Dispatchers.IO) {
        while(isActive) {
            runCatching { mercadoPago?.provider?.verifyAccount() }
                .onFailure { org.slf4j.LoggerFactory.getLogger("Payments").warn("Payment provider verification deferred") }
            runCatching { payments?.discoverPending() }
                .onFailure { org.slf4j.LoggerFactory.getLogger("Payments").warn("Payment lookup retry deferred") }
            runCatching { payments?.processPending() }
                .onFailure { org.slf4j.LoggerFactory.getLogger("Payments").warn("Payment reconciliation retry deferred") }
            delay(15_000)
        }
    }
    install(Authentication) {
        bearer("firebase") {
            realm = "OnlyGoodThings"
            authenticate { credential ->
                val verified = runCatching { verifier.verify(credential.token) }.getOrNull()
                    ?: return@authenticate null
                val profile = runCatching { identity.upsertFromFirebase(verified) }.getOrNull()
                    ?: return@authenticate null
                AuthPrincipal(
                    userId = profile.id,
                    firebaseUid = profile.firebaseUid,
                    role = profile.role,
                    email = verified.email,
                    canPublish = identity.canPublish(profile.id),
                )
            }
        }
    }

    routing {
        get("/health") {
            call.respond(ApiResponse.ok(mapOf("service" to "ogt-backend", "status" to "up",
                "payments" to (if(mercadoPago!=null)paymentMode else if(paymentMode=="disabled")"disabled" else "unavailable"),
                "paymentsReady" to (mercadoPago?.provider?.ready==true).toString())))
        }
        val honors = HonorSqlRepository(db)
        paymentWebhook(payments,mercadoPago?.webhookSecret)
        publicAuthRoutes(verifier, identity)
        publicHonorRoutes(honors)
        publicMediaRoutes(mediaStore) { com.onlygoodthings.backend.crm.crmPublicMediaAvailable(db,it) }
        storyMediaRoutes(stories)
        val blocks=com.onlygoodthings.backend.community.UserBlocks(db)
        dbGatewayRoutes(verifier, identity, hub) { viewer,path ->blocks.canReceiveTree(db,viewer,path) }
        authenticate("firebase") {
            // One security boundary for web, Android and iOS. Reading, reports,
            // account exports and removal of own content remain possible.
            intercept(ApplicationCallPipeline.Call) {
                val actor = call.principal<AuthPrincipal>()
                if (actor != null && !actor.canPublish && crmContributionPath(call.request.path())) {
                    call.respond(HttpStatusCode.Forbidden,
                        ApiResponse.fail<Unit>("Esta cuenta puede leer, pero no publicar", "PUBLISHING_BLOCKED"))
                    finish()
                }
            }
            protectedAuthRoutes(identity) { removed ->
                removed.forEach { id -> hub.projectSocialPost(id,WsCodec.json.encodeToString(
                    SocialLiveCounters.serializer(),com.onlygoodthings.backend.crm.crmRemovedPostLive(id))) }
                hub.projectStoryChange()
            }
            crmRoutes(db, verifier, hub, mediaStore)
            crmStoryRoutes(db, verifier, stories, hub)
            storyRoutes(stories, hub)
            parkingRoutes(ParkingSqlRepository(db), redis, hub, FootRoutingService.fromEnv(), parkingProximity)
            socialRoutes(SocialSqlRepository(db), hub)
            userBlockRoutes(com.onlygoodthings.backend.community.UserBlocks(db),hub)
            mediaRoutes(mediaStore) { owner,name ->
                db.withConnection { c -> c.prepareStatement("INSERT INTO uploaded_media_owners(stored_name,uploader_user_id) VALUES(?,?::uuid)").use { q ->
                    q.setString(1,name);q.setString(2,owner);q.executeUpdate()
                } }
            }
            honorRoutes(honors)
            noticeRoutes(NoticeSqlRepository(db))
            referralRoutes(db)
            animalRoutes(db, hub)
            geoRoutes()
            timebankRoutes(db)
            neighborRoutes(db, hub)
            csrRoutes(db)
            businessRoutes(csr,verifier,payments)
            crowdfundingRoutes(db)
        }
    }
}

private fun env(name: String, default: String): String = System.getenv(name)?.takeIf { it.isNotBlank() } ?: default
