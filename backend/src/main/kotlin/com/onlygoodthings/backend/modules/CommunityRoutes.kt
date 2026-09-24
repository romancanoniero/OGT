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
import com.onlygoodthings.backend.infra.stringOrNull
import com.onlygoodthings.backend.realtime.RealtimeHub
import com.onlygoodthings.backend.social.OutcomeSqlRepository
import com.onlygoodthings.shared.domain.ActionOutcomeKind
import com.onlygoodthings.shared.domain.AnimalListingDto
import com.onlygoodthings.shared.domain.CampaignDesk
import com.onlygoodthings.shared.domain.CompanyDesk
import com.onlygoodthings.shared.domain.PromoIssued
import com.onlygoodthings.shared.domain.AnimalResolveResult
import com.onlygoodthings.shared.domain.ApiResponse
import com.onlygoodthings.shared.domain.UserRole
import com.onlygoodthings.shared.domain.VerificationMethod
import com.onlygoodthings.shared.domain.OgtCrmDefaults
import com.onlygoodthings.shared.domain.titleCasePersonName
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
    val outcomes = OutcomeSqlRepository(db)
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

    post("/api/v1/animals/resolve") {
        val principal = requireUser(call) ?: return@post
        val dataMap = JsonBody.receiveMap(call)
        val listingId = dataMap.reqString("listingId")
        val reporter = animals.reporterOf(listingId)
            ?: return@post call.respond(HttpStatusCode.NotFound, ApiResponse.fail<Unit>("Ficha inexistente", "NOT_FOUND"))
        if (!animals.markResolved(listingId)) {
            return@post call.respond(ApiResponse.ok("ok", "La ficha ya estaba resuelta"))
        }
        val outcome = if (principal.userId != reporter) {
            outcomes.award(
                OutcomeSqlRepository.Draft(
                    kind = ActionOutcomeKind.ANIMAL_RESOLVED,
                    method = VerificationMethod.WITNESS,
                    actorUserId = principal.userId,
                    beneficiaryUserId = reporter,
                    postId = animals.postIdOf(listingId),
                    sourceTable = "animal_listings",
                    sourceId = listingId,
                    points = 40,
                ),
            )
        } else {
            null
        }
        call.respond(
            ApiResponse.ok(
                AnimalResolveResult(
                    listingId = listingId,
                    verifiedOutcome = outcome != null,
                    message = if (outcome == null) {
                        "Marcado resuelto. El autor no se auto-verifica: no hay se logró ni puntos."
                    } else {
                        "Se logró: otra persona confirmó el reencuentro."
                    },
                ),
            ),
        )
    }

    post("/api/v1/animals/open") {
        requireUser(call) ?: return@post
        call.respond(ApiResponse.ok(animals.open()))
    }

    post("/api/v1/animals/get") {
        requireUser(call) ?: return@post
        val dataMap = JsonBody.receiveMap(call)
        val listing = animals.get(dataMap.reqString("listingId"))
            ?: return@post call.respond(HttpStatusCode.NotFound, ApiResponse.fail<Unit>("Ficha inexistente", "NOT_FOUND"))
        call.respond(ApiResponse.ok(listing))
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
    val petName = titleCasePersonName(dataMap.optString("petName").orEmpty())
    val age = dataMap.optString("ageLabel").orEmpty()
    val lost = kind == "LOST"
    val title = dataMap.optString("title")?.takeIf { it.isNotBlank() }
        ?: if (lost) "Se busca a $petName" else listOf(petName, age).filter { it.isNotBlank() }.joinToString(" · ")
    val marks = dataMap.optString("marks").orEmpty()
    val lastSeenPlace = dataMap.optString("lastSeenPlace").orEmpty()
    val lat = dataMap.optDouble("latitude") ?: if (lost) null else home?.first ?: -34.6037
    val lng = dataMap.optDouble("longitude") ?: if (lost) null else home?.second ?: -58.3816
    if (lost) {
        require(marks.isNotBlank()) { "Campo requerido: marks" }
        require(lastSeenPlace.isNotBlank()) { "Campo requerido: lastSeenPlace" }
        require(lat != null && lng != null) { "Falta el punto de última vista" }
    }
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
        marks = marks,
        lastSeenPlace = lastSeenPlace,
        place = dataMap.optString("place") ?: lastSeenPlace,
        latitude = lat ?: -34.6037,
        longitude = lng ?: -58.3816,
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

POST {{base}}/api/v1/animals/get
Authorization: Bearer {{jwt}}
{ "listingId": "{{listingId}}" }

POST {{base}}/api/v1/geo/search
Authorization: Bearer {{jwt}}
{ "query": "Plaza Italia Palermo", "lang": "es" }

POST {{base}}/api/v1/geo/reverse
Authorization: Bearer {{jwt}}
{ "latitude": -34.5812, "longitude": -58.4214, "lang": "es" }
*/

fun Route.timebankRoutes(db: Database) {
    val outcomes = OutcomeSqlRepository(db)

    post("/api/v1/volunteer/check-in") {
        val principal = requireUser(call) ?: return@post
        val dataMap = JsonBody.receiveMap(call)
        val callId = dataMap.reqString("callId")
        val lat = dataMap.reqDouble("latitude")
        val lng = dataMap.reqDouble("longitude")
        val inside = db.withConnection { connection ->
            connection.prepareStatement(
                """
                SELECT ST_DWithin(
                    location::geography,
                    ST_SetSRID(ST_MakePoint(?, ?), 4326)::geography,
                    geofence_radius_m
                ) AS inside
                FROM volunteer_calls
                WHERE id = ?::uuid AND now() BETWEEN starts_at AND ends_at
                """.trimIndent(),
            ).use { stmt ->
                stmt.setDouble(1, lng)
                stmt.setDouble(2, lat)
                stmt.setString(3, callId)
                stmt.executeQuery().use { rs -> if (rs.next()) rs.getBoolean("inside") else null }
            }
        }
        when (inside) {
            null -> return@post call.respond(HttpStatusCode.NotFound, ApiResponse.fail<Unit>("Convocatoria inexistente o fuera de horario", "NOT_FOUND"))
            false -> return@post call.respond(HttpStatusCode.Forbidden, ApiResponse.fail<Unit>("Tenés que estar en el lugar. No se autodeclara.", "GEO_REQUIRED"))
            true -> Unit
        }
        db.withConnection { connection ->
            connection.prepareStatement(
                """
                INSERT INTO volunteer_signups (call_id, user_id, attendance, check_in_location)
                VALUES (?::uuid, ?::uuid, 'CHECKED_IN', ST_SetSRID(ST_MakePoint(?, ?), 4326))
                ON CONFLICT (call_id, user_id) DO UPDATE
                    SET attendance = 'CHECKED_IN',
                        check_in_location = ST_SetSRID(ST_MakePoint(?, ?), 4326)
                """.trimIndent(),
            ).use { stmt ->
                stmt.setString(1, callId)
                stmt.setString(2, principal.userId)
                stmt.setDouble(3, lng)
                stmt.setDouble(4, lat)
                stmt.setDouble(5, lng)
                stmt.setDouble(6, lat)
                stmt.executeUpdate()
            }
        }
        val outcome = outcomes.award(
            OutcomeSqlRepository.Draft(
                kind = ActionOutcomeKind.VOLUNTEER_CHECKED_IN,
                method = VerificationMethod.GEO,
                actorUserId = principal.userId,
                sourceTable = "volunteer_calls",
                sourceId = callId,
                points = 30,
            ),
        )
        call.respond(ApiResponse.ok(outcome, "Check-in verificado. Se logró."))
    }
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
                                com.onlygoodthings.shared.domain.TimebankMatchHit(
                                    userId = rs.getString("id"),
                                    displayName = rs.getString("display_name"),
                                    tag = rs.getString("slug"),
                                    chatEnabledHint = "El chat se habilita tras match mutuo, sin costo.",
                                ),
                            )
                        }
                    }
                }
            }
        }
        call.respond(ApiResponse.ok(matches))
    }

    post("/api/v1/timebank/close") {
        val principal = requireUser(call) ?: return@post
        val dataMap = JsonBody.receiveMap(call)
        val matchId = dataMap.reqString("matchId")
        val pair = db.withConnection { connection ->
            connection.prepareStatement(
                """
                UPDATE timebank_matches
                SET status = 'CLOSED', chat_enabled = FALSE
                WHERE id = ?::uuid AND requester_id = ?::uuid AND status <> 'CLOSED'
                RETURNING provider_id::text, requester_id::text
                """.trimIndent(),
            ).use { stmt ->
                stmt.setString(1, matchId)
                stmt.setString(2, principal.userId)
                stmt.executeQuery().use { rs ->
                    if (!rs.next()) null else rs.getString(1) to rs.getString(2)
                }
            }
        } ?: return@post call.respond(
            HttpStatusCode.Forbidden,
            ApiResponse.fail<Unit>("Solo quien pidió la ayuda puede cerrar el trueque.", "FORBIDDEN"),
        )
        val outcome = outcomes.award(
            OutcomeSqlRepository.Draft(
                kind = ActionOutcomeKind.TIMEBANK_CLOSED,
                method = VerificationMethod.ORGANIZER,
                actorUserId = pair.first,
                beneficiaryUserId = pair.second,
                sourceTable = "timebank_matches",
                sourceId = matchId,
                points = 25,
            ),
        )
        call.respond(ApiResponse.ok(outcome, "Trueque cerrado. Los puntos van a quien ayudó."))
    }
}

