package com.onlygoodthings.backend.crm

import com.onlygoodthings.backend.infra.Database
import com.onlygoodthings.backend.media.recordMediaProcessing
import com.onlygoodthings.backend.stories.*
import com.onlygoodthings.shared.domain.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import java.util.UUID
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString

@Tag("business-integration")
class CrmAuditDashboardIntegrationTest {
    @TempDir lateinit var mediaRoot:Path
    private fun database():Database {
        val url=System.getenv("OGT_BUSINESS_TEST_JDBC").orEmpty()
        require(url.matches(Regex("jdbc:postgresql://127\\.0\\.0\\.1:19432/ogt_business_qa_[a-z0-9]+")))
        return Database(url,"ogt","ogt_qa_local_only")
    }
    @Test fun aggregateMeasurementsExcludeVideoAndPostersAndAuditContainsNoPrivatePayload():Unit=database().use { db ->
        val reader=CrmAuditDashboardReader(db);val before=reader.read()
        db.withConnection { c ->
            recordMediaProcessing(c,"UPLOAD",MediaKind.IMAGE,1000,400)
            recordMediaProcessing(c,"STORY",MediaKind.IMAGE,100,200)
            recordMediaProcessing(c,"STORY_POSTER",MediaKind.IMAGE,5000,1000)
            recordMediaProcessing(c,"UPLOAD",MediaKind.VIDEO,8000,8000)
            c.prepareStatement("INSERT INTO crm_audit_events(action,target_type,target_id,reason,outcome,metadata) VALUES('QA_DASHBOARD','dashboard','PRIVATE_MARKER','PRIVATE_MARKER','DENIED','{\"private\":\"PRIVATE_MARKER\"}')").use { it.executeUpdate() }
        }
        val after=reader.read()
        assertEquals(before.compression.images+2,after.compression.images)
        assertEquals(before.compression.receivedBytes+1100,after.compression.receivedBytes)
        assertEquals(before.compression.storedBytes+600,after.compression.storedBytes)
        assertEquals(before.audit.denied24h+1,after.audit.denied24h)
        assertFalse(Json.encodeToString(after).contains("PRIVATE_MARKER"))
        db.withConnection { c ->c.autoCommit=false;try {recordMediaProcessing(c,"UPLOAD",MediaKind.IMAGE,999,100);c.rollback()} finally {c.autoCommit=true} }
        assertEquals(after.compression,reader.read().compression)
    }
    @Test fun anIdempotentStoryRetryRecordsOneSuccessfulProcessingMeasurement():Unit=database().use { db ->
        val owner=UUID.randomUUID().toString();val id=UUID.randomUUID().toString()
        db.withConnection { c ->c.prepareStatement("INSERT INTO users(id,firebase_uid,email,display_name,invite_code) VALUES(?::uuid,?,?,'Autor QA',?)").use { q ->q.setString(1,owner);q.setString(2,owner);q.setString(3,"$owner@example.test");q.setString(4,owner);q.executeUpdate() } }
        val reader=CrmAuditDashboardReader(db);val before=reader.read().compression
        val stories=StorySqlRepository(db,StoryMediaStore(mediaRoot,"https://example.test"))
        val bytes=javaClass.getResourceAsStream("/images/transparent.webp")!!.readBytes()
        repeat(2){stories.publish(owner,id,"Historia QA",StoryAudience.COMMUNITY,bytes,false,null)}
        val after=reader.read().compression
        assertEquals(before.images+1,after.images);assertEquals(before.receivedBytes+bytes.size,after.receivedBytes)
    }
}
