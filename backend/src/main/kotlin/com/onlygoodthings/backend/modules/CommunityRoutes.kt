package com.onlygoodthings.backend.modules

import com.onlygoodthings.backend.auth.AuthPrincipal
import com.onlygoodthings.backend.http.JsonBody
import com.onlygoodthings.backend.http.optBoolean
import com.onlygoodthings.backend.http.optDouble
import com.onlygoodthings.backend.http.optInt
import com.onlygoodthings.backend.http.optString
import com.onlygoodthings.backend.http.reqDouble
import com.onlygoodthings.backend.http.reqString
import com.onlygoodthings.backend.infra.Database
import com.onlygoodthings.backend.realtime.RealtimeHub
import com.onlygoodthings.shared.domain.AnimalListingDto
import com.onlygoodthings.shared.domain.ApiResponse
import com.onlygoodthings.shared.domain.OgtCrmDefaults
import com.onlygoodthings.shared.domain.MediaKind
import com.onlygoodthings.shared.domain.PostMediaItem
import com.onlygoodthings.shared.domain.SocialLiveCounters
import com.onlygoodthings.shared.protocol.WsChannel
import com.onlygoodthings.shared.protocol.WsCodec
import com.onlygoodthings.shared.protocol.WsEnvelope
import com.onlygoodthings.shared.protocol.WsFrameType
import com.onlygoodthings.shared.protocol.frames.ImpactAlertFrame
import com.onlygoodthings.shared.protocol.frames.ImpactKind
import com.onlygoodthings.shared.realtime.currentEpochMs
import com.onlygoodthings.shared.realtime.nextRequestId
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.call
import io.ktor.server.auth.principal
import io.ktor.server.request.receiveText
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import java.util.UUID

fun Route.referralRoutes(db: Database) {
    post("/api/v1/referrals/link") {
        val principal = requireUser(call) ?: return@post
        val invite = db.withConnection { connection ->
            connection.prepareStatement("SELECT invite_code FROM users WHERE id = ?::uuid").use { stmt ->
                stmt.setString(1, principal.userId)
                stmt.executeQuery().use { rs -> if (rs.next()) rs.getString(1) else null }
            }
        } ?: return@post call.respond(HttpStatusCode.NotFound, ApiResponse.fail<Unit>("Usuario no hallado", "NOT_FOUND"))
        call.respond(
            ApiResponse.ok(
                mapOf(
                    "inviteCode" to invite,
                    "dynamicLink" to "https://onlygoodthings.app/i/$invite",
                ),
            ),
        )
    }

    post("/api/v1/referrals/claim") {
        val principal = requireUser(call) ?: return@post
        val objectMapper = JsonBody.objectMapper
        val dataMap = objectMapper.readValue(call.receiveText(), object : com.fasterxml.jackson.core.type.TypeReference<Map<String, Any?>>() {})
        val code = dataMap.reqString("inviteCode")
        db.withConnection { connection ->
            connection.prepareStatement(
                """
                UPDATE users SET referred_by_user_id = (
                    SELECT id FROM users WHERE invite_code = ?
                )
                WHERE id = ?::uuid AND referred_by_user_id IS NULL
                """.trimIndent(),
            ).use { stmt ->
                stmt.setString(1, code)
                stmt.setString(2, principal.userId)
                stmt.executeUpdate()
            }
        }
        call.respond(ApiResponse.ok("ok", "Invitación atribuida. El emisor cobra al completar la primera acción."))
    }
}