fun Route.csrRoutes(db: Database) {
    val outcomes = OutcomeSqlRepository(db)

    post("/api/v1/companies/mine") {
        val principal = requireUser(call) ?: return@post
        if (principal.role != UserRole.COMPANY_ADMIN) {
            return@post call.respond(HttpStatusCode.Forbidden, ApiResponse.fail<Unit>("Solo COMPANY_ADMIN", "FORBIDDEN"))
        }
        val card = db.withConnection { connection ->
            connection.prepareStatement(
                """
                SELECT c.id::text, c.legal_name, c.trade_name, c.verification_status::text,
                       c.campaign_balance_cents, c.impact_score, c.logo_url, c.tax_id
                FROM companies c
                JOIN company_admins a ON a.company_id = c.id
                WHERE a.user_id = ?::uuid
                ORDER BY a.is_primary DESC
                LIMIT 1
                """.trimIndent(),
            ).use { stmt ->
                stmt.setString(1, principal.userId)
                stmt.executeQuery().use { rs ->
                    if (!rs.next()) return@use null
                    CompanyDesk(
                        id = rs.getString("id"),
                        legalName = rs.getString("legal_name"),
                        tradeName = rs.stringOrNull("trade_name"),
                        verified = rs.getString("verification_status") == "VERIFIED",
                        campaignBalanceCents = rs.getLong("campaign_balance_cents"),
                        impactScore = rs.getDouble("impact_score"),
                        logoUrl = rs.stringOrNull("logo_url"),
                        taxId = rs.stringOrNull("tax_id"),
                    )
                }
            }
        } ?: return@post call.respond(HttpStatusCode.NotFound, ApiResponse.fail<Unit>("No hay empresa a tu cargo", "NOT_FOUND"))
        call.respond(ApiResponse.ok(card))
    }

    post("/api/v1/companies/impact") {
        requireUser(call) ?: return@post
        val dataMap = JsonBody.receiveMap(call)
        val card = outcomes.companyImpact(dataMap.reqString("companyId"))
            ?: return@post call.respond(HttpStatusCode.NotFound, ApiResponse.fail<Unit>("Empresa inexistente", "NOT_FOUND"))
        call.respond(ApiResponse.ok(card))
    }

    post("/api/v1/csr/finance-action") {
        val principal = requireUser(call) ?: return@post
        if (principal.role != UserRole.COMPANY_ADMIN) {
            return@post call.respond(HttpStatusCode.Forbidden, ApiResponse.fail<Unit>("Solo COMPANY_ADMIN", "FORBIDDEN"))
        }
        val dataMap = JsonBody.receiveMap(call)
        val campaignId = dataMap.reqString("campaignId")
        val amountCents = (dataMap["amountCents"] as? Number)?.toLong() ?: error("Campo requerido: amountCents")
        require(amountCents > 0)
        db.withConnection { connection ->
            connection.prepareStatement(
                """
                INSERT INTO money_ledgers (kind, amount_cents, payer_user_id, company_id, campaign_id, note)
                SELECT 'ACTION_FINANCE', ?, ?::uuid, c.company_id, c.id, 'Financia un hecho. El sello llega cuando se verifica.'
                FROM campaigns c
                JOIN company_admins a ON a.company_id = c.company_id
                WHERE c.id = ?::uuid AND a.user_id = ?::uuid
                """.trimIndent(),
            ).use { stmt ->
                stmt.setLong(1, amountCents)
                stmt.setString(2, principal.userId)
                stmt.setString(3, campaignId)
                stmt.setString(4, principal.userId)
                val rows = stmt.executeUpdate()
                if (rows == 0) error("Campaña inexistente o no es tuya")
            }
        }
        call.respond(ApiResponse.ok("ok", "Presupuesto reservado. Empresa que suma se gana al verificarse el hecho."))
    }
    post("/api/v1/csr/campaigns") {
        val principal = requireUser(call) ?: return@post
        if (principal.role.name != "COMPANY_ADMIN") {
            return@post call.respond(HttpStatusCode.Forbidden, ApiResponse.fail<Unit>("Solo COMPANY_ADMIN", "FORBIDDEN"))
        }
        val rows = db.withConnection { connection ->
            connection.prepareStatement(
                """
                SELECT c.id::text, c.title, c.status::text, c.social_goal, c.social_progress, c.budget_cents, c.spent_cents
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
                                CampaignDesk(
                                    id = rs.getString("id"),
                                    title = rs.getString("title"),
                                    status = rs.getString("status"),
                                    socialGoal = rs.getInt("social_goal"),
                                    socialProgress = rs.getInt("social_progress"),
                                    budgetCents = rs.getLong("budget_cents"),
                                    spentCents = rs.getLong("spent_cents"),
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
        call.respond(ApiResponse.ok(PromoIssued(code), "Cupón emitido"))
    }
}

fun Route.crowdfundingRoutes(db: Database) {
    val outcomes = OutcomeSqlRepository(db)
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

    post("/api/v1/causes/deliver") {
        val principal = requireUser(call) ?: return@post
        if (principal.role != UserRole.COMMUNITY_MODERATOR) {
            return@post call.respond(
                HttpStatusCode.Forbidden,
                ApiResponse.fail<Unit>("Solo un moderador confirma la entrega. El organizador no se auto-verifica.", "FORBIDDEN"),
            )
        }
        val dataMap = JsonBody.receiveMap(call)
        val causeId = dataMap.reqString("causeId")
        val organizer = db.withConnection { connection ->
            connection.prepareStatement(
                """
                UPDATE community_causes SET status = 'CLOSED'
                WHERE id = ?::uuid AND status IN ('LIVE', 'FUNDED')
                RETURNING organizer_user_id::text, sponsor_company_id::text
                """.trimIndent(),
            ).use { stmt ->
                stmt.setString(1, causeId)
                stmt.executeQuery().use { rs ->
                    if (!rs.next()) null else rs.getString(1) to rs.getString(2)
                }
            }
        } ?: return@post call.respond(HttpStatusCode.NotFound, ApiResponse.fail<Unit>("Causa inexistente o ya cerrada", "NOT_FOUND"))
        val outcome = outcomes.award(
            OutcomeSqlRepository.Draft(
                kind = ActionOutcomeKind.CAUSE_DELIVERED,
                method = VerificationMethod.ORGANIZER,
                actorUserId = organizer.first ?: principal.userId,
                companyId = organizer.second,
                sourceTable = "community_causes",
                sourceId = causeId,
                points = 60,
            ),
        )
        call.respond(ApiResponse.ok(outcome, "Causa entregada. Se logró."))
    }
}

internal suspend fun requireUser(call: ApplicationCall): AuthPrincipal? {
    val principal = call.principal<AuthPrincipal>()
    if (principal == null) {
        call.respond(HttpStatusCode.Unauthorized, ApiResponse.fail<Unit>("Token requerido", "UNAUTHENTICATED"))
    }
    return principal
}
