package com.onlygoodthings.backend.auth

import com.onlygoodthings.backend.infra.Database
import com.onlygoodthings.backend.crm.crmFirebaseTombstoneHash
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import java.util.UUID
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.*

@Tag("integration")
class AccountErasureIntegrationTest {
    private fun database():Database {
        val url=System.getenv("OGT_ERASURE_TEST_JDBC").orEmpty()
        require(url.matches(Regex("jdbc:postgresql://127\\.0\\.0\\.1:19432/ogt_erasure_qa_[a-z0-9]+"))) { "Use a dedicated QA loopback database; production is forbidden" }
        return Database(url,"ogt","ogt_qa_local_only")
    }
    private fun add(db:Database):Pair<String,String> {
        val uid="qa-erasure-"+UUID.randomUUID();val hash=crmFirebaseTombstoneHash(uid)
        db.withConnection { c ->
            c.prepareStatement("INSERT INTO crm_erased_accounts(uid_hash,reason) VALUES(?,'Solicitud de baja de la identidad sintética de QA')").use { q ->q.setString(1,hash);q.executeUpdate() }
            c.prepareStatement("INSERT INTO account_erasure_jobs(uid_hash,firebase_uid) VALUES(?,?)").use { q ->q.setString(1,hash);q.setString(2,uid);q.executeUpdate() }
        }
        return uid to hash
    }
    @Test fun failedProviderWorkSurvivesUntilANewWorkerCompletesItAndClearsTheUid()=database().use { db ->
        val(uid,hash)=add(db)
        AccountErasureService(db) { throw IdentityDeletionFailure("PERMISSION_DENIED") }.processPending()
        db.withConnection { c ->c.prepareStatement("SELECT firebase_uid,attempts,last_error_code,completed_at IS NULL,next_attempt_at>now() FROM account_erasure_jobs WHERE uid_hash=?").use { q ->
            q.setString(1,hash);q.executeQuery().use { r ->assertTrue(r.next());assertEquals(uid,r.getString(1));assertEquals(1,r.getInt(2));assertEquals("PERMISSION_DENIED",r.getString(3));assertTrue(r.getBoolean(4));assertTrue(r.getBoolean(5)) }
        } }
        db.withConnection { c ->c.prepareStatement("UPDATE account_erasure_jobs SET next_attempt_at=now() WHERE uid_hash=?").use { q ->q.setString(1,hash);q.executeUpdate() } }
        var deleted=0
        AccountErasureService(db) { assertEquals(uid,it);deleted++ }.processPending()
        AccountErasureService(db) { deleted++ }.processPending();assertEquals(1,deleted)
        db.withConnection { c ->c.prepareStatement("SELECT firebase_uid,attempts,last_error_code,completed_at IS NOT NULL FROM account_erasure_jobs WHERE uid_hash=?").use { q ->
            q.setString(1,hash);q.executeQuery().use { r ->assertTrue(r.next());assertNull(r.getString(1));assertEquals(2,r.getInt(2));assertNull(r.getString(3));assertTrue(r.getBoolean(4)) }
        } }
    }
    @Test fun twoWorkersNeverProcessTheSameDeletionConcurrently()=database().use { db ->
        add(db);val started=CountDownLatch(1);val release=CountDownLatch(1);val calls=AtomicInteger()
        val first=CompletableFuture.runAsync { AccountErasureService(db) {calls.incrementAndGet();started.countDown();check(release.await(5,TimeUnit.SECONDS))}.processPending() }
        try {
            assertTrue(started.await(2,TimeUnit.SECONDS))
            CompletableFuture.runAsync { AccountErasureService(db) { calls.incrementAndGet() }.processPending() }.get(2,TimeUnit.SECONDS)
        } finally { release.countDown() }
        first.get(2,TimeUnit.SECONDS);assertEquals(1,calls.get())
    }
}
