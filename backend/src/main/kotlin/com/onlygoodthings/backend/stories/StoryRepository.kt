package com.onlygoodthings.backend.stories

import com.onlygoodthings.backend.infra.Database
import com.onlygoodthings.shared.domain.*
import java.nio.file.Files
import java.sql.Connection
import java.util.UUID

class StorySqlRepository(private val db: Database, val media: StoryMediaStore) {
    fun publishingAllowed(owner:String):Boolean = db.withConnection { c ->
        c.prepareStatement("SELECT status='ACTIVE' AND NOT COALESCE((to_jsonb(users)->>'publishing_blocked')::boolean,false) FROM users WHERE id=?::uuid").use { s ->
            s.setString(1,owner);s.executeQuery().use { it.next() && it.getBoolean(1) }
        }
    }
    private val readPermission = """(p.author_user_id=?::uuid OR (
        u.status='ACTIVE' AND (COALESCE((u.metadata->'settings'->>'publicProfileVisible')::boolean,true)
          OR EXISTS(SELECT 1 FROM follows f WHERE f.follower_id=?::uuid AND f.followed_id=p.author_user_id))
        AND (d.audience='COMMUNITY' OR EXISTS(SELECT 1 FROM follows f WHERE f.follower_id=?::uuid AND f.followed_id=p.author_user_id))))"""
    private val fields = """p.id::text,p.author_user_id::text,u.display_name,u.photo_url,p.body,
        d.frame_mode,d.frame_zoom,d.frame_x,d.frame_y,d.audience,d.kind,d.content_type,d.duration_ms,d.has_poster,p.moderation_status::text,
        (extract(epoch from p.created_at)*1000)::bigint AS created_ms,
        (extract(epoch from p.story_expires_at)*1000)::bigint AS expires_ms"""

    fun list(viewer: String, cursor: String? = null, admin: Boolean = false, query: String = ""): StoryPage = db.withConnection { c ->
        val now = System.currentTimeMillis()
        val offset = cursor?.toIntOrNull()?.coerceIn(0,10000) ?: 0
        val order = if(admin) "(SELECT count(*) FROM content_reports r WHERE r.post_id=p.id AND r.status='PENDING_REVIEW') DESC,p.created_at DESC,p.id DESC" else "p.created_at DESC,p.id DESC"
        val where = if(admin) "TRUE" else "p.moderation_status='VISIBLE' AND p.story_expires_at>now() AND $readPermission"
        val found = c.prepareStatement("""SELECT $fields,
            EXISTS(SELECT 1 FROM story_views v WHERE v.post_id=p.id AND v.viewer_id=?::uuid) AS seen,
            (SELECT count(*) FROM story_views v WHERE v.post_id=p.id) AS views
            FROM social_posts p JOIN story_details d ON d.post_id=p.id JOIN users u ON u.id=p.author_user_id
            WHERE p.is_story AND $where AND (p.body ILIKE ? OR u.display_name ILIKE ? OR p.id::text ILIKE ?)
            ORDER BY $order LIMIT 101 OFFSET ?""").use { s ->
            var index = 1
            s.setString(index++,viewer)
            if(!admin) repeat(3) { s.setString(index++,viewer) }
            s.setString(index++,"%${query.take(120)}%")
            s.setString(index++,"%${query.take(120)}%")
            s.setString(index++,"%${query.take(120)}%")
            s.setInt(index,offset)
            s.executeQuery().use { rs -> buildList {
                while(rs.next()) {
                    val id=rs.getString("id"); val owner=rs.getString("author_user_id")
                    val expires=rs.getLong("expires_ms")
                    val until=if(admin) now+120000 else minOf(now+120000,expires)
                    add(Story(id,owner,rs.getString("display_name"),rs.getString("photo_url"),rs.getString("body"),
                        StoryAudience.valueOf(rs.getString("audience")),MediaKind.valueOf(rs.getString("kind")),
                        media.url(id,viewer,until,admin=admin),
                        if(rs.getBoolean("has_poster")) media.url(id,viewer,until,true,admin) else null,
                        rs.getLong("duration_ms"),rs.getLong("created_ms"),expires,rs.getBoolean("seen"),
                        if(admin || owner==viewer) rs.getInt("views") else 0,
                        if(rs.getString("moderation_status")!="VISIBLE") rs.getString("moderation_status") else if(expires<=now) "EXPIRED" else "VISIBLE",
                        rs.getString("frame_mode")?.let { StoryFrame(StoryFrameMode.valueOf(it),rs.getDouble("frame_zoom"),rs.getDouble("frame_x"),rs.getDouble("frame_y")) }))
                }
            } }
        }
        StoryPage(found.take(100),now,if(found.size>100) (offset+100).toString() else null)
    }