fun Route.animalRoutes(db: Database, hub: RealtimeHub) {
    val animals = AnimalSqlRepository(db)
    post("/api/v1/animals/publish") {
        val principal = requireUser(call) ?: return@post
        val objectMapper = JsonBody.objectMapper
        val dataMap = objectMapper.readValue(call.receiveText(), object : com.fasterxml.jackson.core.type.TypeReference<Map<String, Any?>>() {})
        val write = animalWriteFrom(dataMap, animals.homeLocation(principal.userId))
        val saved = animals.publish(principal.userId, write)
        projectAnimal(hub, saved)
        if (saved.kind == "LOST") broadcastLostAlert(hub, saved)
        call.respond(ApiResponse.ok(saved, "Ficha grabada"))
    }

    post("/api/v1/animals/update") {
        val principal = requireUser(call) ?: return@post
        val objectMapper = JsonBody.objectMapper
        val dataMap = objectMapper.readValue(call.receiveText(), object : com.fasterxml.jackson.core.type.TypeReference<Map<String, Any?>>() {})
        val listingId = dataMap.reqString("listingId")
        val write = animalWriteFrom(dataMap, animals.homeLocation(principal.userId))
        val saved = try {
            animals.update(principal.userId, listingId, write)
        } catch (error: IllegalStateException) {
            return@post call.respond(HttpStatusCode.Forbidden, ApiResponse.fail<Unit>(error.message ?: "No se pudo editar", "FORBIDDEN"))
        }
        projectAnimal(hub, saved)
        call.respond(ApiResponse.ok(saved, "Ficha actualizada"))
    }

    post("/api/v1/animals/open") {
        requireUser(call) ?: return@post
        call.respond(ApiResponse.ok(animals.open()))
    }

    post("/api/v1/animals/nearby") {
        requireUser(call) ?: return@post
        val objectMapper = JsonBody.objectMapper
        val dataMap = objectMapper.readValue(call.receiveText(), object : com.fasterxml.jackson.core.type.TypeReference<Map<String, Any?>>() {})
        val lat = dataMap.reqDouble("latitude")
        val lng = dataMap.reqDouble("longitude")
        val radius = dataMap.optInt("radius", 3000)
        val rows = db.withConnection { connection ->
            connection.prepareStatement(
                """
                SELECT id, kind, species, size, urgency, title, description,
                       ST_Y(location) AS lat, ST_X(location) AS lng, alert_radius_m,
                       ST_Distance(location::geography, ST_SetSRID(ST_MakePoint(?, ?), 4326)::geography) AS meters
                FROM animal_listings
                WHERE resolved = FALSE
                  AND ST_DWithin(location::geography, ST_SetSRID(ST_MakePoint(?, ?), 4326)::geography, ?)
                ORDER BY
                    CASE urgency WHEN 'CRITICAL' THEN 0 WHEN 'HIGH' THEN 1 WHEN 'MEDIUM' THEN 2 ELSE 3 END,
                    meters
                LIMIT 50
                """.trimIndent(),
            ).use { stmt ->
                stmt.setDouble(1, lng)
                stmt.setDouble(2, lat)
                stmt.setDouble(3, lng)
                stmt.setDouble(4, lat)
                stmt.setInt(5, radius)
                stmt.executeQuery().use { rs ->
                    buildList {
                        while (rs.next()) {
                            add(
                                mapOf(
                                    "id" to rs.getString("id"),
                                    "kind" to rs.getString("kind"),
                                    "species" to rs.getString("species"),
                                    "size" to rs.getString("size"),
                                    "urgency" to rs.getString("urgency"),
                                    "title" to rs.getString("title"),
                                    "description" to rs.getString("description"),
                                    "latitude" to rs.getDouble("lat"),
                                    "longitude" to rs.getDouble("lng"),
                                    "radiusMeters" to rs.getInt("alert_radius_m"),
                                    "distanceMeters" to rs.getDouble("meters"),
                                ),
                            )
                        }
                    }
                }
            }
        }
        call.respond(ApiResponse.ok(rows))
    }
}

