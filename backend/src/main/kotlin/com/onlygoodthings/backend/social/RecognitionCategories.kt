package com.onlygoodthings.backend.social

import com.onlygoodthings.backend.auth.AuthPrincipal
import com.onlygoodthings.backend.http.*
import com.onlygoodthings.backend.infra.Database
import com.onlygoodthings.shared.domain.*
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.*
import io.ktor.server.auth.principal
import io.ktor.server.response.respond
import io.ktor.server.routing.*
import java.sql.Connection
import java.sql.ResultSet
import java.util.UUID

class RecognitionCategories(private val db: Database) {
    fun list(includeInactive: Boolean = false): List<RecognitionCategory> = db.withConnection { c ->
        c.prepareStatement("SELECT * FROM recognition_categories WHERE (? OR active) ORDER BY sort_order,label,id").use { q ->
            q.setBoolean(1, includeInactive)
            q.executeQuery().use { rs -> buildList { while(rs.next()) add(rs.category()) } }
        }
    }

    fun save(actor: String, data: Map<String, Any?>): RecognitionCategory {
        val id = data.optString("id") ?: "category-${UUID.randomUUID()}"
        require(Regex("^[a-z0-9-]{2,60}$").matches(id)) { "Identificador de categoría inválido" }
        val label = data.reqString("label").trim()
        val icon = data.optString("icon").orEmpty().trim()
        require(label.length in 2..60 && icon.length <= 16) { "Revisá el nombre y el icono de la categoría." }
        val version = data.optInt("version",0)
        val order = data.optInt("sortOrder",0)
        val active = data.optBoolean("active", true)
        try {
            return db.withConnection { c ->
                c.autoCommit = false
                try {
                    val sql = if (version == 0) "INSERT INTO recognition_categories(id,label,icon,active,sort_order) VALUES(?,?,?,?,?) RETURNING *"
                    else "UPDATE recognition_categories SET label=?,icon=?,active=?,sort_order=?,version=version+1,updated_at=now() WHERE id=? AND version=? RETURNING *"
                    val saved = c.prepareStatement(sql).use { q ->
                        if (version == 0) { q.setString(1,id);q.setString(2,label);q.setString(3,icon);q.setBoolean(4,active);q.setInt(5,order) }
                        else { q.setString(1,label);q.setString(2,icon);q.setBoolean(3,active);q.setInt(4,order);q.setString(5,id);q.setInt(6,version) }
                        q.executeQuery().use { rs -> require(rs.next()) { "La categoría cambió. Actualizá el listado antes de guardar." };rs.category() }
                    }
                    c.prepareStatement("INSERT INTO crm_audit_events(actor_user_id,action,target_type,target_id,reason,outcome) VALUES(?::uuid,'RECOGNITION_CATEGORY_SAVE','recognition_category',?,?,'SUCCESS')").use { q ->
                        q.setString(1,actor);q.setString(2,id);q.setString(3,"$label; activa=$active");q.executeUpdate()
                    }
                    c.commit();saved
                } catch(e:Exception) { c.rollback();throw e } finally { c.autoCommit=true }
            }
        } catch(e:java.sql.SQLException) {
            if(e.sqlState=="23505")throw IllegalArgumentException("Ya existe una categoría con ese nombre.")
            throw e
        }
    }

    companion object {
        fun resolve(c: Connection, categoryId: String?, legacyTopic: String): RecognitionCategory? =
            c.prepareStatement("SELECT * FROM recognition_categories WHERE id=? OR (? IS NULL AND lower(label)=lower(?)) LIMIT 1 FOR SHARE").use { q ->
                q.setString(1,categoryId);q.setString(2,categoryId);q.setString(3,if(legacyTopic.trim().equals("Ayuda vecinal",true)) "Ayuda comunitaria" else legacyTopic.trim())
                q.executeQuery().use { rs -> if(rs.next()) rs.category() else null }
            }
    }
}

private fun ResultSet.category() = RecognitionCategory(getString("id"),getString("label"),getString("icon"),getBoolean("active"),getInt("sort_order"),getInt("version"))

fun Route.recognitionCategoryRoutes(db:Database) {
    post("/api/v1/social/categories") { call.respond(ApiResponse.ok(RecognitionCategories(db).list())) }
}

fun Route.recognitionCrmRoutes(db:Database,authorize:suspend (ApplicationCall)->String?) {
    post("/api/v1/crm/platform/recognition/invitations") {
        authorize(call) ?: return@post
        val counts=db.withConnection { c ->c.createStatement().use { q ->q.executeQuery("SELECT count(*) AS created,count(share_opened_at) AS share_opened,count(opened_at) AS opened,count(registered_at) AS registered,count(*) FILTER(WHERE status='ACCEPTED') AS accepted,count(*) FILTER(WHERE status='DECLINED') AS declined,count(*) FILTER(WHERE status='REVOKED') AS revoked FROM post_invitations").use { rs ->rs.next();mapOf("created" to rs.getLong("created"),"shareOpened" to rs.getLong("share_opened"),"opened" to rs.getLong("opened"),"registered" to rs.getLong("registered"),"accepted" to rs.getLong("accepted"),"declined" to rs.getLong("declined"),"revoked" to rs.getLong("revoked")) } } }
        call.respond(ApiResponse.ok(counts))
    }
    post("/api/v1/crm/platform/recognition/categories") {
        authorize(call) ?: return@post
        call.respond(ApiResponse.ok(RecognitionCategories(db).list(true)))
    }
    post("/api/v1/crm/platform/recognition/categories/save") {
        val actor=authorize(call) ?: return@post
        call.respond(ApiResponse.ok(RecognitionCategories(db).save(actor,JsonBody.receiveMap(call))))
    }
}