    fun publish(owner: String, id: String, caption: String, audience: StoryAudience, bytes: ByteArray, video: Boolean, posterBytes: ByteArray?, frame: StoryFrame? = null): Story {
        UUID.fromString(id)
        require(caption.length<=StoryRules.MAX_CAPTION) { "El texto admite hasta 1200 caracteres" }
        val valid = media.validate(bytes,video)
        val poster = posterBytes?.let { media.validate(it,false) }
        require(!video || poster!=null) { "El video necesita una imagen de vista previa" }
        val digest=media.hash(bytes + caption.toByteArray() + audience.name.toByteArray() + (posterBytes ?: byteArrayOf()) + (frame?.let { "frame:${it.mode}:${it.zoom}:${it.x}:${it.y}".toByteArray() } ?: byteArrayOf()))
        db.withConnection { c ->
            c.autoCommit=false
            var wrote=false
            try {
                c.prepareStatement("SELECT pg_advisory_xact_lock(hashtextextended(?,0))").use { s -> s.setString(1,id);s.execute() }
                val existing=c.prepareStatement("SELECT p.author_user_id::text,d.content_hash FROM social_posts p JOIN story_details d ON d.post_id=p.id WHERE p.id=?::uuid").use { s ->
                    s.setString(1,id);s.executeQuery().use { if(it.next()) it.getString(1) to it.getString(2) else null }
                }
                if(existing!=null) {
                    require(existing.first==owner && existing.second==digest) { "Este intento ya fue usado con otro contenido" }
                } else {
                    require(c.prepareStatement("SELECT status='ACTIVE' AND NOT COALESCE((to_jsonb(users)->>'publishing_blocked')::boolean,false) FROM users WHERE id=?::uuid").use { s->s.setString(1,owner);s.executeQuery().use { it.next() && it.getBoolean(1) } }) { "Esta cuenta no puede publicar" }
                    media.write(id,valid.bytes); wrote=true
                    if(poster!=null) media.write(id,poster.bytes,true)
                    c.prepareStatement("""INSERT INTO social_posts(id,author_kind,author_user_id,body,topic,is_story,story_expires_at)
                        VALUES(?::uuid,'USER',?::uuid,?,'Historia',true,now()+interval '24 hours')""").use { s->s.setString(1,id);s.setString(2,owner);s.setString(3,caption);s.executeUpdate() }
                    c.prepareStatement("""INSERT INTO story_details(post_id,audience,kind,content_type,duration_ms,content_hash,has_poster,frame_mode,frame_zoom,frame_x,frame_y)
                        VALUES(?::uuid,?,?,?,?,?,?,?,?,?,?)""").use { s->
                        s.setString(1,id);s.setString(2,audience.name);s.setString(3,valid.kind.name);s.setString(4,valid.type)
                        s.setLong(5,valid.durationMs);s.setString(6,digest);s.setBoolean(7,poster!=null)
                        s.setString(8,frame?.mode?.name)
                        listOf(frame?.zoom,frame?.x,frame?.y).forEachIndexed { index,value -> if(value==null) s.setNull(9+index,java.sql.Types.DOUBLE) else s.setDouble(9+index,value) }
                        s.executeUpdate()
                    }
                }
                c.commit()
            } catch(e:Exception) { c.rollback();if(wrote) media.delete(id);throw e }
            finally { c.autoCommit=true }
        }
        // Idempotent retries after expiry/removal cannot republish an old story.
        return list(owner,query=id).stories.firstOrNull { it.id==id } ?: throw IllegalArgumentException("La historia ya venció o fue retirada")
    }