private fun animalWriteFrom(dataMap: Map<String, Any?>, home: Pair<Double, Double>?): AnimalWrite {
    val kind = (dataMap.optString("kind") ?: "LOST").uppercase()
    val petName = dataMap.optString("petName").orEmpty()
    val age = dataMap.optString("ageLabel").orEmpty()
    val title = dataMap.optString("title")?.takeIf { it.isNotBlank() }
        ?: if (kind == "LOST") "Se busca a $petName" else listOf(petName, age).filter { it.isNotBlank() }.joinToString(" · ")
    val lat = dataMap.optDouble("latitude") ?: home?.first ?: -34.6037
    val lng = dataMap.optDouble("longitude") ?: home?.second ?: -58.3816
    val lost = kind == "LOST"
    return AnimalWrite(
        kind = if (lost) "LOST" else "ADOPTION",
        species = dataMap.reqString("species"),
        size = dataMap.optString("size") ?: "MEDIUM",
        urgency = dataMap.optString("urgency") ?: if (lost) "HIGH" else "LOW",
        title = title,
        description = dataMap.reqString("description"),
        petName = petName,
        ageLabel = age,
        sex = dataMap.optString("sex").orEmpty(),
        temperament = dataMap.optString("temperament").orEmpty(),
        vaccinated = dataMap.optBoolean("vaccinated"),
        sterilized = dataMap.optBoolean("sterilized"),
        homeNeeds = dataMap.optString("homeNeeds").orEmpty(),
        marks = dataMap.optString("marks").orEmpty(),
        lastSeenPlace = dataMap.optString("lastSeenPlace").orEmpty(),
        place = dataMap.optString("place") ?: dataMap.optString("lastSeenPlace").orEmpty(),
        latitude = lat,
        longitude = lng,
        alertRadiusM = if (lost) OgtCrmDefaults.LOST_ALERT_RADIUS_M else 0,
        media = dataMap.optMediaItems(),
    )
}

@Suppress("UNCHECKED_CAST")
private fun Map<String, Any?>.optMediaItems(): List<PostMediaItem> {
    val raw = this["media"] as? List<*> ?: return emptyList()
    return raw.mapIndexedNotNull { index, item ->
        val row = item as? Map<*, *> ?: return@mapIndexedNotNull null
        val url = row["url"] as? String ?: return@mapIndexedNotNull null
        PostMediaItem(
            id = (row["id"] as? String) ?: UUID.randomUUID().toString(),
            kind = runCatching { MediaKind.valueOf((row["kind"] as? String ?: "IMAGE").uppercase()) }
                .getOrDefault(MediaKind.IMAGE),
            url = url,
            posterUrl = row["posterUrl"] as? String,
            sortOrder = (row["sortOrder"] as? Number)?.toInt() ?: index,
            altText = row["altText"] as? String,
        )
    }
}

private fun projectAnimal(hub: RealtimeHub, saved: AnimalListingDto) {
    if (saved.postId.isBlank()) return
    hub.projectSocialPost(
        saved.postId,
        WsCodec.json.encodeToString(
            SocialLiveCounters.serializer(),
            SocialLiveCounters(
                id = saved.postId,
                commentCount = 0,
                impactCount = 0,
                authorUserId = saved.reporterUserId,
                body = saved.description,
                tag = if (saved.kind == "LOST") "Mascota perdida" else "Adopción",
                place = saved.place,
                createdAtEpochMs = saved.createdAtEpochMs,
                listingId = saved.listingId,
            ),
        ),
    )
}

private suspend fun broadcastLostAlert(hub: RealtimeHub, saved: AnimalListingDto) {
    val lat = saved.latitude ?: return
    val lng = saved.longitude ?: return
    val alert = ImpactAlertFrame(
        alertId = saved.listingId,
        kind = ImpactKind.LOST_PET,
        title = saved.title,
        body = saved.description,
        latitude = lat,
        longitude = lng,
        radiusMeters = saved.alertRadiusM.coerceAtLeast(200),
        urgency = saved.urgency,
        deepLink = "ogt://animals/${saved.listingId}",
    )
    hub.broadcast(
        WsChannel.ANIMALS,
        WsEnvelope(
            type = WsFrameType.IMPACT_ALERT,
            requestId = nextRequestId(),
            channel = WsChannel.ANIMALS,
            sentAtEpochMs = currentEpochMs(),
            payload = WsCodec.toPayload(alert),
        ),
    )
    hub.projectAlert(alert.alertId, WsCodec.json.encodeToString(ImpactAlertFrame.serializer(), alert))
}

