package com.onlygoodthings.backend.community

import com.onlygoodthings.backend.auth.AuthPrincipal
import com.onlygoodthings.backend.http.JsonBody
import com.onlygoodthings.backend.http.optBoolean
import com.onlygoodthings.backend.http.optString
import com.onlygoodthings.backend.http.reqString
import com.onlygoodthings.backend.infra.Database
import com.onlygoodthings.backend.infra.stringOrNull
import com.onlygoodthings.backend.realtime.RealtimeHub
import com.onlygoodthings.shared.domain.ApiResponse
import com.onlygoodthings.shared.domain.CommunityNotice
import com.onlygoodthings.shared.domain.NeighborCard
import com.onlygoodthings.shared.domain.SkillCanon
import com.onlygoodthings.shared.domain.SkillSuggestHit
import com.onlygoodthings.shared.domain.SkillTagDto
import com.onlygoodthings.shared.domain.SocialLiveCounters
import com.onlygoodthings.shared.domain.TimebankBoard
import com.onlygoodthings.shared.domain.TimebankMatchHit
import com.onlygoodthings.shared.domain.TimebankMessageDto
import com.onlygoodthings.shared.domain.TimebankNeedPost
import com.onlygoodthings.shared.domain.TimebankNeedSupport
import com.onlygoodthings.shared.domain.TimebankThread
import com.onlygoodthings.shared.domain.WalletActivity
import com.onlygoodthings.shared.domain.WalletSummary
import com.onlygoodthings.shared.domain.communityLevelLabel
import com.onlygoodthings.shared.protocol.WsCodec
import com.onlygoodthings.shared.realtime.currentEpochMs
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.auth.principal
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import java.util.UUID

private const val MENSAJE_TAG = "mensaje"

fun Route.neighborRoutes(db: Database, hub: RealtimeHub) {
    post("/api/v1/wallet/summary") {
        val principal = requireNeighbor(call) ?: return@post
        call.respond(ApiResponse.ok(walletSummary(db, principal.userId)))
    }

    post("/api/v1/notifications/inbox") {
        val principal = requireNeighbor(call) ?: return@post
        call.respond(ApiResponse.ok(noticeInbox(db, principal.userId)))
    }

    post("/api/v1/notifications/read") {
        val principal = requireNeighbor(call) ?: return@post
        val dataMap = JsonBody.receiveMap(call)
        markNoticesRead(db, principal.userId, dataMap.optString("noticeId"))
        call.respond(ApiResponse.ok("ok", "Marcadas como leídas"))
    }

    post("/api/v1/timebank/tags") {
        val principal = requireNeighbor(call) ?: return@post
        val dataMap = runCatching { JsonBody.receiveMap(call) }.getOrDefault(emptyMap())
        val target = dataMap.optString("userId")?.takeIf { it.isNotBlank() } ?: principal.userId
        call.respond(ApiResponse.ok(skillTags(db, target)))
    }

    post("/api/v1/timebank/skill") {
        val principal = requireNeighbor(call) ?: return@post
        val dataMap = JsonBody.receiveMap(call)
        upsertSkill(
            db,
            principal.userId,
            dataMap.reqString("tagSlug"),
            dataMap.optBoolean("offered"),
            dataMap.optBoolean("requested"),
        )
        call.respond(ApiResponse.ok(skillTags(db, principal.userId), "Quedó en tu aviso"))
    }

    post("/api/v1/timebank/tag/create") {
        val principal = requireNeighbor(call) ?: return@post
        val dataMap = JsonBody.receiveMap(call)
        val created = runCatching {
            createOfferedSkill(db, principal.userId, dataMap.reqString("label"))
        }.getOrElse { error ->
            return@post call.respond(
                HttpStatusCode.BadRequest,
                ApiResponse.fail<Unit>(error.message ?: "No se pudo agregar", "BAD_REQUEST"),
            )
        }
        call.respond(ApiResponse.ok(created, "Quedó en lo que das"))
    }

    post("/api/v1/timebank/suggest") {
        val principal = requireNeighbor(call) ?: return@post
        val dataMap = runCatching { JsonBody.receiveMap(call) }.getOrDefault(emptyMap())
        val query = dataMap.optString("query")?.trim().orEmpty()
        call.respond(ApiResponse.ok(suggestSkills(db, principal.userId, query)))
    }

    post("/api/v1/timebank/need") {
        val principal = requireNeighbor(call) ?: return@post
        val dataMap = JsonBody.receiveMap(call)
        val created = runCatching {
            publishNeed(
                db,
                hub,
                principal.userId,
                dataMap.reqString("label"),
                dataMap.optString("give"),
                dataMap.optString("note"),
            )
        }.getOrElse { error ->
            return@post call.respond(
                HttpStatusCode.BadRequest,
                ApiResponse.fail<Unit>(error.message ?: "No se pudo publicar", "BAD_REQUEST"),
            )
        }
        call.respond(ApiResponse.ok(created, "Quedó publicado en Trueque"))
    }

    post("/api/v1/timebank/need/support") {
        val principal = requireNeighbor(call) ?: return@post
        val dataMap = JsonBody.receiveMap(call)
        val board = runCatching {
            supportNeed(db, principal.userId, dataMap.reqString("needId"), join = true)
        }.getOrElse { error ->
            return@post call.respond(
                HttpStatusCode.BadRequest,
                ApiResponse.fail<Unit>(error.message ?: "No se pudo apoyar", "BAD_REQUEST"),
            )
        }
        call.respond(ApiResponse.ok(board, "Apoyaste el pedido"))
    }

    post("/api/v1/timebank/need/invite") {
        val principal = requireNeighbor(call) ?: return@post
        val dataMap = JsonBody.receiveMap(call)
        val board = runCatching {
            inviteNeed(db, principal.userId, dataMap.reqString("needId"), dataMap.reqString("userId"))
        }.getOrElse { error ->
            return@post call.respond(
                HttpStatusCode.BadRequest,
                ApiResponse.fail<Unit>(error.message ?: "No se pudo invitar", "BAD_REQUEST"),
            )
        }
        call.respond(ApiResponse.ok(board, "Quedó la invitación"))
    }

    post("/api/v1/timebank/need/invite/suggest") {
        val principal = requireNeighbor(call) ?: return@post
        val dataMap = runCatching { JsonBody.receiveMap(call) }.getOrDefault(emptyMap())
        call.respond(ApiResponse.ok(inviteCandidates(db, principal.userId, dataMap.optString("query").orEmpty())))
    }

    post("/api/v1/timebank/need/unsupport") {
        val principal = requireNeighbor(call) ?: return@post
        val dataMap = JsonBody.receiveMap(call)
        val board = runCatching {
            supportNeed(db, principal.userId, dataMap.reqString("needId"), join = false)
        }.getOrElse { error ->
            return@post call.respond(
                HttpStatusCode.BadRequest,
                ApiResponse.fail<Unit>(error.message ?: "No se pudo sacar el apoyo", "BAD_REQUEST"),
            )
        }
        call.respond(ApiResponse.ok(board, "Sacaste el apoyo"))
    }

    post("/api/v1/timebank/board") {
        val principal = requireNeighbor(call) ?: return@post
        call.respond(ApiResponse.ok(timebankBoard(db, principal.userId)))
    }

    post("/api/v1/timebank/seekers") {
        val principal = requireNeighbor(call) ?: return@post
        call.respond(ApiResponse.ok(seekersForMyOffers(db, principal.userId)))
    }

    post("/api/v1/timebank/start") {
        val principal = requireNeighbor(call) ?: return@post
        val dataMap = JsonBody.receiveMap(call)
        val slug = dataMap.optString("tagSlug")?.trim().orEmpty().ifBlank { MENSAJE_TAG }
        val thread = startMatch(db, principal.userId, dataMap.reqString("userId"), slug)
            ?: return@post call.respond(HttpStatusCode.BadRequest, ApiResponse.fail<Unit>("No se pudo abrir el trueque", "BAD_REQUEST"))
        call.respond(ApiResponse.ok(thread, "Hilo abierto"))
    }

    post("/api/v1/timebank/inbox") {
        val principal = requireNeighbor(call) ?: return@post
        call.respond(ApiResponse.ok(timebankInbox(db, principal.userId)))
    }

    post("/api/v1/timebank/messages") {
        val principal = requireNeighbor(call) ?: return@post
        val dataMap = JsonBody.receiveMap(call)
        val matchId = dataMap.reqString("matchId")
        if (!canSeeMatch(db, principal.userId, matchId)) {
            return@post call.respond(HttpStatusCode.Forbidden, ApiResponse.fail<Unit>("Ese hilo no es tuyo", "FORBIDDEN"))
        }
        call.respond(ApiResponse.ok(timebankMessages(db, principal.userId, matchId)))
    }

    post("/api/v1/timebank/message/send") {
        val principal = requireNeighbor(call) ?: return@post
        val dataMap = JsonBody.receiveMap(call)
        val matchId = dataMap.reqString("matchId")
        val body = dataMap.reqString("body").trim()
        if (body.isBlank()) {
            return@post call.respond(HttpStatusCode.BadRequest, ApiResponse.fail<Unit>("Escribí un mensaje", "BAD_REQUEST"))
        }
        if (!canSeeMatch(db, principal.userId, matchId)) {
            return@post call.respond(HttpStatusCode.Forbidden, ApiResponse.fail<Unit>("Ese hilo no es tuyo", "FORBIDDEN"))
        }
        val saved = sendTimebankMessage(db, principal.userId, matchId, body)
        call.respond(ApiResponse.ok(saved, "Enviado"))
    }
}

