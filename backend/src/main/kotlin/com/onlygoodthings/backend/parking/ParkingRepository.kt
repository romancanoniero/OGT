package com.onlygoodthings.backend.parking

import com.onlygoodthings.backend.infra.Database
import com.onlygoodthings.backend.infra.doubleOrNull
import com.onlygoodthings.backend.infra.intOrNull
import com.onlygoodthings.backend.infra.longOrNull
import com.onlygoodthings.backend.infra.stringOrNull
import com.onlygoodthings.shared.domain.GeoPoint
import com.onlygoodthings.shared.domain.ParkingCandidate
import com.onlygoodthings.shared.domain.ParkingCompleteResult
import com.onlygoodthings.shared.domain.ParkingHandoff
import com.onlygoodthings.shared.domain.ParkingMatch
import com.onlygoodthings.shared.domain.ParkingRules
import com.onlygoodthings.shared.domain.ParkingSpot
import com.onlygoodthings.shared.domain.ParkingStatus
import kotlin.random.Random
import java.sql.ResultSet
import java.sql.Timestamp
import java.time.Instant
import java.util.UUID

class ParkingSqlRepository(
    private val db: Database,
    private val random: Random = Random.Default,
) {

    fun publish(
        ownerUserId: String,
        location: GeoPoint,
        ownerLocation: GeoPoint,
        ownerEtaSeconds: Int,
        ttlMinutes: Int,
        notes: String?,
        vehicleLabel: String?,
        leftoverNow: Boolean = false,
    ): ParkingSpot =
        db.withConnection { connection ->
            val id = UUID.randomUUID().toString()
            val now = Instant.now()
            val expires = now.plusSeconds(ttlMinutes.toLong() * 60)
            val interestCloses = if (leftoverNow) now else now.plusMillis(ParkingRules.INTEREST_WINDOW_MS)
            val waitDeadline = if (leftoverNow) now else now.plusSeconds(ownerEtaSeconds.toLong() + ParkingRules.OWNER_WAIT_SECONDS)
            val eta = if (leftoverNow) 1 else ownerEtaSeconds
            connection.prepareStatement(
                """
                INSERT INTO parking_spots (
                    id, owner_user_id, location, owner_last_location, status, expires_at, notes, reward_points,
                    owner_eta_seconds, interest_closes_at, owner_wait_deadline_at,
                    leftover_open, matching_resolved, vehicle_label, address, eta_seconds
                )
                VALUES (
                    ?::uuid,
                    ?::uuid,
                    ST_SetSRID(ST_MakePoint(?, ?), 4326),
                    ST_SetSRID(ST_MakePoint(?, ?), 4326),
                    'AVAILABLE',
                    ?,
                    ?,
                    ?,
                    ?,
                    ?,
                    ?,
                    ?,
                    ?,
                    ?,
                    ?,
                    ?
                )
                RETURNING $SPOT_RETURNING
                """.trimIndent(),
            ).use { stmt ->
                stmt.setString(1, id)
                stmt.setString(2, ownerUserId)
                stmt.setDouble(3, location.longitude)
                stmt.setDouble(4, location.latitude)
                stmt.setDouble(5, ownerLocation.longitude)
                stmt.setDouble(6, ownerLocation.latitude)
                stmt.setTimestamp(7, Timestamp.from(expires))
                stmt.setString(8, notes)
                stmt.setInt(9, ParkingRules.DEFAULT_REWARD_POINTS)
                stmt.setInt(10, eta)
                stmt.setTimestamp(11, Timestamp.from(interestCloses))
                stmt.setTimestamp(12, Timestamp.from(waitDeadline))
                stmt.setBoolean(13, leftoverNow)
                stmt.setBoolean(14, leftoverNow)
                stmt.setString(15, vehicleLabel)
                stmt.setString(16, notes)
                stmt.setInt(17, eta)
                stmt.executeQuery().use { rs ->
                    rs.next()
                    rs.toSpot(distanceMeters = 0.0)
                }
            }
        }

    fun nearby(origin: GeoPoint, radiusMeters: Int, viewerUserId: String? = null): List<ParkingSpot> {
        expireDue()
        resolveDue()
        val radius = radiusMeters.coerceIn(50, ParkingRules.MAX_RADIUS_METERS)
        return db.withConnection { connection ->
            connection.prepareStatement(
                """
                SELECT $SPOT_SELECT,
                       ST_Distance(
                           location::geography,
                           ST_SetSRID(ST_MakePoint(?, ?), 4326)::geography
                       ) AS meters
                FROM parking_spots
                WHERE status = 'AVAILABLE'
                  AND expires_at > now()
                  AND ST_DWithin(
                        location::geography,
                        ST_SetSRID(ST_MakePoint(?, ?), 4326)::geography,
                        ?
                      )
                ORDER BY meters
                LIMIT ${ParkingRules.MAX_NEARBY}
                """.trimIndent(),
            ).use { stmt ->
                stmt.setDouble(1, origin.longitude)
                stmt.setDouble(2, origin.latitude)
                stmt.setDouble(3, origin.longitude)
                stmt.setDouble(4, origin.latitude)
                stmt.setInt(5, radius)
                stmt.executeQuery().use { rs ->
                    buildList {
                        while (rs.next()) add(decorate(rs.toSpot(rs.getDouble("meters")), viewerUserId))
                    }
                }
            }
        }
    }

    /**
     * Reserva con optimistic locking: version + estado AVAILABLE.
     * El UPDATE atómico evita que dos buscadores se lleven la misma plaza.
     */
    fun claim(
        spotId: String,
        claimantUserId: String,
        expectedVersion: Int,
        location: GeoPoint,
    ): ParkingSpot? = db.withConnection { connection ->
        connection.prepareStatement(
            """
            UPDATE parking_spots
            SET status = 'CLAIMED',
                claimed_by_user_id = ?::uuid,
                claimed_at = now(),
                version = version + 1,
                claimant_last_location = ST_SetSRID(ST_MakePoint(?, ?), 4326),
                distance_meters = ST_Distance(
                    location::geography,
                    ST_SetSRID(ST_MakePoint(?, ?), 4326)::geography
                ),
                eta_seconds = GREATEST(
                    1,
                    (ST_Distance(
                        location::geography,
                        ST_SetSRID(ST_MakePoint(?, ?), 4326)::geography
                    ) / 4.5)::int
                )
            WHERE id = ?::uuid
              AND status = 'AVAILABLE'
              AND leftover_open = TRUE
              AND version = ?
              AND expires_at > now()
              AND owner_user_id <> ?::uuid
            RETURNING $SPOT_RETURNING
            """.trimIndent(),
        ).use { stmt ->
            stmt.setString(1, claimantUserId)
            stmt.setDouble(2, location.longitude)
            stmt.setDouble(3, location.latitude)
            stmt.setDouble(4, location.longitude)
            stmt.setDouble(5, location.latitude)
            stmt.setDouble(6, location.longitude)
            stmt.setDouble(7, location.latitude)
            stmt.setString(8, spotId)
            stmt.setInt(9, expectedVersion)
            stmt.setString(10, claimantUserId)
            stmt.executeQuery().use { rs ->
                if (rs.next()) rs.toSpot(null) else null
            }
        }
    }

    fun updateTick(spotId: String, role: String, lat: Double, lng: Double, etaSeconds: Int?, distance: Double?): ParkingSpot? {
        db.withConnection { connection ->
            val column = if (role == "OWNER") "owner_last_location" else "claimant_last_location"
            connection.prepareStatement(
                """
                UPDATE parking_spots
                SET $column = ST_SetSRID(ST_MakePoint(?, ?), 4326),
                    eta_seconds = COALESCE(?, eta_seconds),
                    distance_meters = COALESCE(?, distance_meters)
                WHERE id = ?::uuid
                """.trimIndent(),
            ).use { stmt ->
                stmt.setDouble(1, lng)
                stmt.setDouble(2, lat)
                if (etaSeconds == null) stmt.setNull(3, java.sql.Types.INTEGER) else stmt.setInt(3, etaSeconds)
                if (distance == null) stmt.setNull(4, java.sql.Types.NUMERIC) else stmt.setDouble(4, distance)
                stmt.setString(5, spotId)
                stmt.executeUpdate()
            }
        }
        return findById(spotId)
    }

    fun findById(spotId: String, viewerUserId: String? = null): ParkingSpot? = db.withConnection { connection ->
        connection.prepareStatement(
            """
            SELECT $SPOT_SELECT, distance_meters AS meters
            FROM parking_spots
            WHERE id = ?::uuid
            """.trimIndent(),
        ).use { stmt ->
            stmt.setString(1, spotId)
            stmt.executeQuery().use { rs ->
                if (rs.next()) decorate(rs.toSpot(rs.doubleOrNull("meters")), viewerUserId) else null
            }
        }
    }

    fun activeFor(userId: String): List<ParkingSpot> {
        expireDue()
        resolveDue()
        return db.withConnection { connection ->
            connection.prepareStatement(
                """
                SELECT $SPOT_SELECT, distance_meters AS meters
                FROM parking_spots
                WHERE status IN ('AVAILABLE', 'CLAIMED')
                  AND (owner_user_id = ?::uuid OR claimed_by_user_id = ?::uuid)
                ORDER BY vacated_at DESC
                """.trimIndent(),
            ).use { stmt ->
                stmt.setString(1, userId)
                stmt.setString(2, userId)
                stmt.executeQuery().use { rs ->
                    buildList { while (rs.next()) add(decorate(rs.toSpot(rs.doubleOrNull("meters")), userId)) }
                }
            }
        }
    }

    fun historyFor(userId: String): List<ParkingHandoff> = db.withConnection { connection ->
        connection.prepareStatement(
            """
            SELECT id, parking_spot_id, owner_user_id, claimant_user_id,
                   proximity_meters, verified, points_awarded,
                   EXTRACT(EPOCH FROM created_at) * 1000 AS completed_ms
            FROM parking_handoffs
            WHERE owner_user_id = ?::uuid OR claimant_user_id = ?::uuid
            ORDER BY created_at DESC
            LIMIT 50
            """.trimIndent(),
        ).use { stmt ->
            stmt.setString(1, userId)
            stmt.setString(2, userId)
            stmt.executeQuery().use { rs ->
                buildList { while (rs.next()) add(rs.toHandoff()) }
            }
        }
    }

    fun cancel(spotId: String, actorUserId: String): ParkingSpot? = db.withConnection { connection ->
        connection.prepareStatement(
            """
            UPDATE parking_spots
            SET status = CASE
                    WHEN owner_user_id = ?::uuid AND status IN ('AVAILABLE', 'CLAIMED') THEN 'CANCELLED'::parking_status
                    WHEN claimed_by_user_id = ?::uuid AND status = 'CLAIMED' THEN 'AVAILABLE'::parking_status
                    ELSE status
                END,
                claimed_by_user_id = CASE
                    WHEN claimed_by_user_id = ?::uuid AND status = 'CLAIMED' THEN NULL
                    ELSE claimed_by_user_id
                END,
                claimant_last_location = CASE
                    WHEN claimed_by_user_id = ?::uuid AND status = 'CLAIMED' THEN NULL
                    ELSE claimant_last_location
                END,
                leftover_open = CASE
                    WHEN claimed_by_user_id = ?::uuid AND status = 'CLAIMED' THEN TRUE
                    ELSE leftover_open
                END,
                matching_resolved = CASE
                    WHEN claimed_by_user_id = ?::uuid AND status = 'CLAIMED' THEN TRUE
                    ELSE matching_resolved
                END,
                version = version + 1
            WHERE id = ?::uuid
              AND (
                    (owner_user_id = ?::uuid AND status IN ('AVAILABLE', 'CLAIMED'))
                    OR (claimed_by_user_id = ?::uuid AND status = 'CLAIMED')
                  )
            RETURNING $SPOT_RETURNING
            """.trimIndent(),
        ).use { stmt ->
            repeat(9) { stmt.setString(it + 1, if (it == 6) spotId else actorUserId) }
            stmt.executeQuery().use { rs ->
                if (rs.next()) rs.toSpot(null) else null
            }
        }
    }

    /** El buscador encontró otra plaza: suelta el claim y la cesión vuelve FCFS. */
    fun foundOtherPlace(spotId: String, actorUserId: String): ParkingSpot? = db.withConnection { connection ->
        connection.prepareStatement(
            """
            UPDATE parking_spots
            SET status = 'AVAILABLE'::parking_status,
                claimed_by_user_id = NULL,
                claimant_last_location = NULL,
                leftover_open = TRUE,
                matching_resolved = TRUE,
                version = version + 1
            WHERE id = ?::uuid
              AND claimed_by_user_id = ?::uuid
              AND status = 'CLAIMED'
            RETURNING $SPOT_RETURNING
            """.trimIndent(),
        ).use { stmt ->
            stmt.setString(1, spotId)
            stmt.setString(2, actorUserId)
            stmt.executeQuery().use { rs ->
                if (rs.next()) rs.toSpot(null) else null
            }
        }
    }

    /**
     * Cierra la cesión si el reclamante está a ≤ N m de la plaza
     * o si ambas últimas posiciones coinciden. El cliente no suma puntos.
     */
    fun completeIfClose(spotId: String, maxMeters: Double): ParkingCompleteResult? = db.withConnection { connection ->
        connection.autoCommit = false
        try {
            val verified = connection.prepareStatement(
                """
                UPDATE parking_spots
                SET status = 'COMPLETED', completed_at = now(), version = version + 1
                WHERE id = ?::uuid
                  AND status = 'CLAIMED'
                  AND claimant_last_location IS NOT NULL
                  AND (
                        ST_Distance(location::geography, claimant_last_location::geography) <= ?
                        OR (
                            owner_last_location IS NOT NULL
                            AND ST_Distance(owner_last_location::geography, claimant_last_location::geography) <= ?
                        )
                      )
                RETURNING $SPOT_RETURNING,
                          owner_user_id AS done_owner,
                          claimed_by_user_id AS done_claimant,
                          reward_points AS done_points,
                          LEAST(
                              ST_Distance(location::geography, claimant_last_location::geography),
                              COALESCE(ST_Distance(owner_last_location::geography, claimant_last_location::geography), 1e9)
                          ) AS meters
                """.trimIndent(),
            ).use { stmt ->
                stmt.setString(1, spotId)
                stmt.setDouble(2, maxMeters)
                stmt.setDouble(3, maxMeters)
                stmt.executeQuery().use { rs ->
                    if (!rs.next()) return@use null
                    CompleteRow(
                        spot = rs.toSpot(rs.getDouble("meters")),
                        owner = rs.getString("done_owner"),
                        claimant = rs.getString("done_claimant"),
                        points = rs.getInt("done_points"),
                        meters = rs.getDouble("meters"),
                    )
                }
            } ?: run {
                connection.rollback()
                return@withConnection null
            }
            connection.prepareStatement(
                "UPDATE users SET community_points = community_points + ? WHERE id = ?::uuid",
            ).use { stmt ->
                stmt.setInt(1, verified.points)
                stmt.setString(2, verified.owner)
                stmt.executeUpdate()
            }
            val handoff = connection.prepareStatement(
                """
                INSERT INTO parking_handoffs (parking_spot_id, owner_user_id, claimant_user_id, proximity_meters, verified, points_awarded)
                VALUES (?::uuid, ?::uuid, ?::uuid, ?, TRUE, ?)
                RETURNING id, parking_spot_id, owner_user_id, claimant_user_id,
                          proximity_meters, verified, points_awarded,
                          EXTRACT(EPOCH FROM created_at) * 1000 AS completed_ms
                """.trimIndent(),
            ).use { stmt ->
                stmt.setString(1, spotId)
                stmt.setString(2, verified.owner)
                stmt.setString(3, verified.claimant)
                stmt.setDouble(4, verified.meters)
                stmt.setInt(5, verified.points)
                stmt.executeQuery().use { rs ->
                    rs.next()
                    rs.toHandoff()
                }
            }
            connection.prepareStatement(
                """
                INSERT INTO domain_events (event_type, aggregate_type, aggregate_id, payload)
                VALUES ('parking.completed', 'parking_spot', ?::uuid, jsonb_build_object('points', ?, 'meters', ?))
                """.trimIndent(),
            ).use { stmt ->
                stmt.setString(1, spotId)
                stmt.setInt(2, verified.points)
                stmt.setDouble(3, verified.meters)
                stmt.executeUpdate()
            }
            connection.commit()
            ParkingCompleteResult(verified.spot, handoff)
        } catch (error: Exception) {
            connection.rollback()
            throw error
        } finally {
            connection.autoCommit = true
        }
    }

    fun expireDue() {
        db.withConnection { connection ->
            connection.prepareStatement(
                """
                UPDATE parking_spots
                SET status = 'EXPIRED', version = version + 1
                WHERE status IN ('AVAILABLE', 'CLAIMED')
                  AND (
                        expires_at <= now()
                        OR (owner_wait_deadline_at IS NOT NULL AND owner_wait_deadline_at <= now())
                      )
                """.trimIndent(),
            ).use { it.executeUpdate() }
        }
    }

    fun expressInterest(
        spotId: String,
        userId: String,
        location: GeoPoint,
        seekerEtaSeconds: Int,
    ): ParkingSpot? {
        expireDue()
        resolveDue()
        val spot = findById(spotId, userId) ?: return null
        if (spot.ownerUserId == userId) error("No podés reclamar tu propia plaza")
        if (spot.status != ParkingStatus.AVAILABLE) return null
        if (spot.leftoverOpen) return claim(spotId, userId, spot.version, location)
        val now = Instant.now().toEpochMilli()
        if ((spot.interestClosesAtEpochMs ?: 0L) <= now) {
            resolveDue()
            return findById(spotId, userId)
        }
        val points = communityPoints(userId)
        db.withConnection { connection ->
            connection.prepareStatement(
                """
                INSERT INTO parking_interests (parking_spot_id, user_id, eta_seconds, community_points)
                VALUES (?::uuid, ?::uuid, ?, ?)
                ON CONFLICT (parking_spot_id, user_id)
                DO UPDATE SET eta_seconds = EXCLUDED.eta_seconds,
                              community_points = EXCLUDED.community_points,
                              expressed_at = now()
                """.trimIndent(),
            ).use { stmt ->
                stmt.setString(1, spotId)
                stmt.setString(2, userId)
                stmt.setInt(3, seekerEtaSeconds)
                stmt.setInt(4, points)
                stmt.executeUpdate()
            }
        }
        return findById(spotId, userId)
    }

    fun resolveDue() {
        val dueIds = db.withConnection { connection ->
            connection.prepareStatement(
                """
                SELECT id::text
                FROM parking_spots
                WHERE status = 'AVAILABLE'
                  AND matching_resolved = FALSE
                  AND interest_closes_at IS NOT NULL
                  AND interest_closes_at <= now()
                """.trimIndent(),
            ).use { stmt ->
                stmt.executeQuery().use { rs ->
                    buildList { while (rs.next()) add(rs.getString(1)) }
                }
            }
        }
        dueIds.forEach { resolveSpot(it) }
    }

    private fun resolveSpot(spotId: String) {
        val spot = findById(spotId) ?: return
        if (spot.status != ParkingStatus.AVAILABLE || spot.leftoverOpen) return
        val ownerEta = spot.ownerEtaSeconds ?: 0
        val candidates = loadCandidates(spotId)
        val winner = ParkingMatch.drawWinner(ParkingMatch.eligibleOf(candidates, ownerEta), random)
        if (winner == null) {
            markLeftover(spotId)
        } else {
            assignWinner(spotId, winner.userId, winner.etaSeconds)
        }
    }

    private fun loadCandidates(spotId: String): List<ParkingCandidate> = db.withConnection { connection ->
        connection.prepareStatement(
            """
            SELECT user_id::text, eta_seconds, community_points,
                   EXTRACT(EPOCH FROM expressed_at) * 1000 AS expressed_ms
            FROM parking_interests
            WHERE parking_spot_id = ?::uuid
            """.trimIndent(),
        ).use { stmt ->
            stmt.setString(1, spotId)
            stmt.executeQuery().use { rs ->
                buildList {
                    while (rs.next()) {
                        add(
                            ParkingCandidate(
                                userId = rs.getString("user_id"),
                                etaSeconds = rs.getInt("eta_seconds"),
                                communityPoints = rs.getInt("community_points"),
                                expressedAtEpochMs = rs.getLong("expressed_ms"),
                            ),
                        )
                    }
                }
            }
        }
    }

    private fun markLeftover(spotId: String) {
        db.withConnection { connection ->
            connection.prepareStatement(
                """
                UPDATE parking_spots
                SET leftover_open = TRUE, matching_resolved = TRUE, version = version + 1
                WHERE id = ?::uuid AND status = 'AVAILABLE' AND matching_resolved = FALSE
                """.trimIndent(),
            ).use { stmt ->
                stmt.setString(1, spotId)
                stmt.executeUpdate()
            }
        }
    }

    private fun assignWinner(spotId: String, winnerUserId: String, seekerEta: Int) {
        db.withConnection { connection ->
            connection.prepareStatement(
                """
                UPDATE parking_spots
                SET status = 'CLAIMED',
                    claimed_by_user_id = ?::uuid,
                    claimed_at = now(),
                    version = version + 1,
                    matching_resolved = TRUE,
                    leftover_open = FALSE,
                    eta_seconds = ?
                WHERE id = ?::uuid
                  AND status = 'AVAILABLE'
                  AND matching_resolved = FALSE
                """.trimIndent(),
            ).use { stmt ->
                stmt.setString(1, winnerUserId)
                stmt.setInt(2, seekerEta)
                stmt.setString(3, spotId)
                stmt.executeUpdate()
            }
        }
    }

    private fun communityPoints(userId: String): Int = db.withConnection { connection ->
        connection.prepareStatement("SELECT community_points FROM users WHERE id = ?::uuid").use { stmt ->
            stmt.setString(1, userId)
            stmt.executeQuery().use { rs -> if (rs.next()) rs.getInt(1) else 0 }
        }
    }

    private fun decorate(spot: ParkingSpot, viewerUserId: String?): ParkingSpot {
        val count = interestCount(spot.id)
        val interested = viewerUserId != null && hasInterest(spot.id, viewerUserId)
        return spot.copy(interestCount = count, viewerInterested = interested)
    }

    private fun interestCount(spotId: String): Int = db.withConnection { connection ->
        connection.prepareStatement(
            "SELECT COUNT(*) FROM parking_interests WHERE parking_spot_id = ?::uuid",
        ).use { stmt ->
            stmt.setString(1, spotId)
            stmt.executeQuery().use { rs -> if (rs.next()) rs.getInt(1) else 0 }
        }
    }

    private fun hasInterest(spotId: String, userId: String): Boolean = db.withConnection { connection ->
        connection.prepareStatement(
            "SELECT 1 FROM parking_interests WHERE parking_spot_id = ?::uuid AND user_id = ?::uuid",
        ).use { stmt ->
            stmt.setString(1, spotId)
            stmt.setString(2, userId)
            stmt.executeQuery().use { it.next() }
        }
    }

    private data class CompleteRow(
        val spot: ParkingSpot,
        val owner: String,
        val claimant: String,
        val points: Int,
        val meters: Double,
    )

    private fun ResultSet.toSpot(distanceMeters: Double?): ParkingSpot = ParkingSpot(
        id = getString("id"),
        ownerUserId = getString("owner_user_id"),
        claimedByUserId = stringOrNull("claimed_by_user_id"),
        location = GeoPoint(getDouble("lat"), getDouble("lng")),
        status = ParkingStatus.valueOf(getString("status")),
        version = getInt("version"),
        expiresAtEpochMs = getLong("expires_ms"),
        distanceMeters = distanceMeters ?: doubleOrNull("meters"),
        etaSeconds = intOrNull("eta_seconds"),
        rewardPoints = getInt("reward_points"),
        notes = stringOrNull("notes"),
        address = stringOrNull("address"),
        vehicleLabel = stringOrNull("vehicle_label"),
        ownerEtaSeconds = intOrNull("owner_eta_seconds"),
        interestClosesAtEpochMs = longOrNull("interest_closes_ms"),
        ownerWaitDeadlineAtEpochMs = longOrNull("wait_deadline_ms"),
        leftoverOpen = getBoolean("leftover_open"),
    )

    private fun ResultSet.toHandoff(): ParkingHandoff = ParkingHandoff(
        id = getString("id"),
        parkingSpotId = getString("parking_spot_id"),
        ownerUserId = getString("owner_user_id"),
        claimantUserId = getString("claimant_user_id"),
        proximityMeters = getDouble("proximity_meters"),
        verified = getBoolean("verified"),
        pointsAwarded = getInt("points_awarded"),
        completedAtEpochMs = getLong("completed_ms"),
    )

    private companion object {
        const val SPOT_SELECT = """
            id, owner_user_id, claimed_by_user_id, status, version, reward_points, notes, eta_seconds,
            owner_eta_seconds, leftover_open, vehicle_label, address,
            EXTRACT(EPOCH FROM expires_at) * 1000 AS expires_ms,
            EXTRACT(EPOCH FROM interest_closes_at) * 1000 AS interest_closes_ms,
            EXTRACT(EPOCH FROM owner_wait_deadline_at) * 1000 AS wait_deadline_ms,
            ST_Y(location) AS lat, ST_X(location) AS lng
        """
        const val SPOT_RETURNING = SPOT_SELECT
    }
}
