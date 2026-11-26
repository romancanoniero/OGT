package com.onlygoodthings.backend.social

import com.onlygoodthings.backend.infra.Database
import com.onlygoodthings.shared.domain.*
import java.security.MessageDigest
import java.security.SecureRandom
import java.sql.Connection
import java.sql.ResultSet
import java.util.Base64
import java.util.UUID

internal fun invitationTokenHash(token:String):String = MessageDigest.getInstance("SHA-256").digest(token.toByteArray()).joinToString("") { "%02x".format(it) }

class PostInvitations(private val db:Database) {
    fun create(actor:String,postId:String,drafts:List<PostInvitationDraft>):List<PostInvitationView> = db.withConnection { c ->
        c.autoCommit=false
        try { createInTransaction(c,actor,postId,drafts);c.commit() }
        catch(e:Exception) { c.rollback();throw e } finally { c.autoCommit=true }
        list(c,actor,postId)
    }

    fun mine(actor:String,postId:String):List<PostInvitationView> = db.withConnection { list(it,actor,postId) }

    fun inbox(actor:String):List<PostInvitationView> = db.withConnection { c ->
        c.prepareStatement("SELECT i.*,u.display_name AS issuer_name,'READY' AS delivery_status FROM post_invitations i JOIN users u ON u.id=i.issuer_user_id JOIN social_posts p ON p.id=i.post_id WHERE i.recipient_user_id=?::uuid AND i.status='PENDING' AND i.expires_at>now() AND p.moderation_status='VISIBLE' AND NOT p.is_story AND u.status='ACTIVE' AND ogt_users_can_interact(i.issuer_user_id,i.recipient_user_id) ORDER BY i.created_at DESC LIMIT 100").use { q ->
            q.setString(1,actor);q.executeQuery().use { rs ->buildList { while(rs.next())add(rs.view(false).copy(token=rs.getString("delivery_token"))) } }
        }
    }

    fun peek(token:String):PostInvitationView? = db.withConnection { c ->
        c.prepareStatement("UPDATE post_invitations i SET opened_at=COALESCE(opened_at,now()) FROM social_posts p WHERE i.token_hash=? AND p.id=i.post_id AND p.moderation_status='VISIBLE' AND NOT p.is_story AND i.status='PENDING' AND i.expires_at>now()").use { q ->q.setString(1,invitationTokenHash(token));q.executeUpdate() }
        read(c,token,null)
    }

