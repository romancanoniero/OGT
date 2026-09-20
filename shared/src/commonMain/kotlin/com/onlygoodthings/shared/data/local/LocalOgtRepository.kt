package com.onlygoodthings.shared.data.local

import com.onlygoodthings.shared.data.FeedRepository
import com.onlygoodthings.shared.data.ParkingRepository
import com.onlygoodthings.shared.domain.FeedEventKind
import com.onlygoodthings.shared.domain.FeedMode
import com.onlygoodthings.shared.domain.GeoMath
import com.onlygoodthings.shared.domain.GeoPoint
import com.onlygoodthings.shared.domain.ParkingCandidate
import com.onlygoodthings.shared.domain.ParkingCompleteResult
import com.onlygoodthings.shared.domain.ParkingHandoff
import com.onlygoodthings.shared.domain.ParkingMatch
import com.onlygoodthings.shared.domain.ParkingRules
import com.onlygoodthings.shared.domain.ParkingSpot
import com.onlygoodthings.shared.domain.ParkingStatus
import kotlin.random.Random
import com.onlygoodthings.shared.domain.SocialComment
import com.onlygoodthings.shared.domain.SocialPost
import com.onlygoodthings.shared.protocol.frames.LocationRole
import com.onlygoodthings.shared.realtime.currentEpochMs

/** Feed sobre el catálogo local. Más adelante se reemplaza por REST + WS. */
class LocalFeedRepository(
    private val db: OgtLocalDatabase,
    private val currentUserId: String = OgtIds.Mariana,
) : FeedRepository {
    override suspend fun loadFeed(
        cursor: String?,
        pageSize: Int,
        mode: FeedMode,
        family: com.onlygoodthings.shared.domain.FeedTopicFamily?,
    ): List<SocialPost> {
        val offset = cursor?.toIntOrNull() ?: 0
        return db.rankedFeed(currentUserId, mode, offset = offset, limit = pageSize, family = family).map { post ->
            db.toDomain(post).copy(discovery = db.isDiscovery(currentUserId, post))
        }
    }

    override suspend fun clapPost(postId: String): SocialPost {
        val idx = db.posts.indexOfFirst { it.id == postId }
        val post = db.posts[idx]
        db.posts[idx] = post.copy(impactCount = post.impactCount + 1)
        db.recordFeedEvent(currentUserId, postId, FeedEventKind.CLAP)
        return db.toDomain(db.posts[idx])
    }

    override suspend fun loadComments(postId: String): List<SocialComment> =
        db.commentsOf(postId).map(db::toDomain)

    override suspend fun addComment(postId: String, body: String, parentCommentId: String?): SocialComment {
        val comment = LocalComment(
            id = "c-${db.comments.size + 1}",
            postId = postId,
            authorUserId = currentUserId,
            body = body,
            timeLabel = "Ahora",
            parentCommentId = parentCommentId,
        )
        db.comments += comment
        val idx = db.posts.indexOfFirst { it.id == postId }
        if (idx >= 0) {
            val post = db.posts[idx]
            db.posts[idx] = post.copy(commentCount = post.commentCount + 1)
        }
        db.recordFeedEvent(currentUserId, postId, FeedEventKind.COMMENT)
        return db.toDomain(comment)
    }

    override suspend fun reportPost(postId: String, reason: String, details: String?) {
        // Preview local: no hay cola de moderación.
    }

    override suspend fun recordFeedEvent(postId: String, kind: FeedEventKind, dwellMs: Int?) {
        db.recordFeedEvent(currentUserId, postId, kind, dwellMs)
    }
}