private suspend fun requireNeighbor(call: io.ktor.server.application.ApplicationCall): AuthPrincipal? {
    val principal = call.principal<AuthPrincipal>()
    if (principal == null) {
        call.respond(HttpStatusCode.Unauthorized, ApiResponse.fail<Unit>("Token requerido", "UNAUTHENTICATED"))
    }
    return principal
}

private fun walletSummary(db: Database, userId: String): WalletSummary = db.withConnection { connection ->
    val points = connection.prepareStatement("SELECT community_points FROM users WHERE id = ?::uuid").use { stmt ->
        stmt.setString(1, userId)
        stmt.executeQuery().use { rs -> if (rs.next()) rs.getInt(1) else 0 }
    }
    val activity = connection.prepareStatement(
        """
        SELECT id::text, kind::text, points_awarded,
               EXTRACT(EPOCH FROM verified_at) * 1000 AS created_ms
        FROM action_outcomes
        WHERE actor_user_id = ?::uuid OR beneficiary_user_id = ?::uuid
        ORDER BY verified_at DESC
        LIMIT 20
        """.trimIndent(),
    ).use { stmt ->
        stmt.setString(1, userId)
        stmt.setString(2, userId)
        stmt.executeQuery().use { rs ->
            buildList {
                while (rs.next()) {
                    val kind = rs.getString("kind")
                    add(
                        WalletActivity(
                            id = rs.getString("id"),
                            title = walletTitle(kind),
                            delta = rs.getInt("points_awarded"),
                            createdAtEpochMs = rs.getLong("created_ms"),
                        ),
                    )
                }
            }
        }
    }
    val ranking = connection.prepareStatement(
        """
        SELECT id::text, display_name, photo_url, community_points, role::text
        FROM users
        WHERE status = 'ACTIVE'
        ORDER BY community_points DESC, display_name
        LIMIT 12
        """.trimIndent(),
    ).use { stmt ->
        stmt.executeQuery().use { rs ->
            buildList {
                while (rs.next()) {
                    val pts = rs.getInt("community_points")
                    add(
                        NeighborCard(
                            userId = rs.getString("id"),
                            displayName = rs.getString("display_name"),
                            photoUrl = rs.stringOrNull("photo_url"),
                            communityPoints = pts,
                            role = rs.getString("role"),
                            levelLabel = communityLevelLabel(pts),
                        ),
                    )
                }
            }
        }
    }
    val goal = 3000
    WalletSummary(
        points = points,
        nextGoal = goal,
        levelLabel = communityLevelLabel(points),
        missing = (goal - points).coerceAtLeast(0),
        activity = activity,
        ranking = ranking,
    )
}

private fun walletTitle(kind: String): String = when (kind) {
    "PARKING_HANDOFF" -> "Cesión de estacionamiento"
    "ANIMAL_RESOLVED" -> "Se logró: mascota"
    "VOLUNTEER_CHECKED_IN" -> "Check-in de voluntariado"
    "TIMEBANK_CLOSED" -> "Trueque cerrado"
    "CAUSE_DELIVERED" -> "Causa entregada"
    else -> "Se logró"
}

