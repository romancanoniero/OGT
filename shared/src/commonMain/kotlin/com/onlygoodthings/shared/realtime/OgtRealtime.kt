package com.onlygoodthings.shared.realtime

import com.iyr.db.database.ChildEventListener
import com.iyr.db.database.DataSnapshot
import com.iyr.db.database.DatabaseError
import com.iyr.db.database.DbDatabase
import com.onlygoodthings.shared.domain.GeoPoint
import com.onlygoodthings.shared.domain.ParkingSpot
import com.onlygoodthings.shared.domain.ParkingStatus
import com.onlygoodthings.shared.domain.SocialComment
import com.onlygoodthings.shared.domain.SocialLiveCounters
import com.onlygoodthings.shared.protocol.frames.ImpactAlertFrame
import com.onlygoodthings.shared.protocol.frames.ImpactKind
import com.onlygoodthings.shared.protocol.frames.LocationRole
import com.onlygoodthings.shared.protocol.frames.LocationTickFrame
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/**
 * Fachada de dominio sobre db-kmp-sdk.
 * La UI observa Flows; no habla el wire protocol ni Ktor.
 */
class OgtRealtime(
    private val db: DbDatabase = OgtSdk.database(),
) {
    fun observeParkingSpots(): Flow<ParkingSpot> = observeChildren(OgtDbPaths.PARKING_SPOTS) { snap ->
        snap.toParkingSpot()
    }

    fun observeImpactAlerts(): Flow<ImpactAlertFrame> = observeChildren(OgtDbPaths.IMPACT_ALERTS) { snap ->
        snap.toImpactAlert()
    }

    fun observeSocialPosts(): Flow<SocialLiveCounters> = observeChildren(OgtDbPaths.SOCIAL_FEED) { snap ->
        snap.toSocialLiveCounters()
    }

    fun observePostComments(postId: String): Flow<SocialComment> =
        observeChildren(OgtDbPaths.socialComments(postId)) { snap ->
            snap.toSocialComment(postId)
        }

    suspend fun pushSocialCounters(live: SocialLiveCounters) {
        db.getReference(OgtDbPaths.socialPost(live.id)).setValue(live.toTreeMap())
    }

    suspend fun pushSocialComment(comment: SocialComment) {
        db.getReference(OgtDbPaths.socialComment(comment.postId, comment.id))
            .setValue(comment.toTreeMap())
    }

    suspend fun pushLocationTick(tick: LocationTickFrame) {
        db.getReference(OgtDbPaths.parkingTick(tick.spotId, tick.role.name.lowercase()))
            .setValue(
                mapOf(
                    "spotId" to tick.spotId,
                    "userId" to tick.userId,
                    "role" to tick.role.name,
                    "latitude" to tick.latitude,
                    "longitude" to tick.longitude,
                    "speedMps" to tick.speedMps,
                    "headingDegrees" to tick.headingDegrees,
                    "recordedAtEpochMs" to tick.recordedAtEpochMs,
                ),
            )
    }

    suspend fun markPresence(userId: String, location: GeoPoint) {
        db.getReference(OgtDbPaths.presence(userId)).setValue(
            mapOf(
                "latitude" to location.latitude,
                "longitude" to location.longitude,
                "at" to currentEpochMs(),
            ),
        )
    }

    private fun <T> observeChildren(path: String, map: (DataSnapshot) -> T?): Flow<T> = callbackFlow {
        val query = db.getReference(path)
        val listener = object : ChildEventListener {
            override fun onChildAdded(snapshot: DataSnapshot, previousChildName: String?) {
                map(snapshot)?.let { trySend(it) }
            }

            override fun onChildChanged(snapshot: DataSnapshot, previousChildName: String?) {
                map(snapshot)?.let { trySend(it) }
            }

            override fun onChildRemoved(snapshot: DataSnapshot) = Unit
            override fun onChildMoved(snapshot: DataSnapshot, previousChildName: String?) = Unit
            override fun onCancelled(error: DatabaseError) {
                close(error.toException())
            }
        }
        query.addChildEventListener(listener)
        awaitClose { query.removeEventListener(listener) }
    }
}

@Suppress("UNCHECKED_CAST")
private fun DataSnapshot.toParkingSpot(): ParkingSpot? {
    val raw = getValue() as? Map<String, Any?> ?: return null
    val id = raw["id"] as? String ?: key() ?: return null
    val lat = (raw["latitude"] as? Number)?.toDouble() ?: return null
    val lng = (raw["longitude"] as? Number)?.toDouble() ?: return null
    val status = (raw["status"] as? String)?.let { runCatching { ParkingStatus.valueOf(it) }.getOrNull() }
        ?: ParkingStatus.AVAILABLE
    return ParkingSpot(
        id = id,
        ownerUserId = raw["ownerUserId"] as? String ?: return null,
        claimedByUserId = raw["claimedByUserId"] as? String,
        location = GeoPoint(lat, lng),
        status = status,
        version = (raw["version"] as? Number)?.toInt() ?: 0,
        expiresAtEpochMs = (raw["expiresAtEpochMs"] as? Number)?.toLong() ?: 0L,
        distanceMeters = (raw["distanceMeters"] as? Number)?.toDouble(),
        etaSeconds = (raw["etaSeconds"] as? Number)?.toInt(),
        rewardPoints = (raw["rewardPoints"] as? Number)?.toInt() ?: 0,
        notes = raw["notes"] as? String,
        ownerEtaSeconds = (raw["ownerEtaSeconds"] as? Number)?.toInt(),
        interestClosesAtEpochMs = (raw["interestClosesAtEpochMs"] as? Number)?.toLong(),
        ownerWaitDeadlineAtEpochMs = (raw["ownerWaitDeadlineAtEpochMs"] as? Number)?.toLong(),
        leftoverOpen = raw["leftoverOpen"] as? Boolean ?: false,
        interestCount = (raw["interestCount"] as? Number)?.toInt() ?: 0,
        viewerInterested = raw["viewerInterested"] as? Boolean ?: false,
    )
}