    fun accept(token:String,userId:String,decline:Boolean=false):PostInvitationView = db.withConnection { c ->
        c.autoCommit=false
        try {
            val row=c.prepareStatement("SELECT i.*,p.author_user_id::text AS author_id FROM post_invitations i JOIN social_posts p ON p.id=i.post_id WHERE token_hash=? AND p.moderation_status='VISIBLE' AND NOT p.is_story FOR UPDATE OF i").use { q ->
                q.setString(1,invitationTokenHash(token));q.executeQuery().use { rs ->
                    require(rs.next()) { "Esta invitación no está disponible." }
                    require(rs.getString("issuer_user_id")!=userId) { "La invitación es para otra persona." }
                    require(rs.getString("status") in setOf("PENDING","ACCEPTED") && rs.getTimestamp("expires_at").time>System.currentTimeMillis()) { "Esta invitación venció o fue retirada." }
                    require(!decline || rs.getString("status")=="PENDING") { "La invitación ya fue aceptada." }
                    val claimed=rs.getString("claimed_user_id")
                    require(claimed==null || claimed==userId) { "La invitación ya fue aceptada por otra persona." }
                    listOf(rs.getString("id"),rs.getString("post_id"),rs.getString("purpose"),rs.getString("channel"),rs.getString("contact"),rs.getString("recipient_user_id"),rs.getString("issuer_user_id"))
                }
            }
            val account=c.prepareStatement("SELECT email,phone_e164,display_name,created_at FROM users WHERE id=?::uuid AND status='ACTIVE'").use { q ->
                q.setString(1,userId);q.executeQuery().use { rs ->
                    require(rs.next()) { "Cuenta no disponible" }
                    val recipient=row[5]
                    val matches=if(recipient!=null) recipient==userId else when(row[3]) {
                        "EMAIL" -> rs.getString("email")?.trim()?.equals(row[4],ignoreCase=true)==true
                        "SMS" -> !rs.getString("phone_e164").isNullOrBlank() && rs.getString("phone_e164").filter(Char::isDigit)==row[4]?.filter(Char::isDigit)
                        "SHARE" -> true
                        else -> false
                    }
                    require(matches) { "Ingresá con la cuenta asociada al correo o teléfono que recibió la invitación." }
                    rs.getString("display_name") to rs.getTimestamp("created_at")
                }
            }
            requireAllowed(c,row[6]!!,userId)
            c.prepareStatement("UPDATE post_invitations SET status=?,claimed_user_id=?::uuid,recipient_user_id=?::uuid,accepted_at=CASE WHEN ? THEN accepted_at ELSE COALESCE(accepted_at,now()) END,registered_at=CASE WHEN ? >= created_at THEN ? ELSE registered_at END WHERE id=?::uuid").use { q ->
                q.setString(1,if(decline)"DECLINED" else "ACCEPTED");q.setString(2,userId);q.setString(3,userId);q.setBoolean(4,decline);q.setTimestamp(5,account.second);q.setTimestamp(6,account.second);q.setString(7,row[0]);q.executeUpdate()
            }
            if(!decline && row[2]=="RECOGNITION") {
                c.prepareStatement("SELECT id FROM social_posts WHERE id=?::uuid FOR UPDATE").use { q ->q.setString(1,row[1]);q.executeQuery().close() }
                c.prepareStatement("SELECT 1 FROM post_people WHERE post_id=?::uuid AND role='PROTAGONIST' AND user_id<>?::uuid").use { q ->q.setString(1,row[1]);q.setString(2,userId);q.executeQuery().use { require(!it.next()) { "Esta acción ya tiene otra persona reconocida." } } }
                c.prepareStatement("INSERT INTO post_people(post_id,user_id,role) VALUES(?::uuid,?::uuid,'PROTAGONIST') ON CONFLICT DO NOTHING").use { q ->q.setString(1,row[1]);q.setString(2,userId);q.executeUpdate() }
                // Claiming links the account; the full name written on the post remains its own text.
                c.prepareStatement("UPDATE social_posts SET updated_at=now() WHERE id=?::uuid").use { q ->q.setString(1,row[1]);q.executeUpdate() }
            }
            val result=read(c,token,userId) ?: error("No se pudo abrir la invitación")
            c.commit();result
        } catch(e:Exception) { c.rollback();throw e } finally { c.autoCommit=true }
    }

    fun retry(actor:String,id:String) = db.withConnection { c ->
        c.prepareStatement("UPDATE invitation_deliveries d SET status='PENDING',next_attempt_at=now(),last_error_code=NULL FROM post_invitations i,social_posts p WHERE d.invitation_id=i.id AND i.id=?::uuid AND i.issuer_user_id=?::uuid AND p.id=i.post_id AND p.moderation_status='VISIBLE' AND NOT p.is_story AND i.status='PENDING' AND i.expires_at>now() AND d.status IN ('FAILED','CONFIGURATION_REQUIRED')").use { q ->
            q.setString(1,id);q.setString(2,actor);require(q.executeUpdate()==1) { "Esta invitación no admite reintento; actualizá su estado." }
        }
    }

    fun shareOpened(actor:String,id:String) = db.withConnection { c ->
        c.prepareStatement("UPDATE post_invitations i SET share_opened_at=COALESCE(share_opened_at,now()) FROM social_posts p WHERE i.id=?::uuid AND i.issuer_user_id=?::uuid AND i.status='PENDING' AND i.expires_at>now() AND p.id=i.post_id AND p.moderation_status='VISIBLE' AND NOT p.is_story ").use { q ->q.setString(1,id);q.setString(2,actor);require(q.executeUpdate()==1) { "Esta invitación ya no está disponible." } }
    }

    fun revoke(actor:String,id:String) = db.withConnection { c ->
        c.prepareStatement("UPDATE post_invitations SET status='REVOKED' WHERE id=?::uuid AND issuer_user_id=?::uuid AND status='PENDING'").use { q ->q.setString(1,id);q.setString(2,actor);require(q.executeUpdate()==1) { "No podés retirar esta invitación." } }
    }