/*
Postman — grabar adopción (token de lab)

POST {{base}}/api/v1/animals/publish
Authorization: Bearer  {{jwt}}
{
  "kind": "ADOPTION",
  "species": "DOG",
  "size": "SMALL",
  "petName": "Luna",
  "ageLabel": "6 meses",
  "sex": "HEMBRA",
  "temperament": "Cariñosa y sociable",
  "description": "Cachorra mestiza rescatada.",
  "place": "Parque Centenario",
  "vaccinated": true,
  "sterilized": false,
  "homeNeeds": "Alguien en casa varias horas",
  "latitude": -34.6064,
  "longitude": -58.4356,
  "media": [
    { "kind": "IMAGE", "url": "https://upload.wikimedia.org/wikipedia/commons/thumb/6/6e/Golde33443.jpg/640px-Golde33443.jpg", "altText": "Adopción" }
  ]
}

POST {{base}}/api/v1/animals/update
Authorization: Bearer {{jwt}}
{
  "listingId": "{{listingId}}",
  "kind": "ADOPTION",
  "species": "DOG",
  "size": "MEDIUM",
  "petName": "Luna",
  "ageLabel": "1 año",
  "temperament": "Cariñosa, ya no miedosa",
  "description": "Ya está más grande y sigue cariñosa.",
  "place": "Villa Crespo"
}

POST {{base}}/api/v1/animals/open
Authorization: Bearer {{jwt}}
{}
*/

fun Route.timebankRoutes(db: Database) {
    post("/api/v1/timebank/match") {
        val principal = requireUser(call) ?: return@post
        val objectMapper = JsonBody.objectMapper
        val dataMap = objectMapper.readValue(call.receiveText(), object : com.fasterxml.jackson.core.type.TypeReference<Map<String, Any?>>() {})
        val tag = dataMap.reqString("tagSlug")
        val matches = db.withConnection { connection ->
            connection.prepareStatement(
                """
                SELECT u.id, u.display_name, t.slug
                FROM user_skills offered
                JOIN skill_tags t ON t.id = offered.tag_id
                JOIN users u ON u.id = offered.user_id
                WHERE t.slug = ? AND offered.offered = TRUE AND offered.user_id <> ?::uuid
                  AND EXISTS (
                      SELECT 1 FROM user_skills req
                      WHERE req.user_id = ?::uuid AND req.tag_id = t.id AND req.requested = TRUE
                  )
                LIMIT 20
                """.trimIndent(),
            ).use { stmt ->
                stmt.setString(1, tag)
                stmt.setString(2, principal.userId)
                stmt.setString(3, principal.userId)
                stmt.executeQuery().use { rs ->
                    buildList {
                        while (rs.next()) {
                            add(
                                mapOf(
                                    "userId" to rs.getString("id"),
                                    "displayName" to rs.getString("display_name"),
                                    "tag" to rs.getString("slug"),
                                    "chatEnabledHint" to "El chat se habilita tras match mutuo, sin costo.",
                                ),
                            )
                        }
                    }
                }
            }
        }
        call.respond(ApiResponse.ok(matches))
    }
}

