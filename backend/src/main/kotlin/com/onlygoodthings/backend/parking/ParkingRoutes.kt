package com.onlygoodthings.backend.parking

import com.onlygoodthings.backend.auth.AuthPrincipal
import com.onlygoodthings.backend.http.JsonBody
import com.onlygoodthings.backend.http.optBoolean
import com.onlygoodthings.backend.http.optDouble
import com.onlygoodthings.backend.http.optInt
import com.onlygoodthings.backend.http.optString
import com.onlygoodthings.backend.http.reqDouble
import com.onlygoodthings.backend.http.reqInt
import com.onlygoodthings.backend.http.reqString
import com.onlygoodthings.backend.infra.RedisCache
import com.onlygoodthings.backend.realtime.RealtimeHub
import com.onlygoodthings.backend.routing.FootRoutingService
import com.onlygoodthings.shared.domain.ApiResponse
import com.onlygoodthings.shared.domain.GeoMath
import com.onlygoodthings.shared.domain.GeoPoint
import com.onlygoodthings.shared.domain.ParkingRules
import com.onlygoodthings.shared.domain.ParkingSpot
import com.onlygoodthings.shared.protocol.WsChannel
import com.onlygoodthings.shared.protocol.WsCodec
import com.onlygoodthings.shared.protocol.WsEnvelope
import com.onlygoodthings.shared.protocol.WsFrameType
import com.onlygoodthings.shared.protocol.frames.LocationPairEtaFrame
import com.onlygoodthings.shared.protocol.frames.LocationRole
import com.onlygoodthings.shared.protocol.frames.LocationTickFrame
import com.onlygoodthings.shared.protocol.frames.ParkingBroadcastFrame
import com.onlygoodthings.shared.protocol.frames.ParkingClaimResultFrame
import com.onlygoodthings.shared.realtime.currentEpochMs
import com.onlygoodthings.shared.realtime.nextRequestId
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.call
import io.ktor.server.auth.principal
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.post

