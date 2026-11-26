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

fun Route.nicknameRoutes(db:Database) {
    post("/api/v1/users/nickname") {
        val me=call.principal<AuthPrincipal>() ?: return@post call.respond(HttpStatusCode.Unauthorized)
        val nickname=normalizeNickname(JsonBody.receiveMap(call).reqString("nickname"))
        val profile=try { db.withConnection { c ->
            c.prepareStatement("UPDATE users SET nickname=?,updated_at=now() WHERE id=?::uuid AND status='ACTIVE' RETURNING display_name,photo_url").use { q ->
                q.setString(1,nickname);q.setString(2,me.userId);q.executeQuery().use { rs ->require(rs.next()) { "Cuenta no disponible" };UserLive(me.userId,rs.getString(1),rs.getString(2),nickname) }
            }
        } } catch(e:java.sql.SQLException) { if(e.sqlState=="23505")throw IllegalArgumentException("Ese @nick ya está en uso.");throw e }
        call.respond(ApiResponse.ok(profile))
    }
}