fun Route.csrRoutes(db: Database) {
    post("/api/v1/csr/campaigns") {
        val principal = requireUser(call) ?: return@post
        if (principal.role.name != "COMPANY_ADMIN") {
            return@post call.respond(HttpStatusCode.Forbidden, ApiResponse.fail<Unit>("Solo COMPANY_ADMIN", "FORBIDDEN"))
        }
        val rows = db.withConnection { connection ->
            connection.prepareStatement(
                """
                SELECT c.id, c.title, c.status, c.social_goal, c.social_progress, c.budget_cents, c.spent_cents
                FROM campaigns c
                JOIN company_admins a ON a.company_id = c.company_id
                WHERE a.user_id = ?::uuid
                ORDER BY c.created_at DESC
                """.trimIndent(),
            ).use { stmt ->
                stmt.setString(1, principal.userId)
                stmt.executeQuery().use { rs ->
                    buildList {
                        while (rs.next()) {
                            add(
                                mapOf(
                                    "id" to rs.getString("id"),
                                    "title" to rs.getString("title"),
                                    "status" to rs.getString("status"),
                                    "socialGoal" to rs.getInt("social_goal"),
                                    "socialProgress" to rs.getInt("social_progress"),
                                    "budgetCents" to rs.getLong("budget_cents"),
                                    "spentCents" to rs.getLong("spent_cents"),
                                ),
                            )
                        }
                    }
                }
            }
        }
        call.respond(ApiResponse.ok(rows))
    }

    post("/api/v1/csr/promo/issue") {
        val principal = requireUser(call) ?: return@post
        if (principal.role.name != "COMPANY_ADMIN") {
            return@post call.respond(HttpStatusCode.Forbidden, ApiResponse.fail<Unit>("Solo COMPANY_ADMIN", "FORBIDDEN"))
        }
        val objectMapper = JsonBody.objectMapper
        val dataMap = objectMapper.readValue(call.receiveText(), object : com.fasterxml.jackson.core.type.TypeReference<Map<String, Any?>>() {})
        val code = "OGT-" + UUID.randomUUID().toString().take(8).uppercase()
        db.withConnection { connection ->
            connection.prepareStatement(
                """
                INSERT INTO promo_codes (campaign_id, company_id, code, discount_bps, issued_to_user, expires_at, condition_note)
                SELECT ?::uuid, company_id, ?, ?, ?::uuid, now() + interval '30 days', ?
                FROM campaigns WHERE id = ?::uuid
                """.trimIndent(),
            ).use { stmt ->
                stmt.setString(1, dataMap.reqString("campaignId"))
                stmt.setString(2, code)
                stmt.setInt(3, dataMap.optInt("discountBps", 1000))
                val issuedTo = dataMap.optString("issuedToUser")
                if (issuedTo == null) stmt.setNull(4, java.sql.Types.OTHER) else stmt.setString(4, issuedTo)
                stmt.setString(5, dataMap.optString("conditionNote") ?: "Meta comunitaria alcanzada")
                stmt.setString(6, dataMap.reqString("campaignId"))
                stmt.executeUpdate()
            }
        }
        call.respond(ApiResponse.ok(mapOf("code" to code), "Cupón emitido"))
    }
}

fun Route.crowdfundingRoutes(db: Database) {
    post("/api/v1/causes/progress") {
        requireUser(call) ?: return@post
        val objectMapper = JsonBody.objectMapper
        val dataMap = objectMapper.readValue(call.receiveText(), object : com.fasterxml.jackson.core.type.TypeReference<Map<String, Any?>>() {})
        val causeId = dataMap.reqString("causeId")
        val row = db.withConnection { connection ->
            connection.prepareStatement(
                """
                SELECT title, goal_cents, raised_cents, status, verified
                FROM community_causes WHERE id = ?::uuid
                """.trimIndent(),
            ).use { stmt ->
                stmt.setString(1, causeId)
                stmt.executeQuery().use { rs ->
                    if (!rs.next()) null
                    else {
                        val goal = rs.getLong("goal_cents")
                        val raised = rs.getLong("raised_cents")
                        mapOf(
                            "title" to rs.getString("title"),
                            "goalCents" to goal,
                            "raisedCents" to raised,
                            "percent" to if (goal == 0L) 0.0 else (raised.toDouble() / goal * 100.0),
                            "status" to rs.getString("status"),
                            "verified" to rs.getBoolean("verified"),
                        )
                    }
                }
            }
        } ?: return@post call.respond(HttpStatusCode.NotFound, ApiResponse.fail<Unit>("Causa inexistente", "NOT_FOUND"))
        call.respond(ApiResponse.ok(row))
    }
}

private suspend fun requireUser(call: ApplicationCall): AuthPrincipal? {
    val principal = call.principal<AuthPrincipal>()
    if (principal == null) {
        call.respond(HttpStatusCode.Unauthorized, ApiResponse.fail<Unit>("Token requerido", "UNAUTHENTICATED"))
    }
    return principal
}