private fun noticeInbox(db: Database, userId: String): List<CommunityNotice> = db.withConnection { connection ->
    connection.prepareStatement(
        """
        WITH items AS (
            SELECT
                'mention:' || p.id::text AS notice_key,
                'MENTION' AS kind,
                COALESCE(u.display_name, 'Alguien de la comunidad') || ' te mencionó' AS title,
                LEFT(p.body, 160) AS body,
                p.id::text AS post_id,
                NULL::text AS match_id,
                NULL::text AS listing_id,
                EXTRACT(EPOCH FROM p.created_at) * 1000 AS created_ms
            FROM post_people pp
            JOIN social_posts p ON p.id = pp.post_id
            LEFT JOIN users u ON u.id = p.author_user_id
            WHERE pp.user_id = ?::uuid AND pp.role IN ('PROTAGONIST', 'PARTICIPANT')
              AND p.author_user_id IS DISTINCT FROM ?::uuid
              AND p.moderation_status = 'VISIBLE'
            UNION ALL
            SELECT
                'alert:' || a.id::text,
                'ALERT',
                a.title,
                LEFT(a.description, 160),
                p.id::text,
                NULL,
                a.id::text,
                EXTRACT(EPOCH FROM a.created_at) * 1000
            FROM animal_listings a
            LEFT JOIN social_posts p ON p.listing_id = a.id
            WHERE a.kind = 'LOST' AND a.resolved = FALSE
            UNION ALL
            SELECT
                'match:' || m.id::text,
                'MATCH',
                'Trueque: ' || t.label,
                COALESCE(peer.display_name, 'Un vecino') || ' · ' || t.label,
                NULL,
                m.id::text,
                NULL,
                EXTRACT(EPOCH FROM m.created_at) * 1000
            FROM timebank_matches m
            JOIN skill_tags t ON t.id = m.tag_id
            JOIN users peer ON peer.id = CASE WHEN m.requester_id = ?::uuid THEN m.provider_id ELSE m.requester_id END
            WHERE m.requester_id = ?::uuid OR m.provider_id = ?::uuid
            UNION ALL
            SELECT
                'support:' || s.need_id::text || ':' || s.user_id::text,
                'MATCH',
                COALESCE(u.display_name, 'Un vecino') || ' apoyó tu pedido',
                'Trueque · ' || t.label,
                n.post_id::text,
                NULL,
                NULL,
                EXTRACT(EPOCH FROM s.created_at) * 1000
            FROM timebank_need_supports s
            JOIN timebank_needs n ON n.id = s.need_id AND n.open
            JOIN skill_tags t ON t.id = n.tag_id
            JOIN users u ON u.id = s.user_id
            WHERE n.user_id = ?::uuid
            UNION ALL
            SELECT
                'invite:' || i.need_id::text || ':' || i.inviter_id::text,
                'MATCH',
                COALESCE(u.display_name, 'Un vecino') || ' te invita a apoyar',
                'Trueque · ' || t.label,
                n.post_id::text,
                NULL,
                NULL,
                EXTRACT(EPOCH FROM i.created_at) * 1000
            FROM timebank_need_invites i
            JOIN timebank_needs n ON n.id = i.need_id AND n.open
            JOIN skill_tags t ON t.id = n.tag_id
            JOIN users u ON u.id = i.inviter_id
            WHERE i.invitee_id = ?::uuid
              AND NOT EXISTS (
                  SELECT 1 FROM timebank_need_supports s
                  WHERE s.need_id = i.need_id AND s.user_id = i.invitee_id
              )
            UNION ALL
            SELECT
                'karma:' || o.id::text,
                'KARMA',
                'Se logró',
                o.kind::text,
                o.post_id::text,
                NULL,
                NULL,
                EXTRACT(EPOCH FROM o.verified_at) * 1000
            FROM action_outcomes o
            WHERE o.beneficiary_user_id = ?::uuid OR o.actor_user_id = ?::uuid
        )
        SELECT i.*, EXISTS (
            SELECT 1 FROM notice_reads r
            WHERE r.user_id = ?::uuid AND r.notice_key = i.notice_key
        ) AS read
        FROM items i
        ORDER BY i.created_ms DESC
        LIMIT 40
        """.trimIndent(),
    ).use { stmt ->
        repeat(10) { stmt.setString(it + 1, userId) }
        stmt.executeQuery().use { rs ->
            buildList {
                while (rs.next()) {
                    val kind = rs.getString("kind")
                    add(
                        CommunityNotice(
                            id = rs.getString("notice_key"),
                            kind = kind,
                            title = if (kind == "KARMA") walletTitle(rs.getString("body")) else rs.getString("title"),
                            body = if (kind == "KARMA") "Puntos por un hecho verificado." else rs.getString("body"),
                            postId = rs.stringOrNull("post_id"),
                            matchId = rs.stringOrNull("match_id"),
                            listingId = rs.stringOrNull("listing_id"),
                            createdAtEpochMs = rs.getLong("created_ms"),
                            read = rs.getBoolean("read"),
                        ),
                    )
                }
            }
        }
    }
}

private fun markNoticesRead(db: Database, userId: String, noticeId: String?) {
    db.withConnection { connection ->
        if (noticeId.isNullOrBlank()) {
            // Marca lo que hoy está en bandeja.
            noticeInbox(db, userId).forEach { item ->
                connection.prepareStatement(
                    "INSERT INTO notice_reads (user_id, notice_key) VALUES (?::uuid, ?) ON CONFLICT DO NOTHING",
                ).use { stmt ->
                    stmt.setString(1, userId)
                    stmt.setString(2, item.id)
                    stmt.executeUpdate()
                }
            }
        } else {
            connection.prepareStatement(
                "INSERT INTO notice_reads (user_id, notice_key) VALUES (?::uuid, ?) ON CONFLICT DO NOTHING",
            ).use { stmt ->
                stmt.setString(1, userId)
                stmt.setString(2, noticeId)
                stmt.executeUpdate()
            }
        }
    }
}

private fun skillTags(db: Database, userId: String): List<SkillTagDto> = db.withConnection { connection ->
    connection.prepareStatement(
        """
        SELECT t.slug, t.label,
               COALESCE(us.offered, FALSE) AS offered,
               COALESCE(us.requested, FALSE) AS requested
        FROM skill_tags t
        LEFT JOIN user_skills us ON us.tag_id = t.id AND us.user_id = ?::uuid
        ORDER BY t.label
        """.trimIndent(),
    ).use { stmt ->
        stmt.setString(1, userId)
        stmt.executeQuery().use { rs ->
            buildList {
                while (rs.next()) {
                    add(
                        SkillTagDto(
                            slug = rs.getString("slug"),
                            label = rs.getString("label"),
                            offered = rs.getBoolean("offered"),
                            requested = rs.getBoolean("requested"),
                        ),
                    )
                }
            }
        }
    }
}

private fun upsertSkill(db: Database, userId: String, slug: String, offered: Boolean, requested: Boolean) {
    db.withConnection { connection ->
        val tagId = connection.prepareStatement("SELECT id::text FROM skill_tags WHERE slug = ?").use { stmt ->
            stmt.setString(1, slug)
            stmt.executeQuery().use { rs -> if (rs.next()) rs.getString(1) else null }
        } ?: error("Ese saber no está en el banco")
        if (!offered && !requested) {
            connection.prepareStatement(
                "DELETE FROM user_skills WHERE user_id = ?::uuid AND tag_id = ?::uuid",
            ).use { stmt ->
                stmt.setString(1, userId)
                stmt.setString(2, tagId)
                stmt.executeUpdate()
            }
            closeNeedsForTag(connection, userId, tagId)
            return@withConnection
        }
        connection.prepareStatement(
            """
            INSERT INTO user_skills (user_id, tag_id, offered, requested)
            VALUES (?::uuid, ?::uuid, ?, ?)
            ON CONFLICT (user_id, tag_id) DO UPDATE SET
                offered = EXCLUDED.offered,
                requested = EXCLUDED.requested
            """.trimIndent(),
        ).use { stmt ->
            stmt.setString(1, userId)
            stmt.setString(2, tagId)
            stmt.setBoolean(3, offered)
            stmt.setBoolean(4, requested)
            stmt.executeUpdate()
        }
        if (!requested) closeNeedsForTag(connection, userId, tagId)
    }
    refreshNeedBodiesForUser(db, userId)
}