@Suppress("UNCHECKED_CAST")
private fun DataSnapshot.toImpactAlert(): ImpactAlertFrame? {
    val raw = getValue() as? Map<String, Any?> ?: return null
    val kind = (raw["kind"] as? String)?.let { runCatching { ImpactKind.valueOf(it) }.getOrNull() }
        ?: ImpactKind.LOST_PET
    return ImpactAlertFrame(
        alertId = raw["alertId"] as? String ?: key() ?: return null,
        kind = kind,
        title = raw["title"] as? String ?: return null,
        body = raw["body"] as? String ?: return null,
        latitude = (raw["latitude"] as? Number)?.toDouble() ?: return null,
        longitude = (raw["longitude"] as? Number)?.toDouble() ?: return null,
        radiusMeters = (raw["radiusMeters"] as? Number)?.toInt() ?: 3000,
        urgency = raw["urgency"] as? String ?: "MEDIUM",
        deepLink = raw["deepLink"] as? String,
    )
}

@Suppress("UNCHECKED_CAST")
private fun DataSnapshot.toSocialLiveCounters(): SocialLiveCounters? {
    val raw = getValue() as? Map<String, Any?> ?: return null
    return socialLiveCountersFrom(raw, key())
}

@Suppress("UNCHECKED_CAST")
private fun DataSnapshot.toSocialComment(fallbackPostId: String): SocialComment? {
    val raw = getValue() as? Map<String, Any?> ?: return null
    return socialCommentFrom(raw, key(), fallbackPostId)
}

fun socialLiveCountersFrom(raw: Map<String, Any?>, key: String?): SocialLiveCounters? {
    val id = raw["id"] as? String ?: key ?: return null
    return SocialLiveCounters(
        id = id,
        commentCount = (raw["commentCount"] as? Number)?.toInt() ?: 0,
        impactCount = (raw["impactCount"] as? Number)?.toInt() ?: 0,
        heartCount = (raw["heartCount"] as? Number)?.toInt() ?: 0,
        authorUserId = raw["authorUserId"] as? String,
        body = raw["body"] as? String,
        tag = raw["tag"] as? String,
        place = raw["place"] as? String,
        createdAtEpochMs = (raw["createdAtEpochMs"] as? Number)?.toLong() ?: 0L,
        listingId = raw["listingId"] as? String,
        honoreeName = raw["honoreeName"] as? String,
    )
}

fun socialCommentFrom(raw: Map<String, Any?>, key: String?, fallbackPostId: String?): SocialComment? {
    val id = raw["id"] as? String ?: key ?: return null
    val postId = raw["postId"] as? String ?: fallbackPostId ?: return null
    val authorUserId = raw["authorUserId"] as? String ?: return null
    val body = raw["body"] as? String ?: return null
    return SocialComment(
        id = id,
        postId = postId,
        authorUserId = authorUserId,
        authorName = raw["authorName"] as? String ?: "",
        parentCommentId = raw["parentCommentId"] as? String,
        body = body,
        createdAtEpochMs = (raw["createdAtEpochMs"] as? Number)?.toLong() ?: 0L,
    )
}

private fun SocialLiveCounters.toTreeMap(): Map<String, Any?> = buildMap {
    put("id", id)
    put("commentCount", commentCount)
    put("impactCount", impactCount)
    put("heartCount", heartCount)
    authorUserId?.let { put("authorUserId", it) }
    body?.let { put("body", it) }
    tag?.let { put("tag", it) }
    place?.let { put("place", it) }
    if (createdAtEpochMs > 0L) put("createdAtEpochMs", createdAtEpochMs)
    listingId?.let { put("listingId", it) }
    honoreeName?.let { put("honoreeName", it) }
}

private fun SocialComment.toTreeMap(): Map<String, Any?> = mapOf(
    "id" to id,
    "postId" to postId,
    "authorUserId" to authorUserId,
    "authorName" to authorName,
    "parentCommentId" to parentCommentId,
    "body" to body,
    "createdAtEpochMs" to createdAtEpochMs,
)

fun LocationTickFrame.Companion.now(
    spotId: String,
    userId: String,
    role: LocationRole,
    latitude: Double,
    longitude: Double,
    speedMps: Double? = null,
    headingDegrees: Double? = null,
): LocationTickFrame = LocationTickFrame(
    spotId = spotId,
    userId = userId,
    role = role,
    latitude = latitude,
    longitude = longitude,
    speedMps = speedMps,
    headingDegrees = headingDegrees,
    recordedAtEpochMs = currentEpochMs(),
)
