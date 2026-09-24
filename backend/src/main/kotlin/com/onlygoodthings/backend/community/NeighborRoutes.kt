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
import com.onlygoodthings.shared.domain.ChatLive
import com.onlygoodthings.shared.domain.ChatThreadLive
import com.onlygoodthings.shared.domain.CommunityNotice
import com.onlygoodthings.shared.domain.NeighborCard
import com.onlygoodthings.shared.domain.SkillTagDto
import com.onlygoodthings.shared.domain.TimebankMatchHit
import com.onlygoodthings.shared.domain.TimebankMessageDto
import com.onlygoodthings.shared.domain.TimebankThread
import com.onlygoodthings.shared.domain.WalletActivity
import com.onlygoodthings.shared.domain.WalletSummary
import com.onlygoodthings.shared.domain.communityLevelLabel
import com.onlygoodthings.shared.protocol.WsCodec
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.auth.principal
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import java.sql.Connection
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
        val target = dataMap.optString("userId")?.trim().orEmpty().ifBlank { principal.userId }
        call.respond(ApiResponse.ok(skillTags(db, target)))
    }

    post("/api/v1/timebank/seekers") {
        val principal = requireNeighbor(call) ?: return@post
        val dataMap = runCatching { JsonBody.receiveMap(call) }.getOrDefault(emptyMap())
        call.respond(ApiResponse.ok(skillSeekers(db, principal.userId, dataMap.optString("tagSlug"))))
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

    post("/api/v1/timebank/start") {
        val principal = requireNeighbor(call) ?: return@post
        val dataMap = JsonBody.receiveMap(call)
        val slug = dataMap.optString("tagSlug")?.trim().orEmpty().ifBlank { MENSAJE_TAG }
        val thread = startMatch(db, principal.userId, dataMap.reqString("userId"), slug)
            ?: return@post call.respond(HttpStatusCode.BadRequest, ApiResponse.fail<Unit>("No se pudo abrir el chat", "BAD_REQUEST"))
        projectTimebank(hub, db, thread.matchId, null)
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
        projectTimebank(hub, db, matchId, saved)
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
        repeat(8) { stmt.setString(it + 1, userId) }
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
        WHERE t.slug <> '$MENSAJE_TAG'
          AND (
            t.slug IN ('companionship', 'tutoring', 'gardening', 'repairs', 'pet_care', 'cooking', 'translation', 'tech_help')
            OR EXISTS (SELECT 1 FROM user_skills x WHERE x.tag_id = t.id)
          )
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

/** Vecinos que piden un oficio que yo ya ofrecí en el perfil. */
private fun skillSeekers(db: Database, userId: String, slug: String?): List<TimebankMatchHit> = db.withConnection { connection ->
    connection.prepareStatement(
        """
        SELECT u.id::text, u.display_name, t.slug, t.label
        FROM user_skills mine
        JOIN skill_tags t ON t.id = mine.tag_id
        JOIN user_skills req ON req.tag_id = t.id AND req.requested = TRUE AND req.user_id <> mine.user_id
        JOIN users u ON u.id = req.user_id AND u.status = 'ACTIVE'
        WHERE mine.user_id = ?::uuid AND mine.offered = TRUE
          AND (? IS NULL OR t.slug = ?)
        ORDER BY t.label, u.display_name
        LIMIT 40
        """.trimIndent(),
    ).use { stmt ->
        stmt.setString(1, userId)
        stmt.setString(2, slug)
        stmt.setString(3, slug)
        stmt.executeQuery().use { rs ->
            buildList {
                while (rs.next()) {
                    add(
                        TimebankMatchHit(
                            userId = rs.getString("id"),
                            displayName = rs.getString("display_name"),
                            tag = rs.getString("slug"),
                            tagLabel = rs.getString("label"),
                            chatEnabledHint = "El chat se habilita tras match mutuo, sin costo.",
                        ),
                    )
                }
            }
        }
    }
}

private fun skillSlug(label: String): String {
    val folded = java.text.Normalizer.normalize(label.trim().lowercase(), java.text.Normalizer.Form.NFD)
        .replace(Regex("\\p{M}+"), "")
        .replace(Regex("[^a-z0-9]+"), "-")
        .trim('-')
        .take(48)
    val slug = folded.ifBlank { "oficio" }
    return if (slug == MENSAJE_TAG) "oficio-$slug" else slug
}

private fun createOfferedSkill(
    db: Database,
    userId: String,
    rawLabel: String,
): List<SkillTagDto> {
    val label = rawLabel.trim().replace(Regex("\\s+"), " ")
    if (label.length < 2) error("Escribí un oficio o una tarea")
    if (label.length > 40) error("Usá 40 caracteres o menos")
    val slug = db.withConnection { connection ->
        val reused = connection.prepareStatement(
            "SELECT slug FROM skill_tags WHERE lower(label) = lower(?) LIMIT 1",
        ).use { stmt ->
            stmt.setString(1, label)
            stmt.executeQuery().use { rs -> if (rs.next()) rs.getString(1) else null }
        }
        reused ?: run {
            val base = skillSlug(label)
            var candidate = base
            var n = 2
            while (true) {
                val taken = connection.prepareStatement("SELECT 1 FROM skill_tags WHERE slug = ?").use { stmt ->
                    stmt.setString(1, candidate)
                    stmt.executeQuery().use { it.next() }
                }
                if (!taken) break
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
    val requested = db.withConnection { connection ->
        connection.prepareStatement(
            """
            SELECT us.requested
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
    upsertSkill(db, userId, slug, offered = true, requested = requested)
    return skillTags(db, userId)
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
    }
}

private fun startMatch(db: Database, me: String, peerId: String, slug: String): TimebankThread? {
    if (me == peerId) return null
    return db.withConnection { connection ->
        val peerExists = connection.prepareStatement("SELECT 1 FROM users WHERE id = ?::uuid AND status = 'ACTIVE'").use { stmt ->
            stmt.setString(1, peerId)
            stmt.executeQuery().use { it.next() }
        }
        if (!peerExists) return@withConnection null
        val existing = connection.prepareStatement(
            """
            SELECT id::text FROM timebank_matches
            WHERE status <> 'CLOSED'
              AND ((requester_id = ?::uuid AND provider_id = ?::uuid)
                OR (requester_id = ?::uuid AND provider_id = ?::uuid))
            ORDER BY created_at DESC
            LIMIT 1
            """.trimIndent(),
        ).use { stmt ->
            stmt.setString(1, me)
            stmt.setString(2, peerId)
            stmt.setString(3, peerId)
            stmt.setString(4, me)
            stmt.executeQuery().use { rs -> if (rs.next()) rs.getString(1) else null }
        }
        val matchId = existing ?: run {
            val tagId = connection.prepareStatement("SELECT id::text FROM skill_tags WHERE slug = ?").use { stmt ->
                stmt.setString(1, slug)
                stmt.executeQuery().use { rs -> if (rs.next()) rs.getString(1) else null }
            } ?: return@withConnection null
            UUID.randomUUID().toString().also { id ->
                connection.prepareStatement(
                    """
                    INSERT INTO timebank_matches (id, requester_id, provider_id, tag_id, status, chat_enabled)
                    VALUES (?::uuid, ?::uuid, ?::uuid, ?::uuid, 'ACTIVE', TRUE)
                    """.trimIndent(),
                ).use { stmt ->
                    stmt.setString(1, id)
                    stmt.setString(2, me)
                    stmt.setString(3, peerId)
                    stmt.setString(4, tagId)
                    stmt.executeUpdate()
                }
            }
        }
        threadOf(connection, me, matchId)
    }
}

private fun timebankInbox(db: Database, userId: String): List<TimebankThread> = db.withConnection { connection ->
    connection.prepareStatement(
        """
        SELECT m.id::text, m.status::text, t.slug, t.label,
               CASE WHEN m.requester_id = ?::uuid THEN m.provider_id ELSE m.requester_id END AS peer_id,
               peer.display_name,
               peer.photo_url,
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
                        readThread(rs),
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

private fun threadOf(connection: Connection, viewerId: String, matchId: String): TimebankThread? {
    return connection.prepareStatement(
        """
        SELECT m.id::text, m.status::text, t.slug, t.label,
               CASE WHEN m.requester_id = ?::uuid THEN m.provider_id ELSE m.requester_id END AS peer_id,
               peer.display_name,
               peer.photo_url,
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
        WHERE m.id = ?::uuid
        """.trimIndent(),
    ).use { stmt ->
        stmt.setString(1, viewerId)
        stmt.setString(2, viewerId)
        stmt.setString(3, matchId)
        stmt.executeQuery().use { rs -> if (rs.next()) readThread(rs) else null }
    }
}

private fun readThread(rs: java.sql.ResultSet): TimebankThread = TimebankThread(
    matchId = rs.getString("id"),
    peerUserId = rs.getString("peer_id"),
    peerName = rs.getString("display_name"),
    tag = rs.getString("slug"),
    tagLabel = rs.getString("label"),
    lastBody = rs.getString("last_body").orEmpty(),
    lastAtEpochMs = rs.getLong("last_ms"),
    status = rs.getString("status"),
    photoUrl = rs.stringOrNull("photo_url"),
)

private fun projectTimebank(hub: RealtimeHub, db: Database, matchId: String, message: TimebankMessageDto?) {
    val thread = chatThreadLive(db, matchId) ?: return
    hub.projectChatThread(matchId, WsCodec.json.encodeToString(ChatThreadLive.serializer(), thread))
    if (message != null) {
        hub.projectChatMessage(
            matchId,
            message.id,
            WsCodec.json.encodeToString(
                ChatLive.serializer(),
                ChatLive(
                    id = message.id,
                    matchId = message.matchId,
                    senderId = message.senderId,
                    senderName = message.senderName,
                    body = message.body,
                    createdAtEpochMs = message.createdAtEpochMs,
                ),
            ),
        )
    }
}

private fun chatThreadLive(db: Database, matchId: String): ChatThreadLive? = db.withConnection { connection ->
    connection.prepareStatement(
        """
        SELECT m.id::text, m.status::text, t.slug, t.label,
               m.requester_id::text, m.provider_id::text,
               req.display_name AS requester_name, req.photo_url AS requester_photo,
               prov.display_name AS provider_name, prov.photo_url AS provider_photo,
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
        JOIN users req ON req.id = m.requester_id
        JOIN users prov ON prov.id = m.provider_id
        WHERE m.id = ?::uuid
        """.trimIndent(),
    ).use { stmt ->
        stmt.setString(1, matchId)
        stmt.executeQuery().use { rs ->
            if (!rs.next()) null
            else ChatThreadLive(
                matchId = rs.getString("id"),
                lastBody = rs.getString("last_body").orEmpty(),
                lastAtEpochMs = rs.getLong("last_ms"),
                tag = rs.getString("slug"),
                tagLabel = rs.getString("label"),
                status = rs.getString("status"),
                requesterId = rs.getString("requester_id"),
                providerId = rs.getString("provider_id"),
                requesterName = rs.getString("requester_name"),
                providerName = rs.getString("provider_name"),
                requesterPhotoUrl = rs.stringOrNull("requester_photo"),
                providerPhotoUrl = rs.stringOrNull("provider_photo"),
            )
        }
    }
}