private fun startMatch(db: Database, me: String, peerId: String, slug: String): TimebankThread? {
    if (me == peerId) return null
    if (slug == MENSAJE_TAG) ensureMensajeTag(db)
    return db.withConnection { connection ->
        val tag = connection.prepareStatement("SELECT id::text, label FROM skill_tags WHERE slug = ?").use { stmt ->
            stmt.setString(1, slug)
            stmt.executeQuery().use { rs ->
                if (rs.next()) rs.getString(1) to rs.getString(2) else null
            }
        } ?: return@withConnection null
        val existing = connection.prepareStatement(
            """
            SELECT id::text FROM timebank_matches
            WHERE tag_id = ?::uuid AND status <> 'CLOSED'
              AND ((requester_id = ?::uuid AND provider_id = ?::uuid)
                OR (requester_id = ?::uuid AND provider_id = ?::uuid))
            LIMIT 1
            """.trimIndent(),
        ).use { stmt ->
            stmt.setString(1, tag.first)
            stmt.setString(2, me)
            stmt.setString(3, peerId)
            stmt.setString(4, peerId)
            stmt.setString(5, me)
            stmt.executeQuery().use { rs -> if (rs.next()) rs.getString(1) else null }
        }
        val matchId = existing ?: UUID.randomUUID().toString().also { id ->
            connection.prepareStatement(
                """
                INSERT INTO timebank_matches (id, requester_id, provider_id, tag_id, status, chat_enabled)
                VALUES (?::uuid, ?::uuid, ?::uuid, ?::uuid, 'ACTIVE', TRUE)
                """.trimIndent(),
            ).use { stmt ->
                stmt.setString(1, id)
                stmt.setString(2, me)
                stmt.setString(3, peerId)
                stmt.setString(4, tag.first)
                stmt.executeUpdate()
            }
        }
        val peerName = connection.prepareStatement("SELECT display_name FROM users WHERE id = ?::uuid").use { stmt ->
            stmt.setString(1, peerId)
            stmt.executeQuery().use { rs -> if (rs.next()) rs.getString(1) else "Vecino" }
        }
        TimebankThread(
            matchId = matchId,
            peerUserId = peerId,
            peerName = peerName,
            tag = slug,
            tagLabel = tag.second,
            status = "ACTIVE",
        )
    }
}

private fun timebankInbox(db: Database, userId: String): List<TimebankThread> = db.withConnection { connection ->
    connection.prepareStatement(
        """
        SELECT m.id::text, m.status::text, t.slug, t.label,
               CASE WHEN m.requester_id = ?::uuid THEN m.provider_id ELSE m.requester_id END AS peer_id,
               peer.display_name,
               COALESCE(
                   (SELECT body FROM timebank_messages msg WHERE msg.match_id = m.id ORDER BY created_at DESC LIMIT 1),
                   ''
               ) AS last_body,
               COALESCE(
                   (SELECT EXTRACT(EPOCH FROM created_at) * 1000 FROM timebank_messages msg WHERE msg.match_id = m.id ORDER BY created_at DESC LIMIT 1),
                   EXTRACT(EPOCH FROM m.created_at) * 1000
               ) AS last_ms
        FROM timebank_matches m
        JOIN skill_tags t ON t.id = m.tag_id
        JOIN users peer ON peer.id = CASE WHEN m.requester_id = ?::uuid THEN m.provider_id ELSE m.requester_id END
        WHERE m.requester_id = ?::uuid OR m.provider_id = ?::uuid
        ORDER BY last_ms DESC
        """.trimIndent(),
    ).use { stmt ->
        repeat(4) { stmt.setString(it + 1, userId) }
        stmt.executeQuery().use { rs ->
            buildList {
                while (rs.next()) {
                    add(
                        TimebankThread(
                            matchId = rs.getString("id"),
                            peerUserId = rs.getString("peer_id"),
                            peerName = rs.getString("display_name"),
                            tag = rs.getString("slug"),
                            tagLabel = rs.getString("label"),
                            lastBody = rs.getString("last_body").orEmpty(),
                            lastAtEpochMs = rs.getLong("last_ms"),
                            status = rs.getString("status"),
                        ),
                    )
                }
            }
        }
    }
}

private fun canSeeMatch(db: Database, userId: String, matchId: String): Boolean = db.withConnection { connection ->
    connection.prepareStatement(
        "SELECT 1 FROM timebank_matches WHERE id = ?::uuid AND (requester_id = ?::uuid OR provider_id = ?::uuid)",
    ).use { stmt ->
        stmt.setString(1, matchId)
        stmt.setString(2, userId)
        stmt.setString(3, userId)
        stmt.executeQuery().use { it.next() }
    }
}

private fun timebankMessages(db: Database, userId: String, matchId: String): List<TimebankMessageDto> =
    db.withConnection { connection ->
        connection.prepareStatement(
            """
            SELECT m.id::text, m.match_id::text, m.sender_id::text, m.body,
                   EXTRACT(EPOCH FROM m.created_at) * 1000 AS created_ms,
                   u.display_name
            FROM timebank_messages m
            JOIN users u ON u.id = m.sender_id
            WHERE m.match_id = ?::uuid
            ORDER BY m.created_at
            """.trimIndent(),
        ).use { stmt ->
            stmt.setString(1, matchId)
            stmt.executeQuery().use { rs ->
                buildList {
                    while (rs.next()) {
                        val sender = rs.getString("sender_id")
                        add(
                            TimebankMessageDto(
                                id = rs.getString("id"),
                                matchId = rs.getString("match_id"),
                                senderId = sender,
                                senderName = rs.getString("display_name"),
                                body = rs.getString("body"),
                                createdAtEpochMs = rs.getLong("created_ms"),
                                mine = sender == userId,
                            ),
                        )
                    }
                }
            }
        }
    }

private fun sendTimebankMessage(db: Database, userId: String, matchId: String, body: String): TimebankMessageDto {
    val id = UUID.randomUUID().toString()
    db.withConnection { connection ->
        connection.prepareStatement(
            "INSERT INTO timebank_messages (id, match_id, sender_id, body) VALUES (?::uuid, ?::uuid, ?::uuid, ?)",
        ).use { stmt ->
            stmt.setString(1, id)
            stmt.setString(2, matchId)
            stmt.setString(3, userId)
            stmt.setString(4, body.take(800))
            stmt.executeUpdate()
        }
    }
    return timebankMessages(db, userId, matchId).last { it.id == id }
}

private fun createOfferedSkill(db: Database, userId: String, rawLabel: String): List<SkillTagDto> {
    val slug = ensureSkillTag(db, rawLabel)
    val requested = currentSkillFlag(db, userId, slug, offered = false)
    upsertSkill(db, userId, slug, offered = true, requested = requested)
    return skillTags(db, userId)
}