    private fun read(c:Connection,token:String,viewer:String?):PostInvitationView? = c.prepareStatement("SELECT i.*,u.display_name AS issuer_name,CASE WHEN i.share_opened_at IS NOT NULL THEN 'SHARE_OPENED' ELSE COALESCE(d.status,'READY') END AS delivery_status FROM post_invitations i JOIN users u ON u.id=i.issuer_user_id JOIN social_posts p ON p.id=i.post_id LEFT JOIN invitation_deliveries d ON d.invitation_id=i.id WHERE i.token_hash=? AND p.moderation_status='VISIBLE' AND NOT p.is_story AND u.status='ACTIVE' AND i.expires_at>now() AND (i.status IN ('PENDING','ACCEPTED') OR (i.status='DECLINED' AND i.claimed_user_id=?::uuid))").use { q ->
        q.setString(1,invitationTokenHash(token));q.setString(2,viewer);q.executeQuery().use { rs -> if(rs.next()) rs.view(false) else null }
    }

    private fun list(c:Connection,actor:String,postId:String):List<PostInvitationView> {
        c.prepareStatement("SELECT 1 FROM social_posts WHERE id=?::uuid AND author_user_id=?::uuid AND moderation_status='VISIBLE' AND NOT is_story").use { q ->q.setString(1,postId);q.setString(2,actor);q.executeQuery().use { require(it.next()) { "Solo quien publicó puede administrar invitaciones." } } }
        return c.prepareStatement("SELECT i.*,u.display_name AS issuer_name,CASE WHEN i.share_opened_at IS NOT NULL THEN 'SHARE_OPENED' ELSE COALESCE(d.status,'READY') END AS delivery_status FROM post_invitations i JOIN users u ON u.id=i.issuer_user_id LEFT JOIN invitation_deliveries d ON d.invitation_id=i.id WHERE i.post_id=?::uuid AND i.issuer_user_id=?::uuid ORDER BY i.created_at DESC,i.id").use { q ->
            q.setString(1,postId);q.setString(2,actor);q.executeQuery().use { rs ->buildList { while(rs.next()) add(rs.view(true)) } }
        }
    }

