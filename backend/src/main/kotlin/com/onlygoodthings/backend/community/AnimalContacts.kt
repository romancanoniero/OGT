package com.onlygoodthings.backend.community

import com.onlygoodthings.backend.auth.AuthPrincipal
import com.onlygoodthings.backend.http.JsonBody
import com.onlygoodthings.backend.http.reqString
import com.onlygoodthings.backend.infra.Database
import com.onlygoodthings.shared.domain.*
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.auth.principal
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.sql.Connection
import java.util.UUID

class AnimalContacts(private val db: Database) {
    fun open(actor: String, incoming: AnimalContactRequest): TimebankThread {
        val request = incoming.copy(listingId = UUID.fromString(incoming.listingId).toString())
        validateAnimalContact(request)
        ensureMensajeTag(db)
        val matchId = guardedContactWrite { db.withConnection { connection ->
            connection.autoCommit = false
            try {
                val listing = connection.prepareStatement("SELECT reporter_user_id::text,title,kind::text,resolved FROM animal_listings WHERE id=?::uuid FOR SHARE").use { q ->
                    q.setString(1,request.listingId)
                    q.executeQuery().use { r ->
                        require(r.next()) { "Ficha no disponible" }
                        require(!r.getBoolean(4)) { "La ficha ya fue resuelta" }
                        require(if(request.kind=="ADOPTION") r.getString(3)=="ADOPTION" else r.getString(3) in setOf("LOST","FOUND")) { "Este contacto no corresponde a la ficha" }
                        r.getString(1) to r.getString(2)
                    }
                }
                connection.prepareStatement("SELECT 1 FROM social_posts WHERE listing_id=?::uuid AND moderation_status='VISIBLE' LIMIT 1").use { q ->
                    q.setString(1,request.listingId);q.executeQuery().use { r ->require(r.next()) { "Ficha no disponible" } }
                }
                require(actor != listing.first) { "Esta ficha es tuya" }
                connection.prepareStatement("SELECT ogt_lock_user_pair(?::uuid,?::uuid)").use { q ->q.setString(1,actor);q.setString(2,listing.first);q.execute() }
                connection.prepareStatement("SELECT ogt_users_can_interact(?::uuid,?::uuid)").use { q ->
                    q.setString(1,actor);q.setString(2,listing.first)
                    q.executeQuery().use { r ->r.next();require(r.getBoolean(1)) { "Esta interacción no está disponible" } }
                }
                // Serializes retries across devices for the same applicant and listing.
                connection.prepareStatement("SELECT pg_advisory_xact_lock(hashtextextended(?,0))").use { q ->q.setString(1,"animal:${request.listingId}:$actor:${request.kind}");q.execute() }
                val existing = connection.prepareStatement("SELECT match_id::text FROM animal_contacts WHERE listing_id=?::uuid AND applicant_user_id=?::uuid AND kind=?").use { q ->
                    q.setString(1,request.listingId);q.setString(2,actor);q.setString(3,request.kind)
                    q.executeQuery().use { r ->if(r.next())r.getString(1) else null }
                }
                val id = existing ?: UUID.randomUUID().toString().also { id ->
                    connection.prepareStatement("INSERT INTO timebank_matches(id,requester_id,provider_id,tag_id,status,chat_enabled) SELECT ?::uuid,?::uuid,?::uuid,id,'ACTIVE',true FROM skill_tags WHERE slug='mensaje'").use { q ->
                        q.setString(1,id);q.setString(2,actor);q.setString(3,listing.first);check(q.executeUpdate()==1)
                    }
                    val messageId=UUID.randomUUID().toString()
                    val body=if(request.kind=="ADOPTION") "Formulario de adopción · ${listing.second}" else "Avistaje · ${listing.second}\n${request.note}".trim()
                    connection.prepareStatement("INSERT INTO timebank_messages(id,match_id,sender_id,body) VALUES (?::uuid,?::uuid,?::uuid,?)").use { q ->
                        q.setString(1,messageId);q.setString(2,id);q.setString(3,actor);q.setString(4,body);q.executeUpdate()
                    }
                    connection.prepareStatement("INSERT INTO animal_contacts(match_id,listing_id,applicant_user_id,kind,listing_title,request_payload,initial_message_id) VALUES (?::uuid,?::uuid,?::uuid,?,?,?::jsonb,?::uuid)").use { q ->
                        q.setString(1,id);q.setString(2,request.listingId);q.setString(3,actor);q.setString(4,request.kind);q.setString(5,listing.second);q.setString(6,Json.encodeToString(request));q.setString(7,messageId);q.executeUpdate()
                    }
                }
                connection.commit()
                id
            } catch(failure:Exception) { connection.rollback();throw failure }
            finally { connection.autoCommit=true }
        } }
        return timebankInbox(db,actor).first { it.matchId==matchId }
    }