private fun publishNeed(
    db: Database,
    hub: RealtimeHub,
    userId: String,
    rawLabel: String,
    rawGive: String?,
    rawNote: String?,
): TimebankBoard {
    val slug = ensureSkillTag(db, rawLabel)
    val label = skillLabel(db, slug)
    val extraGive = rawGive?.trim()?.takeIf { it.isNotEmpty() }
    if (!extraGive.isNullOrBlank() && !SkillCanon.sameTrade(label, extraGive)) {
        createOfferedSkill(db, userId, extraGive)
    }
    val offered = currentSkillFlag(db, userId, slug, offered = true)
    upsertSkill(db, userId, slug, offered = offered, requested = true)
    val note = rawNote?.trim()?.takeIf { it.isNotEmpty() }
    val alreadyOpen = db.withConnection { connection ->
        connection.prepareStatement(
            """
            SELECT n.id::text AS need_id, n.post_id::text AS post_id
            FROM timebank_needs n
            JOIN skill_tags t ON t.id = n.tag_id
            WHERE n.user_id = ?::uuid AND t.slug = ? AND n.open
            LIMIT 1
            """.trimIndent(),
        ).use { stmt ->
            stmt.setString(1, userId)
            stmt.setString(2, slug)
            stmt.executeQuery().use { rs ->
                if (rs.next()) rs.getString("need_id") to rs.stringOrNull("post_id") else null
            }
        }
    }
    if (alreadyOpen != null) {
        db.withConnection { connection ->
            connection.prepareStatement(
                "UPDATE timebank_needs SET note = ? WHERE id = ?::uuid",
            ).use { stmt ->
                if (note == null) stmt.setNull(1, java.sql.Types.VARCHAR) else stmt.setString(1, note)
                stmt.setString(2, alreadyOpen.first)
                stmt.executeUpdate()
            }
        }
        refreshNeedBodiesForNeed(db, alreadyOpen.first)
    } else {
        val postId = UUID.randomUUID().toString()
        val needId = UUID.randomUUID().toString()
        val offers = offeredLabelsForNeedAuthor(db, userId, slug)
        val body = needBody(label, offers, note)
        db.withConnection { connection ->
            val tagId = connection.prepareStatement("SELECT id::text FROM skill_tags WHERE slug = ?").use { stmt ->
                stmt.setString(1, slug)
                stmt.executeQuery().use { rs -> if (rs.next()) rs.getString(1) else null }
            } ?: error("Ese saber no está en el banco")
            connection.prepareStatement(
                """
                INSERT INTO social_posts (
                    id, author_kind, author_user_id, body, media_urls, topic, placement
                ) VALUES (
                    ?::uuid, 'USER', ?::uuid, ?, ARRAY[]::text[], 'Trueque', 'ORGANIC'
                )
                """.trimIndent(),
            ).use { stmt ->
                stmt.setString(1, postId)
                stmt.setString(2, userId)
                stmt.setString(3, body)
                stmt.executeUpdate()
            }
            connection.prepareStatement(
                "INSERT INTO post_people (post_id, user_id, role) VALUES (?::uuid, ?::uuid, 'AUTHOR')",
            ).use { stmt ->
                stmt.setString(1, postId)
                stmt.setString(2, userId)
                stmt.executeUpdate()
            }
            connection.prepareStatement(
                """
                INSERT INTO timebank_needs (id, user_id, tag_id, post_id, note, open)
                VALUES (?::uuid, ?::uuid, ?::uuid, ?::uuid, ?, TRUE)
                """.trimIndent(),
            ).use { stmt ->
                stmt.setString(1, needId)
                stmt.setString(2, userId)
                stmt.setString(3, tagId)
                stmt.setString(4, postId)
                if (note == null) stmt.setNull(5, java.sql.Types.VARCHAR) else stmt.setString(5, note)
                stmt.executeUpdate()
            }
        }
        hub.projectSocialPost(
            postId,
            WsCodec.json.encodeToString(
                SocialLiveCounters.serializer(),
                SocialLiveCounters(
                    id = postId,
                    commentCount = 0,
                    impactCount = 0,
                    authorUserId = userId,
                    body = body,
                    tag = "Trueque",
                    createdAtEpochMs = currentEpochMs(),
                ),
            ),
        )
    }
    return timebankBoard(db, userId)
}

private fun supportNeed(db: Database, userId: String, needId: String, join: Boolean): TimebankBoard {
    val need = db.withConnection { connection ->
        connection.prepareStatement(
            """
            SELECT n.user_id::text AS author_id, t.slug, n.open
            FROM timebank_needs n
            JOIN skill_tags t ON t.id = n.tag_id
            WHERE n.id = ?::uuid
            """.trimIndent(),
        ).use { stmt ->
            stmt.setString(1, needId)
            stmt.executeQuery().use { rs ->
                if (!rs.next()) null
                else Triple(rs.getString("author_id"), rs.getString("slug"), rs.getBoolean("open"))
            }
        }
    } ?: error("Ese pedido no está")
    if (need.first == userId) error("No podés apoyar tu propio pedido")
    if (!need.third) error("Ese pedido ya se cerró")
    db.withConnection { connection ->
        if (join) {
            connection.prepareStatement(
                """
                INSERT INTO timebank_need_supports (need_id, user_id)
                VALUES (?::uuid, ?::uuid)
                ON CONFLICT (need_id, user_id) DO NOTHING
                """.trimIndent(),
            ).use { stmt ->
                stmt.setString(1, needId)
                stmt.setString(2, userId)
                stmt.executeUpdate()
            }
        } else {
            connection.prepareStatement(
                "DELETE FROM timebank_need_supports WHERE need_id = ?::uuid AND user_id = ?::uuid",
            ).use { stmt ->
                stmt.setString(1, needId)
                stmt.setString(2, userId)
                stmt.executeUpdate()
            }
        }
    }
    if (join) {
        val offered = currentSkillFlag(db, userId, need.second, offered = true)
        upsertSkill(db, userId, need.second, offered = offered, requested = true)
    } else {
        refreshNeedBodiesForNeed(db, needId)
    }
    return timebankBoard(db, userId)
}

private fun inviteNeed(db: Database, me: String, needId: String, inviteeId: String): TimebankBoard {
    if (me == inviteeId) error("Invitá a otra persona")
    val need = db.withConnection { connection ->
        connection.prepareStatement(
            """
            SELECT n.user_id::text AS author_id, n.open, t.label
            FROM timebank_needs n
            JOIN skill_tags t ON t.id = n.tag_id
            WHERE n.id = ?::uuid
            """.trimIndent(),
        ).use { stmt ->
            stmt.setString(1, needId)
            stmt.executeQuery().use { rs ->
                if (!rs.next()) null
                else Triple(rs.getString("author_id"), rs.getBoolean("open"), rs.getString("label"))
            }
        }
    } ?: error("Ese pedido no está")
    if (need.first != me) error("Solo quien publicó puede invitar")
    if (!need.second) error("Ese pedido ya se cerró")
    val exists = db.withConnection { connection ->
        connection.prepareStatement("SELECT 1 FROM users WHERE id = ?::uuid AND status = 'ACTIVE'").use { stmt ->
            stmt.setString(1, inviteeId)
            stmt.executeQuery().use { it.next() }
        }
    }
    if (!exists) error("Ese vecino no está")
    val already = db.withConnection { connection ->
        connection.prepareStatement(
            "SELECT 1 FROM timebank_need_supports WHERE need_id = ?::uuid AND user_id = ?::uuid",
        ).use { stmt ->
            stmt.setString(1, needId)
            stmt.setString(2, inviteeId)
            stmt.executeQuery().use { it.next() }
        }
    }
    if (already) error("Esa persona ya apoyó el pedido")
    db.withConnection { connection ->
        connection.prepareStatement(
            """
            INSERT INTO timebank_need_invites (need_id, invitee_id, inviter_id)
            VALUES (?::uuid, ?::uuid, ?::uuid)
            ON CONFLICT (need_id, invitee_id) DO UPDATE SET
                inviter_id = EXCLUDED.inviter_id,
                created_at = now()
            """.trimIndent(),
        ).use { stmt ->
            stmt.setString(1, needId)
            stmt.setString(2, inviteeId)
            stmt.setString(3, me)
            stmt.executeUpdate()
        }
    }
    return timebankBoard(db, me)
}

