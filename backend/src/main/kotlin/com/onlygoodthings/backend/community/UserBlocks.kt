package com.onlygoodthings.backend.community

import com.onlygoodthings.backend.infra.Database
import com.onlygoodthings.backend.auth.AuthPrincipal
import com.onlygoodthings.backend.http.*
import com.onlygoodthings.backend.realtime.RealtimeHub
import com.onlygoodthings.shared.domain.ApiResponse
import com.onlygoodthings.shared.domain.BlockedUser
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import java.util.UUID

/** Only a validated UUID is embedded; column expressions are internal constants, never request input. */
internal fun interactionSql(viewer:String,otherColumn:String):String =
    "ogt_users_can_interact('${UUID.fromString(viewer)}'::uuid,$otherColumn)"

class UserBlocks(private val db:Database) {
    fun allowed(a:String,b:String):Boolean = db.withConnection { c ->
        c.prepareStatement("SELECT ogt_users_can_interact(?::uuid,?::uuid)").use { q ->
            q.setString(1,a);q.setString(2,b);q.executeQuery().use { r ->r.next();r.getBoolean(1) }
        }
    }
    fun requireAllowed(a:String,b:String) { require(allowed(a,b)) { "Esta interacción no está disponible" } }
    fun excluded(viewer:String):Set<String> = db.withConnection { c ->
        c.prepareStatement("SELECT CASE WHEN blocker_id=?::uuid THEN blocked_id ELSE blocker_id END::text FROM user_blocks WHERE blocker_id=?::uuid OR blocked_id=?::uuid").use { q ->
            repeat(3){q.setString(it+1,viewer)};q.executeQuery().use { r ->buildSet { while(r.next())add(r.getString(1)) } }
        }
    }
    fun list(viewer:String):List<BlockedUser> = db.withConnection { c ->
        c.prepareStatement("SELECT u.id::text,u.display_name,extract(epoch FROM b.created_at)::bigint*1000 FROM user_blocks b JOIN users u ON u.id=b.blocked_id WHERE b.blocker_id=?::uuid ORDER BY b.created_at DESC").use { q ->
            q.setString(1,viewer);q.executeQuery().use { r ->buildList { while(r.next())add(BlockedUser(r.getString(1),r.getString(2),r.getLong(3))) } }
        }
    }
    fun set(viewer:String,other:String,blocked:Boolean) {
        UUID.fromString(other);require(viewer!=other) { "No podés bloquearte a vos mismo" }
        db.withConnection { c ->
            c.autoCommit=false
            try {
                c.prepareStatement("SELECT ogt_lock_user_pair(?::uuid,?::uuid)").use { q ->q.setString(1,viewer);q.setString(2,other);q.execute() }
                val sql=if(blocked) "INSERT INTO user_blocks(blocker_id,blocked_id) SELECT ?::uuid,id FROM users WHERE id=?::uuid AND status='ACTIVE' ON CONFLICT DO NOTHING" else "DELETE FROM user_blocks WHERE blocker_id=?::uuid AND blocked_id=?::uuid"
                c.prepareStatement(sql).use { q ->q.setString(1,viewer);q.setString(2,other);q.executeUpdate() }
                if(blocked)c.prepareStatement("DELETE FROM follows WHERE (follower_id=?::uuid AND followed_id=?::uuid) OR (follower_id=?::uuid AND followed_id=?::uuid)").use { q ->q.setString(1,viewer);q.setString(2,other);q.setString(3,other);q.setString(4,viewer);q.executeUpdate() }
                c.commit()
            } catch(error:Exception){c.rollback();throw error} finally {c.autoCommit=true}
        }
    }
}
fun Route.userBlockRoutes(blocks:UserBlocks,hub:RealtimeHub) {
    post("/api/v1/users/blocks") {
        val me=call.principal<AuthPrincipal>() ?: return@post call.respond(io.ktor.http.HttpStatusCode.Unauthorized)
        call.respond(ApiResponse.ok(blocks.list(me.userId)))
    }
    for((path,value) in listOf("block" to true,"unblock" to false))post("/api/v1/users/$path") {
        val me=call.principal<AuthPrincipal>() ?: return@post call.respond(io.ktor.http.HttpStatusCode.Unauthorized)
        blocks.set(me.userId,JsonBody.receiveMap(call).reqString("userId"),value)
        hub.projectStoryChange()
        call.respond(ApiResponse.ok("ok"))
    }
}

internal fun UserBlocks.canReceiveTree(db:Database,viewer:String,path:String):Boolean {
    val userRoot=com.onlygoodthings.shared.realtime.OgtDbPaths.USERS+"/"
    if(path.startsWith(userRoot)) {
        val id=path.removePrefix(userRoot).substringBefore('/')
        if(runCatching { UUID.fromString(id) }.isFailure)return false
        return allowed(viewer,id)
    }
    val root=com.onlygoodthings.shared.realtime.OgtDbPaths.SOCIAL_FEED+"/"
    if(!path.startsWith(root))return true
    val parts=path.removePrefix(root).split('/')
    if(runCatching { UUID.fromString(parts[0]) }.isFailure)return false
    if(parts.size>=3 && parts[1]=="comments" && runCatching { UUID.fromString(parts[2]) }.isFailure)return false
    return db.withConnection { c ->
        c.prepareStatement("SELECT ${interactionSql(viewer,"author_user_id")} FROM social_posts WHERE id=?::uuid").use { q ->
            q.setString(1,parts[0]);q.executeQuery().use { r ->!r.next() || r.getBoolean(1) }
        } && (parts.size<3 || parts[1]!="comments" || c.prepareStatement("SELECT ${interactionSql(viewer,"author_user_id")} FROM post_comments WHERE id=?::uuid").use { q ->
            q.setString(1,parts[2]);q.executeQuery().use { r ->!r.next() || r.getBoolean(1) }
        })
    }
}

/** Translate only newly added direct-contact constraint failures into a safe client error. */
internal fun <T> guardedContactWrite(block:()->T):T = try { block() } catch(error:java.sql.SQLException) {
    if(error.sqlState=="23514")throw IllegalArgumentException("Esta interacción no está disponible")
    throw error
}
