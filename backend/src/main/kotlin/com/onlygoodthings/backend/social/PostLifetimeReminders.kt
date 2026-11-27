package com.onlygoodthings.backend.social

import com.onlygoodthings.backend.infra.Database
import com.onlygoodthings.backend.notify.NoticeSqlRepository
import com.onlygoodthings.shared.domain.postDeepLink

/** Durable inbox entry and at most one push attempt per owner and expiry period. */
class PostLifetimeReminders(private val db: Database,
    private val deliver: (String,String) -> Int = { owner,post -> NoticeSqlRepository(db).send(owner,
        "Tu publicación vence mañana", "Podés renovarla por 90 días. Si no, se archivará del feed y conservará su contenido y mensajes.",
        "POST_EXPIRY",post,postDeepLink(post)) }) {
    fun process(): Int {
        val claimed = db.withConnection { c ->
            c.createStatement().use { q -> q.executeQuery("""
                INSERT INTO post_lifetime_notices(post_id,recipient_user_id,expires_at)
                SELECT p.id,owners.user_id,p.feed_expires_at
                FROM social_posts p
                CROSS JOIN LATERAL (
                  SELECT p.author_user_id AS user_id WHERE p.author_user_id IS NOT NULL
                  UNION SELECT user_id FROM company_admins WHERE company_id=p.author_company_id
                ) owners
                JOIN users u ON u.id=owners.user_id AND u.status='ACTIVE' AND NOT u.publishing_blocked
                WHERE NOT p.is_story AND p.moderation_status='VISIBLE'
                  AND p.feed_expires_at>now() AND p.feed_expires_at<=now()+interval '24 hours'
                  AND NOT EXISTS(SELECT 1 FROM post_lifetime_notices n WHERE n.post_id=p.id AND n.recipient_user_id=owners.user_id AND n.expires_at=p.feed_expires_at)
                ORDER BY p.feed_expires_at LIMIT 100
                ON CONFLICT(post_id,recipient_user_id,expires_at) DO NOTHING
                RETURNING id::text,recipient_user_id::text,post_id::text
            """.trimIndent()).use { r -> buildList { while(r.next()) add(Triple(r.getString(1),r.getString(2),r.getString(3))) } } }
        }
        for ((id,owner,post) in claimed) {
            val sent=runCatching { deliver(owner,post)>0 }.getOrDefault(false)
            db.withConnection { c -> c.prepareStatement("UPDATE post_lifetime_notices SET push_sent=? WHERE id=?::uuid").use { q ->q.setBoolean(1,sent);q.setString(2,id);q.executeUpdate() } }
        }
        return claimed.size
    }
}