private fun inviteCandidates(db: Database, userId: String, query: String): List<NeighborCard> =
    db.withConnection { connection ->
        val q = query.trim()
        val sql = if (q.isEmpty()) {
            """
            SELECT u.id::text AS user_id, u.display_name, u.photo_url, u.community_points, u.role::text AS role
            FROM follows f
            JOIN users u ON u.id = f.followed_id AND u.status = 'ACTIVE'
            WHERE f.follower_id = ?::uuid AND u.id <> ?::uuid
            ORDER BY u.display_name
            LIMIT 12
            """.trimIndent()
        } else {
            """
            SELECT u.id::text AS user_id, u.display_name, u.photo_url, u.community_points, u.role::text AS role
            FROM users u
            WHERE u.status = 'ACTIVE' AND u.id <> ?::uuid AND u.display_name ILIKE ?
            ORDER BY u.community_points DESC, u.display_name
            LIMIT 12
            """.trimIndent()
        }
        connection.prepareStatement(sql).use { stmt ->
            stmt.setString(1, userId)
            if (q.isEmpty()) {
                stmt.setString(2, userId)
            } else {
                stmt.setString(2, "%$q%")
            }
            stmt.executeQuery().use { rs ->
                buildList {
                    while (rs.next()) {
                        val pts = rs.getInt("community_points")
                        add(
                            NeighborCard(
                                userId = rs.getString("user_id"),
                                displayName = rs.getString("display_name"),
                                photoUrl = rs.stringOrNull("photo_url"),
                                communityPoints = pts,
                                role = rs.getString("role"),
                                levelLabel = communityLevelLabel(pts),
                            ),
                        )
                    }
                }
            }
        }
    }

private fun needBody(need: String, offers: List<String>, note: String?): String = buildString {
    append("Necesito $need.")
    if (offers.isNotEmpty()) append(" A cambio: ${offers.joinToString(", ")}.")
    if (!note.isNullOrBlank()) append(" $note")
}

private fun offeredLabelsForNeedAuthor(db: Database, userId: String, excludeSlug: String): List<String> =
    db.withConnection { connection ->
        connection.prepareStatement(
            """
            SELECT t.label
            FROM user_skills us
            JOIN skill_tags t ON t.id = us.tag_id
            WHERE us.user_id = ?::uuid AND us.offered AND t.slug <> 'mensaje' AND t.slug <> ?
            ORDER BY t.label
            """.trimIndent(),
        ).use { stmt ->
            stmt.setString(1, userId)
            stmt.setString(2, excludeSlug)
            stmt.executeQuery().use { rs ->
                buildList { while (rs.next()) add(rs.getString(1)) }
            }
        }
    }

private fun offeredLabelsForNeed(db: Database, needId: String): List<String> = db.withConnection { connection ->
    connection.prepareStatement(
        """
        SELECT DISTINCT t.label
        FROM timebank_needs n
        JOIN (
            SELECT n2.user_id AS uid FROM timebank_needs n2 WHERE n2.id = ?::uuid
            UNION
            SELECT s.user_id FROM timebank_need_supports s WHERE s.need_id = ?::uuid
        ) people ON TRUE
        JOIN user_skills us ON us.user_id = people.uid AND us.offered
        JOIN skill_tags t ON t.id = us.tag_id AND t.slug <> 'mensaje'
        WHERE n.id = ?::uuid AND us.tag_id IS DISTINCT FROM n.tag_id
        ORDER BY t.label
        """.trimIndent(),
    ).use { stmt ->
        stmt.setString(1, needId)
        stmt.setString(2, needId)
        stmt.setString(3, needId)
        stmt.executeQuery().use { rs ->
            buildList { while (rs.next()) add(rs.getString(1)) }
        }
    }
}

private fun refreshNeedBodiesForNeed(db: Database, needId: String) {
    val row = db.withConnection { connection ->
        connection.prepareStatement(
            """
            SELECT n.post_id::text AS post_id, t.label, n.note
            FROM timebank_needs n
            JOIN skill_tags t ON t.id = n.tag_id
            WHERE n.id = ?::uuid AND n.open
            """.trimIndent(),
        ).use { stmt ->
            stmt.setString(1, needId)
            stmt.executeQuery().use { rs ->
                if (!rs.next()) null
                else Triple(rs.stringOrNull("post_id"), rs.getString("label"), rs.stringOrNull("note"))
            }
        }
    } ?: return
    val postId = row.first ?: return
    val body = needBody(row.second, offeredLabelsForNeed(db, needId), row.third)
    db.withConnection { connection ->
        connection.prepareStatement(
            "UPDATE social_posts SET body = ?, updated_at = now() WHERE id = ?::uuid",
        ).use { stmt ->
            stmt.setString(1, body)
            stmt.setString(2, postId)
            stmt.executeUpdate()
        }
    }
}

private fun refreshNeedBodiesForUser(db: Database, userId: String) {
    val ids = db.withConnection { connection ->
        connection.prepareStatement(
            """
            SELECT n.id::text
            FROM timebank_needs n
            WHERE n.open AND (
                n.user_id = ?::uuid
                OR EXISTS (
                    SELECT 1 FROM timebank_need_supports s
                    WHERE s.need_id = n.id AND s.user_id = ?::uuid
                )
            )
            """.trimIndent(),
        ).use { stmt ->
            stmt.setString(1, userId)
            stmt.setString(2, userId)
            stmt.executeQuery().use { rs ->
                buildList { while (rs.next()) add(rs.getString(1)) }
            }
        }
    }
    ids.forEach { refreshNeedBodiesForNeed(db, it) }
}

private fun timebankBoard(db: Database, userId: String): TimebankBoard {
    val tags = skillTags(db, userId)
    val posts = loadNeedPosts(db, userId)
    return TimebankBoard(
        tags = tags,
        mine = posts.filter { it.mine },
        seekingMine = posts.filter { !it.mine && it.matchesMyOffer },
        others = posts.filter { !it.mine && !it.matchesMyOffer },
        helpers = helpersForRequested(db, userId),
    )
}

