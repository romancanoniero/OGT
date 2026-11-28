package com.onlygoodthings.backend.social

import com.onlygoodthings.backend.infra.Database
import com.onlygoodthings.backend.community.interactionSql
import com.onlygoodthings.shared.domain.PostDiffusion
import java.sql.Connection
import java.util.UUID

/** Idempotent public diffusion of an original post; all reads retain original visibility and author. */
class PostDiffusions(private val db: Database) {
    fun set(user: String, post: String, enabled: Boolean): Boolean {
        UUID.fromString(user);UUID.fromString(post)
        return db.withConnection { c ->
            c.autoCommit=false
            try {
                // Same pair lock as blocking/following. Moderation holds the original row lock.
                c.prepareStatement("SELECT ogt_lock_user_pair(?::uuid,author_user_id) FROM social_posts WHERE id=?::uuid").use { q ->q.setString(1,user);q.setString(2,post);q.executeQuery().close() }
                c.prepareStatement("SELECT id FROM social_posts WHERE id=?::uuid AND NOT is_story AND moderation_status='VISIBLE' AND ${interactionSql(user,"author_user_id")} FOR SHARE").use { q ->
                    q.setString(1,post);q.executeQuery().use { require(it.next()) { "La publicación no está disponible para difundir." } }
                }
                // Keep the first timestamp: retries and older clients cannot withdraw/re-boost it.
                val changed=enabled && c.prepareStatement("INSERT INTO post_diffusions(post_id,user_id) VALUES(?::uuid,?::uuid) ON CONFLICT DO NOTHING").use { q ->q.setString(1,post);q.setString(2,user);q.executeUpdate()>0 }
                c.commit()
                changed
            } catch(error:Exception) {c.rollback();throw error} finally {c.autoCommit=true}
        }
    }
    fun inventory(viewer:String):Map<String,List<PostDiffusion>> = db.withConnection { c ->
        c.prepareStatement("""SELECT d.post_id FROM post_diffusions d
            JOIN social_posts p ON p.id=d.post_id
            WHERE p.moderation_status='VISIBLE' AND NOT p.is_story
            AND (d.user_id=?::uuid OR EXISTS(SELECT 1 FROM follows f WHERE f.follower_id=?::uuid AND f.followed_id=d.user_id))
            AND ${interactionSql(viewer,"d.user_id")} AND ${interactionSql(viewer,"p.author_user_id")}
            GROUP BY d.post_id ORDER BY max(d.created_at) DESC LIMIT 200""").use { q ->
            q.setString(1,viewer);q.setString(2,viewer)
            val ids=q.executeQuery().use { rows ->buildList {while(rows.next())add(rows.getString(1))} }
            forPosts(c,ids,viewer)
        }
    }
    fun forPosts(c:Connection,ids:List<String>,viewer:String):Map<String,List<PostDiffusion>> {
        if(ids.isEmpty())return emptyMap()
        return c.prepareStatement("""SELECT d.post_id,d.user_id,u.display_name,EXTRACT(EPOCH FROM d.created_at)*1000 AS created_ms
            FROM post_diffusions d JOIN users u ON u.id=d.user_id
            WHERE d.post_id=ANY(?::uuid[]) AND (d.user_id=?::uuid OR EXISTS(SELECT 1 FROM follows f WHERE f.follower_id=?::uuid AND f.followed_id=d.user_id))
            AND ${interactionSql(viewer,"d.user_id")} ORDER BY d.created_at DESC""").use { q ->
            val array=c.createArrayOf("uuid",ids.toTypedArray())
            try {
                q.setArray(1,array);q.setString(2,viewer);q.setString(3,viewer)
                q.executeQuery().use { rows ->buildList {while(rows.next())add(rows.getString("post_id") to PostDiffusion(rows.getString("user_id"),rows.getString("display_name"),rows.getLong("created_ms")))}.groupBy({it.first},{it.second}) }
            } finally {array.free()}
        }
    }
}
