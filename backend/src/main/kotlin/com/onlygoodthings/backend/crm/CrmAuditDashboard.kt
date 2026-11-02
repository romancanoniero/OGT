package com.onlygoodthings.backend.crm

import com.onlygoodthings.backend.infra.Database
import kotlinx.serialization.Serializable
import java.time.Instant

@Serializable data class CrmCompressionSummary(
    val images:Long,val receivedBytes:Long,val storedBytes:Long,val measuredSinceEpochSeconds:Long?,
    val inputOutputRatio:Double?,val reductionPercent:Double?,
)
@Serializable data class CrmAuditActivity(val action:String,val targetType:String,val outcome:String,val atEpochSeconds:Long)
@Serializable data class CrmAuditTotals(val events24h:Long,val denied24h:Long,val errors24h:Long,val events7d:Long,val recent:List<CrmAuditActivity>)
@Serializable data class CrmDashboardStatistics(val newUsers24h:Long,val recentlySeen7d:Long,val posts24h:Long,val activeStories:Long)
@Serializable data class CrmAuditDashboard(
    val queriedAtEpochSeconds:Long,val statistics:CrmDashboardStatistics,val audit:CrmAuditTotals,
    val compression:CrmCompressionSummary,val operations:CrmOperationsSnapshot,
)

internal fun compressionSummary(images:Long,input:Long,output:Long,since:Long?):CrmCompressionSummary =
    CrmCompressionSummary(images,input,output,since,
        if(images>0 && output>0)input.toDouble()/output else null,
        if(images>0 && input>0)100.0*(input-output)/input else null)

/** Only aggregate audit data; no contacts, reasons, targets, IPs, secrets or content. */
internal class CrmAuditDashboardReader(private val db:Database,private val operations:CrmOperations=CrmOperations()) {
    fun read():CrmAuditDashboard = db.withConnection { c ->
        c.autoCommit=false
        try {
            c.createStatement().use { it.execute("SET TRANSACTION ISOLATION LEVEL REPEATABLE READ READ ONLY");it.execute("SET LOCAL statement_timeout='5000ms'") }
            val statistics=c.createStatement().use { q ->q.executeQuery("""
                SELECT (SELECT count(*) FROM users WHERE created_at>=now()-interval '24 hours'),
                    (SELECT count(*) FROM users WHERE status='ACTIVE' AND last_seen_at>=now()-interval '7 days'),
                    (SELECT count(*) FROM social_posts WHERE NOT is_story AND created_at>=now()-interval '24 hours' AND moderation_status='VISIBLE'),
                    (SELECT count(*) FROM social_posts WHERE is_story AND story_expires_at>now() AND moderation_status='VISIBLE')
            """.trimIndent()).use { r ->check(r.next());CrmDashboardStatistics(r.getLong(1),r.getLong(2),r.getLong(3),r.getLong(4)) } }
            val recent=c.createStatement().use { q ->q.executeQuery("SELECT action,target_type,outcome,EXTRACT(EPOCH FROM created_at)::bigint FROM crm_audit_events ORDER BY created_at DESC,id DESC LIMIT 12").use { r ->buildList { while(r.next())add(CrmAuditActivity(r.getString(1),r.getString(2),r.getString(3),r.getLong(4))) } } }
            val audit=c.createStatement().use { q ->q.executeQuery("""
                SELECT count(*) FILTER(WHERE created_at>=now()-interval '24 hours'),
                    count(*) FILTER(WHERE created_at>=now()-interval '24 hours' AND outcome='DENIED'),
                    count(*) FILTER(WHERE created_at>=now()-interval '24 hours' AND outcome='ERROR'),count(*)
                FROM crm_audit_events WHERE created_at>=now()-interval '7 days'
            """.trimIndent()).use { r ->check(r.next());CrmAuditTotals(r.getLong(1),r.getLong(2),r.getLong(3),r.getLong(4),recent) } }
            val compression=c.createStatement().use { q ->q.executeQuery("""
                SELECT count(*),COALESCE(sum(received_bytes),0),COALESCE(sum(stored_bytes),0),EXTRACT(EPOCH FROM min(created_at))::bigint
                FROM media_processing_metrics WHERE kind='IMAGE' AND source IN ('UPLOAD','STORY')
            """.trimIndent()).use { r ->check(r.next());val since=r.getObject(4)?.let { (it as Number).toLong() };compressionSummary(r.getLong(1),r.getLong(2),r.getLong(3),since) } }
            c.commit()
            CrmAuditDashboard(Instant.now().epochSecond,statistics,audit,compression,operations.read())
        } catch(error:Exception) { c.rollback();throw error }
        finally { c.autoCommit=true }
    }
}