private fun loadNeedPosts(db: Database, userId: String): List<TimebankNeedPost> {
    val rows = db.withConnection { connection ->
        connection.prepareStatement(
            """
            SELECT n.id::text AS need_id, n.post_id::text AS post_id, n.note, t.slug, t.label,
                   u.id::text AS author_id, u.display_name, u.photo_url,
                   COALESCE(p.body, 'Necesito ' || t.label || '.') AS body,
                   EXTRACT(EPOCH FROM n.created_at) * 1000 AS created_ms,
                   (n.user_id = ?::uuid) AS mine,
                   EXISTS (
                       SELECT 1 FROM user_skills mine
                       WHERE mine.user_id = ?::uuid AND mine.tag_id = n.tag_id AND mine.offered
                   ) AS matches_me
            FROM timebank_needs n
            JOIN skill_tags t ON t.id = n.tag_id
            JOIN users u ON u.id = n.user_id
            LEFT JOIN social_posts p ON p.id = n.post_id
            WHERE n.open
            ORDER BY matches_me DESC, n.created_at DESC
            LIMIT 50
            """.trimIndent(),
        ).use { stmt ->
            stmt.setString(1, userId)
            stmt.setString(2, userId)
            stmt.executeQuery().use { rs ->
                buildList {
                    while (rs.next()) {
                        add(
                            TimebankNeedPost(
                                needId = rs.getString("need_id"),
                                postId = rs.stringOrNull("post_id"),
                                note = rs.stringOrNull("note"),
                                tag = rs.getString("slug"),
                                tagLabel = rs.getString("label"),
                                userId = rs.getString("author_id"),
                                displayName = rs.getString("display_name"),
                                photoUrl = rs.stringOrNull("photo_url"),
                                body = rs.getString("body"),
                                createdAtEpochMs = rs.getLong("created_ms"),
                                mine = rs.getBoolean("mine"),
                                matchesMyOffer = rs.getBoolean("matches_me"),
                            ),
                        )
                    }
                }
            }
        }
    }
    val ids = rows.map { it.needId }
    val labels = loadGiveLabelsByNeed(db, ids)
    val supports = loadSupportsByNeed(db, ids)
    val invited = loadInvitedNeedIds(db, userId, ids)
    return rows.map { row ->
        val offers = labels[row.needId].orEmpty()
        val people = supports[row.needId].orEmpty()
        row.copy(
            giveLabels = offers,
            giveLabel = offers.joinToString(" · ").ifBlank { null },
            supporters = people,
            supportCount = people.size,
            viewerSupported = people.any { it.userId == userId },
            viewerInvited = invited.contains(row.needId),
        )
    }
}

private fun loadInvitedNeedIds(db: Database, userId: String, ids: List<String>): Set<String> {
    if (ids.isEmpty()) return emptySet()
    val placeholders = ids.joinToString(",") { "?::uuid" }
    return db.withConnection { connection ->
        connection.prepareStatement(
            """
            SELECT need_id::text
            FROM timebank_need_invites
            WHERE invitee_id = ?::uuid AND need_id IN ($placeholders)
            """.trimIndent(),
        ).use { stmt ->
            stmt.setString(1, userId)
            ids.forEachIndexed { index, id -> stmt.setString(index + 2, id) }
            stmt.executeQuery().use { rs ->
                buildSet { while (rs.next()) add(rs.getString(1)) }
            }
        }
    }
}

private fun loadGiveLabelsByNeed(db: Database, ids: List<String>): Map<String, List<String>> {
    if (ids.isEmpty()) return emptyMap()
    val placeholders = ids.joinToString(",") { "?::uuid" }
    return db.withConnection { connection ->
        connection.prepareStatement(
            """
            SELECT n.id::text AS need_id, t.label
            FROM timebank_needs n
            JOIN (
                SELECT n2.id AS need_id, n2.user_id AS uid
                FROM timebank_needs n2
                WHERE n2.id IN ($placeholders)
                UNION
                SELECT s.need_id, s.user_id
                FROM timebank_need_supports s
                WHERE s.need_id IN ($placeholders)
            ) people ON people.need_id = n.id
            JOIN user_skills us ON us.user_id = people.uid AND us.offered
            JOIN skill_tags t ON t.id = us.tag_id AND t.slug <> 'mensaje'
            WHERE n.id IN ($placeholders) AND us.tag_id IS DISTINCT FROM n.tag_id
            ORDER BY t.label
            """.trimIndent(),
        ).use { stmt ->
            ids.forEachIndexed { index, id -> stmt.setString(index + 1, id) }
            ids.forEachIndexed { index, id -> stmt.setString(ids.size + index + 1, id) }
            ids.forEachIndexed { index, id -> stmt.setString(ids.size * 2 + index + 1, id) }
            stmt.executeQuery().use { rs ->
                buildMap<String, MutableList<String>> {
                    while (rs.next()) {
                        getOrPut(rs.getString("need_id")) { mutableListOf() }.add(rs.getString("label"))
                    }
                }.mapValues { (_, labels) -> labels.distinct() }
            }
        }
    }
}

private fun loadSupportsByNeed(db: Database, ids: List<String>): Map<String, List<TimebankNeedSupport>> {
    if (ids.isEmpty()) return emptyMap()
    val placeholders = ids.joinToString(",") { "?::uuid" }
    return db.withConnection { connection ->
        connection.prepareStatement(
            """
            SELECT s.need_id::text AS need_id, u.id::text AS user_id, u.display_name, u.photo_url
            FROM timebank_need_supports s
            JOIN users u ON u.id = s.user_id
            WHERE s.need_id IN ($placeholders)
            ORDER BY s.created_at
            """.trimIndent(),
        ).use { stmt ->
            ids.forEachIndexed { index, id -> stmt.setString(index + 1, id) }
            stmt.executeQuery().use { rs ->
                buildMap<String, MutableList<TimebankNeedSupport>> {
                    while (rs.next()) {
                        getOrPut(rs.getString("need_id")) { mutableListOf() }.add(
                            TimebankNeedSupport(
                                userId = rs.getString("user_id"),
                                displayName = rs.getString("display_name"),
                                photoUrl = rs.stringOrNull("photo_url"),
                            ),
                        )
                    }
                }
            }
        }
    }
}

private fun helpersForRequested(db: Database, userId: String): List<TimebankMatchHit> = db.withConnection { connection ->
    connection.prepareStatement(
        """
        SELECT DISTINCT u.id::text, u.display_name, t.slug, t.label
        FROM user_skills req
        JOIN skill_tags t ON t.id = req.tag_id
        JOIN user_skills offered ON offered.tag_id = t.id
            AND offered.offered = TRUE
            AND offered.user_id <> req.user_id
        JOIN users u ON u.id = offered.user_id AND u.status = 'ACTIVE'
        WHERE req.user_id = ?::uuid AND req.requested = TRUE
        LIMIT 20
        """.trimIndent(),
    ).use { stmt ->
        stmt.setString(1, userId)
        stmt.executeQuery().use { rs ->
            buildList {
                while (rs.next()) {
                    add(
                        TimebankMatchHit(
                            userId = rs.getString(1),
                            displayName = rs.getString(2),
                            tag = rs.getString(3),
                            tagLabel = rs.getString(4),
                            chatEnabledHint = "El chat se habilita tras match mutuo, sin costo.",
                        ),
                    )
                }
            }
        }
    }
}

private fun seekersForMyOffers(db: Database, userId: String): List<TimebankMatchHit> = db.withConnection { connection ->
    connection.prepareStatement(
        """
        SELECT DISTINCT u.id::text, u.display_name, t.slug, t.label
        FROM user_skills req
        JOIN skill_tags t ON t.id = req.tag_id
        JOIN user_skills mine ON mine.tag_id = req.tag_id
            AND mine.user_id = ?::uuid AND mine.offered = TRUE
        JOIN users u ON u.id = req.user_id AND u.status = 'ACTIVE'
        WHERE req.requested = TRUE AND req.user_id <> ?::uuid
        LIMIT 20
        """.trimIndent(),
    ).use { stmt ->
        stmt.setString(1, userId)
        stmt.setString(2, userId)
        stmt.executeQuery().use { rs ->
            buildList {
                while (rs.next()) {
                    add(
                        TimebankMatchHit(
                            userId = rs.getString(1),
                            displayName = rs.getString(2),
                            tag = rs.getString(3),
                            tagLabel = rs.getString(4),
                            chatEnabledHint = "El chat se habilita tras match mutuo, sin costo.",
                        ),
                    )
                }
            }
        }
    }
}