    companion object {
        fun createInTransaction(c:Connection,actor:String,postId:String,drafts:List<PostInvitationDraft>) {
            require(drafts.size<=20) { "Podés agregar hasta 20 invitaciones por vez." }
            if(drafts.isEmpty())return
            c.prepareStatement("SELECT pg_advisory_xact_lock(hashtextextended(?,0))").use { q ->q.setString(1,"invitations:$actor");q.execute() }
            val hero=c.prepareStatement("SELECT p.honoree_name,(SELECT user_id::text FROM post_people WHERE post_id=p.id AND role='PROTAGONIST' LIMIT 1) AS hero FROM social_posts p JOIN users u ON u.id=p.author_user_id WHERE p.id=?::uuid AND p.author_user_id=?::uuid AND p.moderation_status='VISIBLE' AND NOT p.is_story AND u.status='ACTIVE' AND NOT u.publishing_blocked").use { q ->
                q.setString(1,postId);q.setString(2,actor);q.executeQuery().use { rs ->require(rs.next()) { "Solo quien publicó puede invitar a este post." };rs.getString(1) to rs.getString(2) }
            }
            var daily=c.prepareStatement("SELECT count(*) FROM post_invitations WHERE issuer_user_id=?::uuid AND created_at>now()-interval '1 day'").use { q ->q.setString(1,actor);q.executeQuery().use { rs ->rs.next();rs.getInt(1) } }

            for(draft in drafts) {
                validateInvitationDraft(draft)
                val contact=normalizeInvitationContact(draft.channel,draft.contact)
                val recipient=if(!draft.recipientUserId.isNullOrBlank()) {
                    c.prepareStatement("SELECT id::text FROM users WHERE id=?::uuid AND status='ACTIVE'").use { q ->q.setString(1,draft.recipientUserId);q.executeQuery().use { rs ->require(rs.next()) { "No se puede invitar esa cuenta." };rs.getString(1) } }
                } else c.prepareStatement("SELECT id::text FROM users WHERE status='ACTIVE' AND ((?='EMAIL' AND lower(email)=?) OR (?='SMS' AND regexp_replace(phone_e164,'[^0-9]','','g')=?)) LIMIT 1").use { q ->
                    q.setString(1,draft.channel);q.setString(2,contact);q.setString(3,draft.channel);q.setString(4,contact.filter(Char::isDigit));q.executeQuery().use { rs ->if(rs.next())rs.getString(1) else null }
                }
                require(recipient!=actor) { "Elegí a otra persona para invitar." }
                if(recipient!=null)requireAllowed(c,actor,recipient)
                if(draft.purpose=="RECOGNITION") {
                    require(!hero.first.isNullOrBlank() && (hero.second==null || hero.second==recipient)) { "La invitación debe corresponder a la persona reconocida." }
                    require(draft.recipientName.trim().equals(hero.first!!.trim(),ignoreCase=true)) { "Usá el nombre de la persona reconocida para esta invitación." }
                }
                val target=when { recipient!=null -> "user:$recipient";draft.channel=="SHARE" -> "share:${draft.recipientName.trim().lowercase()}";else -> "${draft.channel}:$contact" }
                if(draft.purpose=="RECOGNITION")c.prepareStatement("SELECT 1 FROM post_invitations WHERE post_id=?::uuid AND purpose='RECOGNITION' AND target_key<>? AND status NOT IN ('REVOKED','DECLINED')").use { q ->q.setString(1,postId);q.setString(2,target);q.executeQuery().use { require(!it.next()) { "Este reconocimiento ya tiene una invitación personal." } } }
                val token=Base64.getUrlEncoder().withoutPadding().encodeToString(ByteArray(32).also { SecureRandom().nextBytes(it) })
                val id=UUID.randomUUID().toString()
                val inserted=c.prepareStatement("INSERT INTO post_invitations(id,post_id,issuer_user_id,recipient_user_id,recipient_name,purpose,channel,contact,token_hash,delivery_token,target_key) VALUES(?::uuid,?::uuid,?::uuid,?::uuid,?,?,?,?,?,?,?) ON CONFLICT(post_id,purpose,target_key) WHERE status IN ('PENDING','ACCEPTED') DO NOTHING RETURNING id").use { q ->
                    q.setString(1,id);q.setString(2,postId);q.setString(3,actor);q.setString(4,recipient);q.setString(5,draft.recipientName.trim());q.setString(6,draft.purpose);q.setString(7,draft.channel);q.setString(8,contact);q.setString(9,invitationTokenHash(token));q.setString(10,token);q.setString(11,target);q.executeQuery().use { it.next() }
                }
                if(inserted) { daily++;require(daily<=50) { "Llegaste al límite diario de invitaciones." } }
                if(inserted && recipient!=null)c.prepareStatement("INSERT INTO invitation_deliveries(invitation_id) VALUES(?::uuid)").use { q ->q.setString(1,id);q.executeUpdate() }
            }
        }

        private fun requireAllowed(c:Connection,actor:String,recipient:String) {
            c.prepareStatement("SELECT ogt_lock_user_pair(?::uuid,?::uuid)").use { q ->q.setString(1,actor);q.setString(2,recipient);q.execute() }
            c.prepareStatement("SELECT 1 FROM user_blocks WHERE (blocker_id=?::uuid AND blocked_id=?::uuid) OR (blocker_id=?::uuid AND blocked_id=?::uuid)").use { q ->
                q.setString(1,actor);q.setString(2,recipient);q.setString(3,recipient);q.setString(4,actor);q.executeQuery().use { require(!it.next()) { "No se puede invitar a esa persona." } }
            }
        }
    }
}

private fun ResultSet.view(owner:Boolean) = PostInvitationView(getString("id"),getString("post_id"),getString("recipient_name"),getString("issuer_name"),getString("purpose"),getString("status"),getTimestamp("expires_at").time,
    if(owner)getString("delivery_status") else "",if(owner)getString("channel") else "",if(owner)maskInvitationContact(getString("channel"),getString("contact")) else "",token=if(owner)getString("delivery_token") else null,contact=if(owner)getString("contact") else null)
internal fun maskInvitationContact(channel:String,contact:String):String = when(channel) {
    "EMAIL" -> contact.take(1)+"***@"+contact.substringAfter('@',"")
    "SMS" -> "***"+contact.takeLast(4)
    else -> "Cuenta de OGT"
}
