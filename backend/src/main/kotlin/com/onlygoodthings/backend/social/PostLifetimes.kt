package com.onlygoodthings.backend.social

import com.onlygoodthings.backend.infra.Database
import com.onlygoodthings.shared.domain.PostLifetime
import java.util.UUID

internal class PostLifetimes(private val db: Database) {
    fun own(user: String, post: String, renew: Boolean): PostLifetime? {
        UUID.fromString(user); UUID.fromString(post)
        return db.withConnection { c ->
            val permitted = """id=?::uuid AND NOT is_story AND moderation_status='VISIBLE' AND
                (author_user_id=?::uuid OR author_company_id IN (SELECT company_id FROM company_admins WHERE user_id=?::uuid))"""
            val sql = if (renew) """UPDATE social_posts SET feed_expires_at=now()+interval '90 days',updated_at=now()
                WHERE $permitted AND feed_expires_at IS NOT NULL RETURNING feed_expires_at"""
                else "SELECT feed_expires_at FROM social_posts WHERE $permitted"
            c.prepareStatement(sql).use { q ->
                q.setString(1,post);q.setString(2,user);q.setString(3,user)
                q.executeQuery().use { r ->
                    if (!r.next()) null else {
                        val expires = r.getTimestamp(1)?.time
                        PostLifetime(post,expires,expires!=null && expires<=System.currentTimeMillis())
                    }
                }
            }
        }
    }
}