private fun currentSkillFlag(db: Database, userId: String, slug: String, offered: Boolean): Boolean =
    db.withConnection { connection ->
        val column = if (offered) "us.offered" else "us.requested"
        connection.prepareStatement(
            """
            SELECT $column
            FROM user_skills us
            JOIN skill_tags st ON st.id = us.tag_id
            WHERE us.user_id = ?::uuid AND st.slug = ?
            """.trimIndent(),
        ).use { stmt ->
            stmt.setString(1, userId)
            stmt.setString(2, slug)
            stmt.executeQuery().use { rs -> if (rs.next()) rs.getBoolean(1) else false }
        }
    }

private fun skillLabel(db: Database, slug: String): String = db.withConnection { connection ->
    connection.prepareStatement("SELECT label FROM skill_tags WHERE slug = ?").use { stmt ->
        stmt.setString(1, slug)
        stmt.executeQuery().use { rs -> if (rs.next()) rs.getString(1) else slug }
    }
}

private fun suggestSkills(db: Database, userId: String, query: String): List<SkillSuggestHit> {
    val catalog = skillCatalogHits(db, userId).filter { it.slug != MENSAJE_TAG }
    val q = query.trim()
    val matched = if (q.isEmpty()) {
        catalog.filter { it.offeredCount > 0 }.sortedByDescending { it.offeredCount }.take(8)
    } else {
        catalog.filter { SkillCanon.matchesQuery(q, it.label) }
            .sortedWith(compareByDescending<SkillSuggestHit> { it.offeredCount }.thenBy { it.label })
            .take(8)
    }
    val canonical = if (q.length >= 2) SkillCanon.canonicalLabel(q) else ""
    val already = matched.any { SkillCanon.sameTrade(it.label, canonical) }
    return if (canonical.isNotBlank() && !already) {
        matched + SkillSuggestHit(label = canonical, canonical = true)
    } else {
        matched.map { hit ->
            if (canonical.isNotBlank() && SkillCanon.sameTrade(hit.label, canonical)) {
                hit.copy(canonical = true, label = SkillCanon.canonicalLabel(hit.label))
            } else hit
        }
    }
}

private fun skillCatalogHits(db: Database, userId: String): List<SkillSuggestHit> = db.withConnection { connection ->
    connection.prepareStatement(
        """
        SELECT t.slug, t.label,
               COUNT(*) FILTER (WHERE us.offered = TRUE AND us.user_id <> ?::uuid)::int AS offered_count,
               COALESCE(BOOL_OR(us.offered = TRUE AND us.user_id = ?::uuid), FALSE) AS mine_offered
        FROM skill_tags t
        LEFT JOIN user_skills us ON us.tag_id = t.id
        GROUP BY t.slug, t.label
        ORDER BY t.label
        """.trimIndent(),
    ).use { stmt ->
        stmt.setString(1, userId)
        stmt.setString(2, userId)
        stmt.executeQuery().use { rs ->
            buildList {
                while (rs.next()) {
                    add(
                        SkillSuggestHit(
                            slug = rs.getString("slug"),
                            label = rs.getString("label"),
                            offeredCount = rs.getInt("offered_count"),
                            mineOffered = rs.getBoolean("mine_offered"),
                        ),
                    )
                }
            }
        }
    }
}

private fun ensureSkillTag(db: Database, rawLabel: String): String {
    val label = SkillCanon.canonicalLabel(rawLabel)
    if (label.length < 2) error("Escribí un oficio o una tarea")
    if (label.length > 40) error("Usá 40 caracteres o menos")
    return db.withConnection { connection ->
        val existing = connection.prepareStatement("SELECT slug, label FROM skill_tags").use { stmt ->
            stmt.executeQuery().use { rs ->
                buildList {
                    while (rs.next()) add(rs.getString(1) to rs.getString(2))
                }
            }
        }
        val reused = existing.firstOrNull { (_, current) ->
            current.equals(label, ignoreCase = true) || SkillCanon.sameTrade(current, label)
        }
        if (reused != null) {
            if (reused.second != label) {
                connection.prepareStatement("UPDATE skill_tags SET label = ? WHERE slug = ?").use { stmt ->
                    stmt.setString(1, label)
                    stmt.setString(2, reused.first)
                    stmt.executeUpdate()
                }
            }
            reused.first
        } else {
            val base = skillSlug(label)
            var candidate = base
            var n = 2
            while (existing.any { it.first == candidate }) {
                candidate = "${base.take(44)}-$n"
                n += 1
            }
            connection.prepareStatement(
                "INSERT INTO skill_tags (id, slug, label) VALUES (?::uuid, ?, ?)",
            ).use { stmt ->
                stmt.setString(1, java.util.UUID.randomUUID().toString())
                stmt.setString(2, candidate)
                stmt.setString(3, label)
                stmt.executeUpdate()
            }
            candidate
        }
    }
}

private fun ensureMensajeTag(db: Database) {
    db.withConnection { connection ->
        connection.prepareStatement(
            """
            INSERT INTO skill_tags (slug, label)
            SELECT 'mensaje', 'Mensaje'
            WHERE NOT EXISTS (SELECT 1 FROM skill_tags WHERE slug = 'mensaje')
            """.trimIndent(),
        ).use { it.executeUpdate() }
    }
}

private fun skillSlug(label: String): String {
    val folded = java.text.Normalizer.normalize(label, java.text.Normalizer.Form.NFD)
        .replace(Regex("\\p{M}+"), "")
        .lowercase()
        .replace(Regex("[^a-z0-9]+"), "-")
        .trim('-')
        .take(48)
    return folded.ifBlank { "oficio" }
}

private fun closeNeedsForTag(connection: java.sql.Connection, userId: String, tagId: String) {
    connection.prepareStatement(
        "UPDATE timebank_needs SET open = FALSE WHERE user_id = ?::uuid AND tag_id = ?::uuid AND open",
    ).use { stmt ->
        stmt.setString(1, userId)
        stmt.setString(2, tagId)
        stmt.executeUpdate()
    }
}

/*
Postman — Trueque

POST {{base}}/api/v1/timebank/suggest
Authorization: Bearer {{jwt}}
{ "query": "albañil" }

POST {{base}}/api/v1/timebank/need
Authorization: Bearer {{jwt}}
{ "label": "albañil", "note": "Un muro chico" }

POST {{base}}/api/v1/timebank/need/invite
Authorization: Bearer {{jwt}}
{ "needId": "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa", "userId": "22222222-2222-2222-2222-222222222222" }

POST {{base}}/api/v1/timebank/need/invite/suggest
Authorization: Bearer {{jwt}}
{ "query": "bruno" }

POST {{base}}/api/v1/timebank/need/support
Authorization: Bearer {{jwt}}
{ "needId": "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa" }

POST {{base}}/api/v1/timebank/need/unsupport
Authorization: Bearer {{jwt}}
{ "needId": "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa" }

POST {{base}}/api/v1/timebank/board
Authorization: Bearer {{jwt}}
{}

POST {{base}}/api/v1/timebank/tag/create
Authorization: Bearer {{jwt}}
{ "label": "Clases de guitarra" }
*/