fun Route.parkingRoutes(
    repository: ParkingSqlRepository,
    redis: RedisCache,
    hub: RealtimeHub,
    routing: FootRoutingService,
    proximityMeters: Double = ParkingRules.PROXIMITY_METERS,
) {
    post("/api/v1/parking/publish") {
        val principal = call.requireUser() ?: return@post
        val dataMap = JsonBody.receiveMap(call)
        val latitude = dataMap.reqDouble("latitude")
        val longitude = dataMap.reqDouble("longitude")
        val ttlMinutes = dataMap.optInt("ttlMinutes", ParkingRules.DEFAULT_TTL_MINUTES)
        val notes = dataMap.optString("notes")
        val vehicleLabel = dataMap.optString("vehicleLabel")
        val car = GeoPoint(latitude, longitude)
        val ownerLocation = GeoPoint(
            dataMap.optDouble("ownerLatitude") ?: latitude,
            dataMap.optDouble("ownerLongitude") ?: longitude,
        )

        if (!redis.allowRate("rl:parking:publish:${principal.userId}", ParkingRules.PUBLISH_RATE_PER_MINUTE, 60)) {
            return@post call.respond(
                HttpStatusCode.TooManyRequests,
                ApiResponse.fail<Unit>("Demasiadas publicaciones", "RATE_LIMIT"),
            )
        }

        val ownerEta = routing.walkEtaSeconds(ownerLocation, car)
        val spot = repository.publish(
            ownerUserId = principal.userId,
            location = car,
            ownerLocation = ownerLocation,
            ownerEtaSeconds = ownerEta,
            ttlMinutes = ttlMinutes,
            notes = notes,
            vehicleLabel = vehicleLabel,
            leftoverNow = dataMap.optBoolean("leftoverNow"),
        )
        hub.broadcast(
            WsChannel.PARKING,
            envelope(
                WsFrameType.PARKING_BROADCAST,
                ParkingBroadcastFrame(
                    spotId = spot.id,
                    ownerUserId = spot.ownerUserId,
                    latitude = latitude,
                    longitude = longitude,
                    expiresAtEpochMs = spot.expiresAtEpochMs,
                    rewardPoints = spot.rewardPoints,
                    notes = notes,
                    ownerEtaSeconds = spot.ownerEtaSeconds,
                    interestClosesAtEpochMs = spot.interestClosesAtEpochMs,
                    ownerWaitDeadlineAtEpochMs = spot.ownerWaitDeadlineAtEpochMs,
                ),
            ),
        )
        hub.projectParkingSpot(spot.id, WsCodec.json.encodeToString(ParkingSpot.serializer(), spot))
        call.respond(ApiResponse.ok(spot, "Vacante publicada"))
    }

    post("/api/v1/parking/nearby") {
        val principal = call.requireUser() ?: return@post
        val dataMap = JsonBody.receiveMap(call)
        val spots = repository.nearby(
            GeoPoint(dataMap.reqDouble("latitude"), dataMap.reqDouble("longitude")),
            dataMap.optInt("radius", ParkingRules.DEFAULT_RADIUS_METERS),
            principal.userId,
        )
        call.respond(ApiResponse.ok(spots))
    }

    post("/api/v1/parking/interest") {
        val principal = call.requireUser() ?: return@post
        val dataMap = JsonBody.receiveMap(call)
        val spotId = dataMap.reqString("spotId")
        val location = GeoPoint(dataMap.reqDouble("latitude"), dataMap.reqDouble("longitude"))
        val spot = repository.findById(spotId, principal.userId)
            ?: return@post call.respond(HttpStatusCode.NotFound, ApiResponse.fail<Unit>("Plaza inexistente", "NOT_FOUND"))
        val seekerEta = routing.walkEtaSeconds(location, spot.location)
        val updated = repository.expressInterest(spotId, principal.userId, location, seekerEta)
            ?: return@post call.respond(
                HttpStatusCode.Conflict,
                ApiResponse.fail<Unit>("La plaza ya no está libre", "CONFLICT"),
            )
        hub.projectParkingSpot(updated.id, WsCodec.json.encodeToString(ParkingSpot.serializer(), updated))
        val message = if (updated.status.name == "CLAIMED" && updated.claimedByUserId == principal.userId) {
            "Plaza asignada"
        } else if (updated.leftoverOpen) {
            "Quedó libre: el primero que la pida se la lleva"
        } else {
            "Interés registrado"
        }
        call.respond(ApiResponse.ok(updated, message))
    }

    post("/api/v1/parking/claim") {
        val principal = call.requireUser() ?: return@post
        val dataMap = JsonBody.receiveMap(call)
        val spotId = dataMap.reqString("spotId")
        val expectedVersion = dataMap.reqInt("expectedVersion")
        val location = GeoPoint(dataMap.reqDouble("latitude"), dataMap.reqDouble("longitude"))
        repository.resolveDue()
        val current = repository.findById(spotId, principal.userId)
        if (current != null && current.status.name == "AVAILABLE" && !current.leftoverOpen) {
            val seekerEta = routing.walkEtaSeconds(location, current.location)
            val updated = repository.expressInterest(spotId, principal.userId, location, seekerEta)
                ?: return@post call.respond(
                    HttpStatusCode.Conflict,
                    ApiResponse.fail<Unit>("La plaza ya no está libre", "CONFLICT"),
                )
            hub.projectParkingSpot(updated.id, WsCodec.json.encodeToString(ParkingSpot.serializer(), updated))
            return@post call.respond(ApiResponse.ok(updated, "Interés registrado"))
        }

        val claimed = repository.claim(spotId, principal.userId, expectedVersion, location)
        if (claimed == null) {
            hub.broadcast(
                WsChannel.PARKING,
                envelope(
                    WsFrameType.PARKING_CLAIM,
                    ParkingClaimResultFrame(
                        spotId = spotId,
                        accepted = false,
                        newVersion = expectedVersion,
                        status = "AVAILABLE",
                        conflictReason = "VERSION_CONFLICT",
                    ),
                ),
            )
            return@post call.respond(
                HttpStatusCode.Conflict,
                ApiResponse.fail<Unit>("La plaza ya fue reservada", "VERSION_CONFLICT"),
            )
        }
        hub.broadcast(
            WsChannel.PARKING,
            envelope(
                WsFrameType.PARKING_CLAIM,
                ParkingClaimResultFrame(
                    spotId = claimed.id,
                    accepted = true,
                    newVersion = claimed.version,
                    status = claimed.status.name,
                    conflictReason = null,
                ),
            ),
        )
        hub.projectParkingSpot(claimed.id, WsCodec.json.encodeToString(ParkingSpot.serializer(), claimed))
        call.respond(ApiResponse.ok(claimed, "Plaza asignada"))
    }

    post("/api/v1/parking/tick") {
        val principal = call.requireUser() ?: return@post
        val dataMap = JsonBody.receiveMap(call)
        val spotId = dataMap.reqString("spotId")
        val location = GeoPoint(dataMap.reqDouble("latitude"), dataMap.reqDouble("longitude"))
        val spot = repository.findById(spotId)
            ?: return@post call.respond(HttpStatusCode.NotFound, ApiResponse.fail<Unit>("Plaza inexistente", "NOT_FOUND"))
        val role = resolveRole(dataMap.optString("role"), principal.userId, spot)
            ?: return@post call.respond(HttpStatusCode.Forbidden, ApiResponse.fail<Unit>("No participás de esta cesión", "FORBIDDEN"))
        val peer = if (role == LocationRole.OWNER) {
            spot.location
        } else {
            spot.location
        }
        val meters = GeoMath.haversineMeters(location, peer)
        val speed = (dataMap["speedMps"] as? Number)?.toDouble()
        val updated = repository.updateTick(
            spotId = spotId,
            role = role.name,
            lat = location.latitude,
            lng = location.longitude,
            etaSeconds = GeoMath.etaSeconds(meters, speed),
            distance = meters,
        ) ?: spot
        hub.broadcast(
            WsChannel.PARKING,
            envelope(
                WsFrameType.LOCATION_TICK,
                LocationTickFrame(
                    spotId = spotId,
                    userId = principal.userId,
                    role = role,
                    latitude = location.latitude,
                    longitude = location.longitude,
                    speedMps = speed,
                    recordedAtEpochMs = currentEpochMs(),
                ),
            ),
        )
        hub.projectParkingSpot(
            spotId,
            WsCodec.json.encodeToString(
                LocationPairEtaFrame.serializer(),
                LocationPairEtaFrame(
                    spotId = spotId,
                    distanceMeters = meters,
                    etaSeconds = updated.etaSeconds ?: GeoMath.etaSeconds(meters, speed),
                    ownerLatitude = if (role == LocationRole.OWNER) location.latitude else spot.location.latitude,
                    ownerLongitude = if (role == LocationRole.OWNER) location.longitude else spot.location.longitude,
                    claimantLatitude = if (role == LocationRole.CLAIMANT) location.latitude else spot.location.latitude,
                    claimantLongitude = if (role == LocationRole.CLAIMANT) location.longitude else spot.location.longitude,
                ),
            ),
        )
        call.respond(ApiResponse.ok(updated, "Posición actualizada"))
    }

    post("/api/v1/parking/complete") {
        val principal = call.requireUser() ?: return@post
        val dataMap = JsonBody.receiveMap(call)
        val spotId = dataMap.reqString("spotId")
        val location = GeoPoint(dataMap.reqDouble("latitude"), dataMap.reqDouble("longitude"))
        val spot = repository.findById(spotId)
            ?: return@post call.respond(HttpStatusCode.NotFound, ApiResponse.fail<Unit>("Plaza inexistente", "NOT_FOUND"))
        val role = resolveRole(null, principal.userId, spot)
            ?: return@post call.respond(HttpStatusCode.Forbidden, ApiResponse.fail<Unit>("Solo el cedente o quien reclama puede confirmar", "FORBIDDEN"))
        repository.updateTick(spotId, role.name, location.latitude, location.longitude, null, null)
        val result = repository.completeIfClose(spotId, proximityMeters)
            ?: return@post call.respond(
                HttpStatusCode.Conflict,
                ApiResponse.fail<Unit>(
                    "Acercate a ${proximityMeters.toInt()} m para confirmar la cesión",
                    "TOO_FAR",
                ),
            )
        hub.projectParkingSpot(result.spot.id, WsCodec.json.encodeToString(ParkingSpot.serializer(), result.spot))
        call.respond(ApiResponse.ok(result, "Cesión verificada"))
    }

    post("/api/v1/parking/cancel") {
        val principal = call.requireUser() ?: return@post
        val dataMap = JsonBody.receiveMap(call)
        val cancelled = repository.cancel(dataMap.reqString("spotId"), principal.userId)
            ?: return@post call.respond(
                HttpStatusCode.Conflict,
                ApiResponse.fail<Unit>("No podés cancelar esta plaza", "CONFLICT"),
            )
        hub.projectParkingSpot(cancelled.id, WsCodec.json.encodeToString(ParkingSpot.serializer(), cancelled))
        call.respond(ApiResponse.ok(cancelled, "Plaza liberada"))
    }

    post("/api/v1/parking/found-other") {
        val principal = call.requireUser() ?: return@post
        val dataMap = JsonBody.receiveMap(call)
        val released = repository.foundOtherPlace(dataMap.reqString("spotId"), principal.userId)
            ?: return@post call.respond(
                HttpStatusCode.Conflict,
                ApiResponse.fail<Unit>("Solo quien pidió el lugar puede soltarlo porque encontró otro", "CONFLICT"),
            )
        hub.projectParkingSpot(released.id, WsCodec.json.encodeToString(ParkingSpot.serializer(), released))
        call.respond(ApiResponse.ok(released, "Encontró otro lugar. La plaza volvió al radar."))
    }

    post("/api/v1/parking/active") {
        val principal = call.requireUser() ?: return@post
        JsonBody.receiveMap(call)
        call.respond(ApiResponse.ok(repository.activeFor(principal.userId)))
    }

    post("/api/v1/parking/history") {
        val principal = call.requireUser() ?: return@post
        JsonBody.receiveMap(call)
        call.respond(ApiResponse.ok(repository.historyFor(principal.userId)))
    }

    post("/api/v1/parking/get") {
        val principal = call.requireUser() ?: return@post
        val dataMap = JsonBody.receiveMap(call)
        val spot = repository.findById(dataMap.reqString("spotId"), principal.userId)
            ?: return@post call.respond(HttpStatusCode.NotFound, ApiResponse.fail<Unit>("Plaza inexistente", "NOT_FOUND"))
        call.respond(ApiResponse.ok(spot))
    }
}

private suspend fun ApplicationCall.requireUser(): AuthPrincipal? {
    val principal = principal<AuthPrincipal>()
    if (principal == null) {
        respond(HttpStatusCode.Unauthorized, ApiResponse.fail<Unit>("Token requerido", "UNAUTHENTICATED"))
    }
    return principal
}

private fun resolveRole(raw: String?, userId: String, spot: ParkingSpot): LocationRole? {
    val requested = raw?.let { runCatching { LocationRole.valueOf(it.uppercase()) }.getOrNull() }
    return when {
        requested == LocationRole.OWNER && spot.ownerUserId == userId -> LocationRole.OWNER
        requested == LocationRole.CLAIMANT && spot.claimedByUserId == userId -> LocationRole.CLAIMANT
        requested == null && spot.ownerUserId == userId -> LocationRole.OWNER
        requested == null && spot.claimedByUserId == userId -> LocationRole.CLAIMANT
        else -> null
    }
}

private inline fun <reified T> envelope(type: WsFrameType, payload: T): WsEnvelope = WsEnvelope(
    type = type,
    requestId = nextRequestId(),
    channel = WsChannel.PARKING,
    sentAtEpochMs = currentEpochMs(),
    payload = WsCodec.toPayload(payload),
)
