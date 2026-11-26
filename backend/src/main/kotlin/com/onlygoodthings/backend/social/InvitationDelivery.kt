package com.onlygoodthings.backend.social

import com.onlygoodthings.backend.infra.Database
import com.onlygoodthings.backend.notify.NoticeSqlRepository
import com.onlygoodthings.shared.domain.invitationLink

/** External messages are composed by the user's apps. This queue only handles existing OGT push. */
class InvitationDeliveryWorker(private val db:Database,private val notices:NoticeSqlRepository=NoticeSqlRepository(db)) {
    fun processPending(limit:Int=10):Int {
        var count=0
        repeat(limit) {
            val job=db.withConnection { c ->
                c.autoCommit=false
                try {
                    c.createStatement().use { q ->q.executeUpdate("UPDATE invitation_deliveries SET status='FAILED',last_error_code='INTERRUPTED' WHERE status='SENDING' AND locked_at<now()-interval '2 minutes'") }
                    val row=c.prepareStatement("SELECT i.id::text,i.recipient_user_id::text,i.post_id::text,i.delivery_token,u.display_name FROM invitation_deliveries d JOIN post_invitations i ON i.id=d.invitation_id JOIN users u ON u.id=i.issuer_user_id JOIN social_posts p ON p.id=i.post_id WHERE d.status='PENDING' AND d.next_attempt_at<=now() AND i.status='PENDING' AND i.expires_at>now() AND i.recipient_user_id IS NOT NULL AND p.moderation_status='VISIBLE' AND u.status='ACTIVE' AND NOT u.publishing_blocked AND ogt_users_can_interact(i.issuer_user_id,i.recipient_user_id) ORDER BY d.next_attempt_at LIMIT 1 FOR UPDATE OF d SKIP LOCKED").use { q ->q.executeQuery().use { rs ->if(rs.next())(1..5).map { rs.getString(it) } else null } }
                    if(row!=null)c.prepareStatement("UPDATE invitation_deliveries SET status='SENDING',locked_at=now(),attempts=attempts+1 WHERE invitation_id=?::uuid").use { q ->q.setString(1,row[0]);q.executeUpdate() }
                    c.commit();row
                } catch(e:Exception) { c.rollback();throw e } finally { c.autoCommit=true }
            } ?: return count
            val ok=runCatching { notices.send(job[1],"${job[4]} te invita","Te invitaron a una publicación de OGT.","POST_INVITATION",job[2],invitationLink(job[3])) }.isSuccess
            db.withConnection { c ->c.prepareStatement("UPDATE invitation_deliveries SET status=?,accepted_at=CASE WHEN ? THEN now() ELSE accepted_at END,last_error_code=? WHERE invitation_id=?::uuid").use { q ->
                q.setString(1,if(ok)"ACCEPTED" else "FAILED");q.setBoolean(2,ok);q.setString(3,if(ok)null else "PUSH_UNAVAILABLE");q.setString(4,job[0]);q.executeUpdate()
            } }
            count++
        }
        return count
    }
}