    fun canRead(id: String, viewer: String, admin: Boolean=false): Boolean = db.withConnection { c ->
        val permission=if(admin) """EXISTS(SELECT 1 FROM crm_platform_staff s JOIN users staff ON staff.id=s.user_id
            WHERE s.user_id=?::uuid AND s.enabled AND s.approved_by IS NOT NULL AND s.approved_at IS NOT NULL
            AND staff.status='ACTIVE' AND s.staff_role IN ('OWNER','OPERATIONS','TRUST_SAFETY','SUPPORT'))"""
            else "p.moderation_status='VISIBLE' AND p.story_expires_at>now() AND $readPermission"
        c.prepareStatement("""SELECT 1 FROM social_posts p JOIN story_details d ON d.post_id=p.id
            JOIN users u ON u.id=p.author_user_id WHERE p.id=?::uuid AND p.is_story AND $permission""").use { s->
            s.setString(1,id);repeat(if(admin) 1 else 3) { s.setString(it+2,viewer) };s.executeQuery().use { it.next() }
        }
    }
    fun mediaType(id: String): String = db.withConnection { c -> c.prepareStatement("SELECT content_type FROM story_details WHERE post_id=?::uuid").use { s->s.setString(1,id);s.executeQuery().use { if(it.next()) it.getString(1) else "application/octet-stream" } } }
    fun markSeen(id: String, viewer: String) {
        UUID.fromString(id)
        require(canRead(id,viewer)) { "La historia no está disponible" }
        db.withConnection { c -> c.prepareStatement("INSERT INTO story_views(post_id,viewer_id) SELECT ?::uuid,?::uuid WHERE NOT EXISTS (SELECT 1 FROM social_posts WHERE id=?::uuid AND author_user_id=?::uuid) ON CONFLICT DO NOTHING").use { s->s.setString(1,id);s.setString(2,viewer);s.setString(3,id);s.setString(4,viewer);s.executeUpdate() } }
    }
    /** Reports are private, retry-safe, and never make content visible to a new audience. */
    fun report(id:String,viewer:String,reason:String,details:String?) {
        UUID.fromString(id)
        validateStoryReport(reason,details)
        db.withConnection { c ->
            c.autoCommit=false
            try {
                c.prepareStatement("SELECT pg_advisory_xact_lock(hashtext(?))").use { q -> q.setString(1,"story-report:$id:$viewer");q.execute() }
                val allowed=c.prepareStatement("""SELECT p.author_user_id::text FROM social_posts p
                    JOIN story_details d ON d.post_id=p.id JOIN users u ON u.id=p.author_user_id
                    WHERE p.id=?::uuid AND p.is_story AND p.moderation_status='VISIBLE'
                    AND p.story_expires_at>now() AND $readPermission FOR SHARE OF p""").use { q ->
                    q.setString(1,id);repeat(3) { q.setString(it+2,viewer) }
                    q.executeQuery().use { r -> r.next() && r.getString(1)!=viewer }
                }
                require(allowed) { "La historia no está disponible para denunciar" }
                c.prepareStatement("""INSERT INTO content_reports(reporter_id,post_id,reason,details)
                    SELECT ?::uuid,?::uuid,?,? WHERE NOT EXISTS
                    (SELECT 1 FROM content_reports WHERE reporter_id=?::uuid AND post_id=?::uuid)""").use { q ->
                    q.setString(1,viewer);q.setString(2,id);q.setString(3,reason);q.setString(4,details)
                    q.setString(5,viewer);q.setString(6,id);q.executeUpdate()
                }
                c.commit()
            } catch(error:Exception) { c.rollback();throw error } finally { c.autoCommit=true }
        }
    }
    fun reportCounts(ids:List<String>):Map<String,Int> = db.withConnection { c ->
        c.prepareStatement("SELECT post_id::text,count(*) FROM content_reports WHERE post_id=ANY(?::uuid[]) AND status='PENDING_REVIEW' GROUP BY post_id").use { q ->
            q.setArray(1,c.createArrayOf("uuid",ids.toTypedArray()));q.executeQuery().use { r -> buildMap { while(r.next()) put(r.getString(1),r.getInt(2)) } }
        }
    }
    fun reports(id:String):List<StoryModerationReport> = db.withConnection { c ->
        UUID.fromString(id)
        c.prepareStatement("""SELECT r.id::text,r.reason,r.details,r.status::text,
            (extract(epoch from r.created_at)*1000)::bigint FROM content_reports r
            JOIN social_posts p ON p.id=r.post_id WHERE p.id=?::uuid AND p.is_story
            ORDER BY r.created_at DESC LIMIT 100""").use { q ->
            q.setString(1,id);q.executeQuery().use { r -> buildList {
                while(r.next()) {
                    val code=r.getString(2)
                    add(StoryModerationReport(r.getString(1),code,r.getString(3),r.getString(4),r.getLong(5),PostReportCatalog.byCode(code)?.label ?: "Otro motivo"))
                }
            } }
        }
    }
    fun dismissReports(id:String,actor:String,reason:String) {
        UUID.fromString(id);require(reason.trim().length in 3..500)
        db.withConnection { c ->
            c.autoCommit=false
            try {
                val changed=c.prepareStatement("""UPDATE content_reports r SET status='HIDDEN'
                    WHERE r.post_id=?::uuid AND r.status='PENDING_REVIEW'
                    AND EXISTS(SELECT 1 FROM social_posts p WHERE p.id=r.post_id AND p.is_story)""").use { q -> q.setString(1,id);q.executeUpdate() }
                if(changed>0)c.prepareStatement("""INSERT INTO crm_audit_events(actor_user_id,action,target_type,target_id,reason,outcome)
                    VALUES(?::uuid,'STORY_REPORT_DISMISS','story',?,?,'SUCCESS')""").use { q ->q.setString(1,actor);q.setString(2,id);q.setString(3,reason.trim());q.executeUpdate() }
                c.commit()
            } catch(error:Exception) { c.rollback();throw error } finally { c.autoCommit=true }
        }
    }
    fun remove(id: String, actor: String, admin: Boolean=false, reason: String="Retirada por el autor") {
        UUID.fromString(id)
        db.withConnection { c ->
            c.autoCommit=false
            try {
                val changed=c.prepareStatement("""UPDATE social_posts SET moderation_status='REMOVED',updated_at=now()
                    WHERE id=?::uuid AND is_story AND (? OR author_user_id=?::uuid) RETURNING id""").use { s->
                    s.setString(1,id);s.setBoolean(2,admin);s.setString(3,actor);s.executeQuery().use { it.next() }
                }
                require(changed) { "No podés retirar esta historia" }
                c.prepareStatement("UPDATE story_details SET removal_reason=?,removed_by=?::uuid WHERE post_id=?::uuid").use { s->s.setString(1,reason);s.setString(2,actor);s.setString(3,id);s.executeUpdate() }
                if(admin) c.prepareStatement("""INSERT INTO crm_audit_events(actor_user_id,action,target_type,target_id,reason,outcome)
                    VALUES(?::uuid,'STORY_REMOVE','story',?,?,'SUCCESS')""").use { s->s.setString(1,actor);s.setString(2,id);s.setString(3,reason);s.executeUpdate() }
                c.prepareStatement("UPDATE content_reports SET status='REMOVED' WHERE post_id=?::uuid AND status='PENDING_REVIEW'").use { q -> q.setString(1,id);q.executeUpdate() }
                c.commit()
            } catch(e:Exception) { c.rollback();throw e } finally { c.autoCommit=true }
        }
    }
    fun purgeErasedMedia() {
        val ids=db.withConnection { c -> c.createStatement().use { q -> q.executeQuery("SELECT story_id::text FROM erased_story_media WHERE processed_at IS NULL ORDER BY created_at LIMIT 100").use { r ->buildList { while(r.next())add(r.getString(1)) } } } }
        ids.forEach { id ->
            media.delete(id)
            db.withConnection { c -> c.prepareStatement("UPDATE erased_story_media SET processed_at=now() WHERE story_id=?::uuid").use { q ->q.setString(1,id);q.executeUpdate() } }
        }
    }
    fun purgeExpiredMedia() {
        val ids=db.withConnection { c->c.prepareStatement("SELECT id::text FROM social_posts WHERE is_story AND story_expires_at<now()-interval '1 hour'").use { s->s.executeQuery().use { buildList { while(it.next()) add(it.getString(1)) } } } }
        ids.forEach { media.delete(it) }
    }
}

@kotlinx.serialization.Serializable
data class StoryModerationReport(val id:String,val reason:String,val details:String?,val status:String,val createdAtEpochMs:Long,val reasonLabel:String)

internal fun validateStoryReport(reason:String,details:String?) {
    require(PostReportCatalog.byCode(reason)!=null) { "Motivo de denuncia inválido" }
    require(details==null || details.length<=500) { "El detalle supera 500 caracteres" }
}