    fun sightings(viewer: String, listingId: String): List<AnimalSightingDto> {
        UUID.fromString(listingId)
        return db.withConnection { connection ->
            connection.prepareStatement("""SELECT a.public_id::text,a.match_id::text,a.listing_id::text,a.applicant_user_id::text,a.request_payload::text,
                u.display_name,u.photo_url,(extract(epoch FROM a.created_at)*1000)::bigint AS created_ms,
                (SELECT id::text FROM social_posts WHERE listing_id=a.listing_id AND moderation_status='VISIBLE' ORDER BY created_at LIMIT 1) AS post_id,
                m.provider_id::text FROM animal_contacts a JOIN users u ON u.id=a.applicant_user_id
                JOIN animal_listings l ON l.id=a.listing_id JOIN timebank_matches m ON m.id=a.match_id
                WHERE a.listing_id=?::uuid AND a.kind='SIGHTING' AND NOT l.resolved
                AND ${interactionSql(viewer,"a.applicant_user_id")} AND ${interactionSql(viewer,"l.reporter_user_id")}
                AND EXISTS(SELECT 1 FROM social_posts p WHERE p.listing_id=l.id AND p.moderation_status='VISIBLE')
                ORDER BY a.created_at DESC LIMIT 200""".trimIndent()).use { q ->
                q.setString(1,listingId)
                q.executeQuery().use { r ->buildList {
                    while(r.next()) {
                        val request=Json.decodeFromString<AnimalContactRequest>(r.getString("request_payload"))
                        val actor=r.getString("applicant_user_id")
                        add(AnimalSightingDto(r.getString("public_id"),listingId,r.getString("post_id"),actor,r.getString("display_name"),r.getString("photo_url"),request.note,request.latitude!!,request.longitude!!,
                            r.getString("match_id").takeIf { viewer==actor || viewer==r.getString("provider_id") },r.getLong("created_ms")))
                    }
                } }
            }
        }
    }
}

fun Route.animalContactRoutes(db: Database) {
    val contacts=AnimalContacts(db)
    post("/api/v1/animals/contact") {
        val me=call.principal<AuthPrincipal>() ?: return@post call.respond(HttpStatusCode.Unauthorized)
        val saved=try {
            val raw=JsonBody.receiveMap(call)
            contacts.open(me.userId,Json.decodeFromString<AnimalContactRequest>(JsonBody.objectMapper.writeValueAsString(raw)))
        } catch(failure:IllegalArgumentException) {
            return@post call.respond(HttpStatusCode.BadRequest,ApiResponse.fail<Unit>(failure.message ?: "Contacto inválido","BAD_REQUEST"))
        }
        call.respond(ApiResponse.ok(saved))
    }
    post("/api/v1/animals/sightings") {
        val me=call.principal<AuthPrincipal>() ?: return@post call.respond(HttpStatusCode.Unauthorized)
        val rows=try { contacts.sightings(me.userId,JsonBody.receiveMap(call).reqString("listingId")) }
        catch(_:IllegalArgumentException) { return@post call.respond(HttpStatusCode.BadRequest,ApiResponse.fail<Unit>("Ficha inválida","BAD_REQUEST")) }
        call.respond(ApiResponse.ok(rows))
    }
}