/** Parking sobre el catálogo local. Distancia haversine, sin PostGIS. */
class LocalParkingRepository(
    private val db: OgtLocalDatabase,
    private val currentUserId: String = OgtIds.Mariana,
    private val random: Random = Random.Default,
    private val clock: () -> Long = { currentEpochMs() },
) : ParkingRepository {
    override suspend fun publishVacancy(
        location: GeoPoint,
        ttlMinutes: Int,
        notes: String?,
        vehicleLabel: String?,
        ownerLocation: GeoPoint?,
        leftoverNow: Boolean,
    ): ParkingSpot {
        val now = clock()
        val owner = db.user(currentUserId)
        val from = ownerLocation ?: location
        val ownerEta = if (leftoverNow) 1 else GeoMath.walkEtaSeconds(from, location)
        val spot = LocalParkingSpot(
            id = "spot-${now.toString(16)}",
            ownerUserId = currentUserId,
            claimedByUserId = null,
            latitude = location.latitude,
            longitude = location.longitude,
            status = ParkingStatus.AVAILABLE,
            version = 0,
            address = notes ?: owner.barrio,
            vehicleLabel = vehicleLabel?.takeIf { it.isNotBlank() } ?: "Mi vehículo",
            etaSeconds = ownerEta,
            distanceMeters = GeoMath.haversineMeters(from, location),
            rewardPoints = ParkingRules.DEFAULT_REWARD_POINTS,
            notes = notes,
            expiresAtEpochMs = now + ttlMinutes * 60_000L,
            ownerLastLat = from.latitude,
            ownerLastLng = from.longitude,
            ownerEtaSeconds = ownerEta,
            interestClosesAtEpochMs = if (leftoverNow) now else ParkingMatch.interestClosesAt(now),
            ownerWaitDeadlineAtEpochMs = if (leftoverNow) now else ParkingMatch.ownerWaitDeadlineAt(now, ownerEta),
            leftoverOpen = leftoverNow,
            matchingResolved = leftoverNow,
        )
        db.parkingSpots += spot
        db.bumpParking()
        return db.toDomain(spot, currentUserId)
    }

    override suspend fun nearby(location: GeoPoint, radiusMeters: Int): List<ParkingSpot> {
        settleParking()
        val radius = radiusMeters.coerceIn(50, ParkingRules.MAX_RADIUS_METERS)
        return db.parkingSpots
            .filter { it.status == ParkingStatus.AVAILABLE && it.ownerUserId != currentUserId }
            .map { spot ->
                val meters = GeoMath.haversineMeters(location, GeoPoint(spot.latitude, spot.longitude))
                spot.copy(distanceMeters = meters, etaSeconds = GeoMath.walkEtaSeconds(location, GeoPoint(spot.latitude, spot.longitude)))
            }
            .filter { (it.distanceMeters ?: 0.0) <= radius }
            .sortedBy { it.distanceMeters }
            .take(ParkingRules.MAX_NEARBY)
            .map { db.toDomain(it, currentUserId) }
    }

    override suspend fun expressInterest(spotId: String, location: GeoPoint): ParkingSpot {
        settleParking()
        val idx = db.parkingSpots.indexOfFirst { it.id == spotId }
        require(idx >= 0) { "Plaza inexistente" }
        val spot = db.parkingSpots[idx]
        require(spot.ownerUserId != currentUserId) { "No podés reclamar tu propia plaza" }
        require(spot.status == ParkingStatus.AVAILABLE) { "La plaza ya no está libre" }
        if (spot.leftoverOpen || spot.matchingResolved) {
            return claim(spotId, spot.version, location)
        }
        require(spot.interestClosesAtEpochMs > clock()) { "La ventana de interés ya cerró" }
        val eta = GeoMath.walkEtaSeconds(location, GeoPoint(spot.latitude, spot.longitude))
        val points = db.userOrNull(currentUserId)?.communityPoints ?: 0
        val existing = db.parkingInterests.indexOfFirst { it.spotId == spotId && it.userId == currentUserId }
        val row = LocalParkingInterest(
            spotId = spotId,
            userId = currentUserId,
            etaSeconds = eta,
            communityPoints = points,
            expressedAtEpochMs = clock(),
        )
        if (existing >= 0) db.parkingInterests[existing] = row else db.parkingInterests += row
        db.parkingSpots[idx] = spot.copy(
            distanceMeters = GeoMath.haversineMeters(location, GeoPoint(spot.latitude, spot.longitude)),
            etaSeconds = eta,
        )
        db.bumpParking()
        return db.toDomain(db.parkingSpots[idx], currentUserId)
    }

    override suspend fun claim(spotId: String, expectedVersion: Int, location: GeoPoint): ParkingSpot {
        settleParking()
        val idx = db.parkingSpots.indexOfFirst { it.id == spotId }
        require(idx >= 0) { "Plaza inexistente" }
        val spot = db.parkingSpots[idx]
        require(spot.ownerUserId != currentUserId) { "No podés reclamar tu propia plaza" }
        if (spot.status == ParkingStatus.AVAILABLE && !spot.leftoverOpen && spot.interestClosesAtEpochMs > clock()) {
            return expressInterest(spotId, location)
        }
        require(spot.status == ParkingStatus.AVAILABLE && spot.version == expectedVersion) {
            "La plaza ya fue asignada"
        }
        require(spot.leftoverOpen) { "Todavía se está emparejando" }
        return assignClaimant(idx, currentUserId, expectedVersion, location)
    }

    override suspend fun tick(
        spotId: String,
        location: GeoPoint,
        role: LocationRole?,
        speedMps: Double?,
    ): ParkingSpot {
        val idx = db.parkingSpots.indexOfFirst { it.id == spotId }
        require(idx >= 0) { "Plaza inexistente" }
        val spot = db.parkingSpots[idx]
        val resolved = role ?: when (currentUserId) {
            spot.ownerUserId -> LocationRole.OWNER
            spot.claimedByUserId -> LocationRole.CLAIMANT
            else -> error("No participás de esta cesión")
        }
        val peer = if (resolved == LocationRole.OWNER) {
            spot.claimantLastLat?.let { GeoPoint(it, spot.claimantLastLng ?: spot.longitude) }
                ?: GeoPoint(spot.latitude, spot.longitude)
        } else {
            spot.ownerLastLat?.let { GeoPoint(it, spot.ownerLastLng ?: spot.longitude) }
                ?: GeoPoint(spot.latitude, spot.longitude)
        }
        val meters = GeoMath.haversineMeters(location, peer)
        db.parkingSpots[idx] = if (resolved == LocationRole.OWNER) {
            spot.copy(
                ownerLastLat = location.latitude,
                ownerLastLng = location.longitude,
                distanceMeters = meters,
                etaSeconds = GeoMath.etaSeconds(meters, speedMps),
            )
        } else {
            spot.copy(
                claimantLastLat = location.latitude,
                claimantLastLng = location.longitude,
                distanceMeters = meters,
                etaSeconds = GeoMath.etaSeconds(meters, speedMps),
            )
        }
        db.bumpParking()
        return db.toDomain(db.parkingSpots[idx], currentUserId)
    }

    override suspend fun complete(spotId: String, location: GeoPoint): ParkingCompleteResult {
        tick(spotId, location, null, null)
        val idx = db.parkingSpots.indexOfFirst { it.id == spotId }
        val spot = db.parkingSpots[idx]
        require(spot.status == ParkingStatus.CLAIMED) { "La plaza no está reservada" }
        require(currentUserId == spot.ownerUserId || currentUserId == spot.claimedByUserId) {
            "Solo el cedente o quien reclama puede confirmar"
        }
        val claimantPoint = GeoPoint(
            spot.claimantLastLat ?: location.latitude,
            spot.claimantLastLng ?: location.longitude,
        )
        val ownerPoint = GeoPoint(
            spot.ownerLastLat ?: spot.latitude,
            spot.ownerLastLng ?: spot.longitude,
        )
        val pairMeters = GeoMath.haversineMeters(ownerPoint, claimantPoint)
        val toSpot = GeoMath.haversineMeters(claimantPoint, GeoPoint(spot.latitude, spot.longitude))
        val meters = minOf(pairMeters, toSpot)
        require(meters <= ParkingRules.PROXIMITY_METERS) {
            "Acercate a ${ParkingRules.PROXIMITY_METERS.toInt()} m para confirmar la cesión"
        }
        val now = clock()
        db.parkingSpots[idx] = spot.copy(status = ParkingStatus.COMPLETED, version = spot.version + 1)
        val ownerIdx = db.users.indexOfFirst { it.id == spot.ownerUserId }
        if (ownerIdx >= 0) {
            val owner = db.users[ownerIdx]
            db.users[ownerIdx] = owner.copy(communityPoints = owner.communityPoints + spot.rewardPoints)
        }
        val handoff = LocalParkingHandoff(
            id = "handoff-$now",
            parkingSpotId = spot.id,
            ownerUserId = spot.ownerUserId,
            claimantUserId = spot.claimedByUserId ?: currentUserId,
            proximityMeters = meters,
            verified = true,
            pointsAwarded = spot.rewardPoints,
            completedAtEpochMs = now,
        )
        db.parkingHandoffs += handoff
        db.karma += LocalKarmaEntry(
            id = "k-park-$now",
            userId = spot.ownerUserId,
            title = "Espacio de parking liberado",
            place = spot.address,
            delta = spot.rewardPoints,
            timeLabel = "Ahora",
        )
        db.notifications += LocalNotification(
            id = "n-park-$now",
            kind = "KARMA",
            title = "¡Ganaste +${spot.rewardPoints} Pts Barriales!",
            body = "Confirmamos la cesión en ${spot.address}. La comunidad respira un poco más.",
            timeLabel = "Ahora · Impacto: menos tráfico en la zona",
            urgent = false,
            recipientUserId = spot.ownerUserId,
        )
        db.bumpParking()
        return ParkingCompleteResult(db.toDomain(db.parkingSpots[idx], currentUserId), db.toDomain(handoff))
    }

    override suspend fun foundOtherPlace(spotId: String): ParkingSpot {
        val idx = db.parkingSpots.indexOfFirst { it.id == spotId }
        require(idx >= 0) { "Plaza inexistente" }
        val spot = db.parkingSpots[idx]
        require(currentUserId == spot.claimedByUserId && spot.status == ParkingStatus.CLAIMED) {
            "Solo quien pidió el lugar puede soltarlo porque encontró otro"
        }
        val now = clock()
        db.parkingSpots[idx] = spot.copy(
            status = ParkingStatus.AVAILABLE,
            claimedByUserId = null,
            claimantLastLat = null,
            claimantLastLng = null,
            leftoverOpen = true,
            matchingResolved = true,
            version = spot.version + 1,
        )
        val seeker = db.userOrNull(currentUserId)
        db.notifications += LocalNotification(
            id = "n-found-other-$now",
            kind = "PARKING",
            title = "Encontró otro lugar",
            body = "${seeker?.displayName ?: "Alguien de la comunidad"} ya no necesita la plaza en ${spot.address}. Sigue en el radar.",
            timeLabel = "Ahora",
            urgent = true,
            recipientUserId = spot.ownerUserId,
        )
        db.bumpParking()
        return db.toDomain(db.parkingSpots[idx], currentUserId)
    }

    override suspend fun cancel(spotId: String): ParkingSpot {
        val idx = db.parkingSpots.indexOfFirst { it.id == spotId }
        require(idx >= 0) { "Plaza inexistente" }
        val spot = db.parkingSpots[idx]
        db.parkingSpots[idx] = when {
            currentUserId == spot.ownerUserId &&
                spot.status in listOf(ParkingStatus.AVAILABLE, ParkingStatus.CLAIMED) ->
                spot.copy(status = ParkingStatus.CANCELLED, version = spot.version + 1)
            currentUserId == spot.claimedByUserId && spot.status == ParkingStatus.CLAIMED ->
                spot.copy(
                    status = ParkingStatus.AVAILABLE,
                    claimedByUserId = null,
                    claimantLastLat = null,
                    claimantLastLng = null,
                    leftoverOpen = true,
                    matchingResolved = true,
                    version = spot.version + 1,
                )
            else -> error("No podés cancelar esta plaza")
        }
        db.bumpParking()
        return db.toDomain(db.parkingSpots[idx], currentUserId)
    }

    override suspend fun active(): List<ParkingSpot> {
        settleParking()
        return db.parkingSpots
            .filter {
                it.status in listOf(ParkingStatus.AVAILABLE, ParkingStatus.CLAIMED) &&
                    (it.ownerUserId == currentUserId || it.claimedByUserId == currentUserId)
            }
            .map { db.toDomain(it, currentUserId) }
    }

    override suspend fun history(): List<ParkingHandoff> =
        db.parkingHandoffs
            .filter { it.ownerUserId == currentUserId || it.claimantUserId == currentUserId }
            .sortedByDescending { it.completedAtEpochMs }
            .map(db::toDomain)

    override suspend fun get(spotId: String): ParkingSpot? {
        settleParking()
        return db.parkingSpots.firstOrNull { it.id == spotId }?.let { db.toDomain(it, currentUserId) }
    }

    private fun settleParking() {
        db.expireDueParking(clock())
        resolveDueMatches()
        db.expireDueParking(clock())
    }

    private fun resolveDueMatches() {
        val now = clock()
        db.parkingSpots.indices.forEach { idx ->
            val spot = db.parkingSpots[idx]
            if (spot.status != ParkingStatus.AVAILABLE || spot.matchingResolved) return@forEach
            if (spot.interestClosesAtEpochMs > now) return@forEach
            val ownerEta = spot.ownerEtaSeconds ?: 0
            val candidates = db.parkingInterests
                .filter { it.spotId == spot.id }
                .map {
                    ParkingCandidate(
                        userId = it.userId,
                        etaSeconds = it.etaSeconds,
                        communityPoints = it.communityPoints,
                        expressedAtEpochMs = it.expressedAtEpochMs,
                    )
                }
            val winner = ParkingMatch.drawWinner(ParkingMatch.eligibleOf(candidates, ownerEta), random)
            if (winner == null) {
                db.parkingSpots[idx] = spot.copy(leftoverOpen = true, matchingResolved = true, version = spot.version + 1)
                db.bumpParking()
            } else {
                val interest = db.parkingInterests.first { it.spotId == spot.id && it.userId == winner.userId }
                assignClaimant(
                    idx = idx,
                    claimantUserId = winner.userId,
                    expectedVersion = db.parkingSpots[idx].version,
                    location = GeoPoint(spot.latitude, spot.longitude),
                    seekerEta = interest.etaSeconds,
                    markResolved = true,
                )
            }
        }
    }

    private fun assignClaimant(
        idx: Int,
        claimantUserId: String,
        expectedVersion: Int,
        location: GeoPoint,
        seekerEta: Int? = null,
        markResolved: Boolean = true,
    ): ParkingSpot {
        val spot = db.parkingSpots[idx]
        require(spot.status == ParkingStatus.AVAILABLE && spot.version == expectedVersion) {
            "La plaza ya fue asignada"
        }
        val meters = GeoMath.haversineMeters(location, GeoPoint(spot.latitude, spot.longitude))
        db.parkingSpots[idx] = spot.copy(
            claimedByUserId = claimantUserId,
            status = ParkingStatus.CLAIMED,
            version = expectedVersion + 1,
            claimantLastLat = location.latitude,
            claimantLastLng = location.longitude,
            distanceMeters = meters,
            etaSeconds = seekerEta ?: GeoMath.walkEtaSeconds(location, GeoPoint(spot.latitude, spot.longitude)),
            leftoverOpen = false,
            matchingResolved = markResolved,
        )
        db.bumpParking()
        return db.toDomain(db.parkingSpots[idx], currentUserId)
    }
}
