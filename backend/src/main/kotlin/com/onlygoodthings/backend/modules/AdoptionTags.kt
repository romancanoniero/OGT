package com.onlygoodthings.backend.modules

import com.onlygoodthings.backend.http.*
import com.onlygoodthings.backend.infra.Database
import com.onlygoodthings.shared.domain.*
import io.ktor.server.application.*
import io.ktor.server.response.respond
import io.ktor.server.routing.*
import java.sql.ResultSet
import java.util.UUID

class AdoptionTags(private val db: Database) {
    fun list(includeInactive: Boolean = false): List<AdoptionTag> = db.withConnection { c ->
        c.prepareStatement("SELECT * FROM adoption_tags WHERE (? OR active) ORDER BY sort_order,label,id").use { q ->
            q.setBoolean(1, includeInactive)
            q.executeQuery().use { rs -> buildList { while (rs.next()) add(rs.adoptionTag()) } }
        }
    }

    fun save(actor: String, data: Map<String, Any?>): AdoptionTag {
        val id = data.optString("id") ?: "adoption-${UUID.randomUUID()}"
        require(Regex("^[a-z0-9-]{2,60}$").matches(id)) { "Identificador de etiqueta inválido." }
        val label = data.reqString("label").trim()
        val key = Hashtags.key(label)
        val group = data.optString("group") ?: "OTHER"
        require(group in AdoptionRules.groups) { "Grupo de etiqueta inválido." }
        val value = data.optString("legacyValue").orEmpty()
        val meanings = when (group) {
            "SPECIES" -> setOf("DOG", "CAT", "BIRD", "RABBIT", "HAMSTER", "FISH", "TURTLE", "OTHER")
            "SIZE" -> setOf("SMALL", "MEDIUM", "LARGE")
            "SEX" -> setOf("HEMBRA", "MACHO")
            "VACCINATION", "STERILIZATION" -> setOf("ALL", "SOME", "NONE", "UNKNOWN")
            else -> emptySet()
        }
        require(value.isEmpty() || value in meanings) { "Significado de etiqueta inválido." }
        require(group !in AdoptionRules.exclusiveGroups || value in meanings) { "Elegí si aplica a todos, algunos o si no está informado." }
        val version = data.optInt("version", 0)
        return try {
            db.withConnection { c ->
                c.autoCommit = false
                try {
                    c.prepareStatement("SELECT id FROM adoption_tags WHERE id<>? AND (tag_key=? OR translate(lower(regexp_replace(label,'[^[:alnum:]_]','','g')),'áéíóúüñ','aeiouun')=?) FOR SHARE").use { q ->
                        q.setString(1,id);q.setString(2,key);q.setString(3,key)
                        q.executeQuery().use { r -> require(!r.next()) { "Ya existe una etiqueta con ese hashtag o nombre." } }
                    }
                    val sql = if (version == 0)
                        "INSERT INTO adoption_tags(id,label,tag_key,group_name,legacy_value,active,sort_order) VALUES(?,?,?,?,?,?,?) RETURNING *"
                    else "UPDATE adoption_tags SET label=?,active=?,sort_order=?,version=version+1 WHERE id=? AND version=? RETURNING *"
                    val saved = c.prepareStatement(sql).use { q ->
                        if (version == 0) {
                            q.setString(1, id); q.setString(2, label); q.setString(3, key); q.setString(4, group)
                            q.setString(5, value); q.setBoolean(6, data.optBoolean("active", true)); q.setInt(7, data.optInt("sortOrder", 0))
                        } else {
                            q.setString(1, label); q.setBoolean(2, data.optBoolean("active", true)); q.setInt(3, data.optInt("sortOrder", 0))
                            q.setString(4, id); q.setInt(5, version)
                        }
                        q.executeQuery().use { rs -> require(rs.next()) { "La etiqueta cambió. Actualizá el catálogo antes de guardar." }; rs.adoptionTag() }
                    }
                    c.prepareStatement("INSERT INTO crm_audit_events(actor_user_id,action,target_type,target_id,reason,outcome) VALUES(?::uuid,'ADOPTION_TAG_SAVE','adoption_tag',?,?,'SUCCESS')").use { q ->
                        q.setString(1, actor); q.setString(2, id); q.setString(3, "$label; activa=${saved.active}"); q.executeUpdate()
                    }
                    c.commit(); saved
                } catch (e: Exception) { c.rollback(); throw e } finally { c.autoCommit = true }
            }
        } catch (e: java.sql.SQLException) {
            if (e.sqlState == "23505") throw IllegalArgumentException("Ya existe una etiqueta con ese hashtag o significado.")
            throw e
        }
    }
}

private fun ResultSet.adoptionTag() = AdoptionTag(
    getString("id"), getString("label"), getString("tag_key"), getString("group_name"), getString("legacy_value"),
    getBoolean("active"), getInt("sort_order"), getInt("version"),
)

fun Route.adoptionCatalogRoutes(db: Database) {
    post("/api/v1/animals/adoption/tags") {
        requireUser(call) ?: return@post
        // Inactive identities let older listings retain their original traits without enabling new uses.
        call.respond(ApiResponse.ok(AdoptionTags(db).list(true)))
    }
}

fun Route.adoptionCrmRoutes(db: Database, authorize: suspend (ApplicationCall) -> String?) {
    post("/api/v1/crm/platform/adoption/tags") {
        authorize(call) ?: return@post
        call.respond(ApiResponse.ok(AdoptionTags(db).list(true)))
    }
    post("/api/v1/crm/platform/adoption/tags/save") {
        val actor = authorize(call) ?: return@post
        call.respond(ApiResponse.ok(AdoptionTags(db).save(actor, JsonBody.receiveMap(call))))
    }
}
