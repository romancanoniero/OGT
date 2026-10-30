package com.onlygoodthings.backend.auth

import java.security.MessageDigest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*

class RecoveryPrivacyTest {
    @Test fun liveAndArbitraryDatabaseNamesAreRejected() {
        assertTrue(recoveryDatabaseAllowed("ogt_recovery_"+"a".repeat(32)))
        for(name in listOf("onlygoodthings","postgres","ogt_recovery_qa","ogt_recovery_"+"A".repeat(32),"ogt_recovery_"+"a".repeat(32)+";DROP"))assertFalse(recoveryDatabaseAllowed(name))
    }
    @Test fun aJournalMustCoverWritesAfterTheSnapshotAndHaveTheExpectedFingerprint() {
        val payload="""{"kind":"ogt-erasure-journal-v1","capturedAtEpochSeconds":200,"hashes":["${"a".repeat(64)}"]}""".toByteArray()
        val sha=MessageDigest.getInstance("SHA-256").digest(payload).joinToString("") {"%02x".format(it)}
        assertEquals(1,recoveryErasureJournal(payload,sha,100,200).hashes.size)
        assertThrows(IllegalArgumentException::class.java) {recoveryErasureJournal(payload,sha,100,201)}
        assertThrows(IllegalArgumentException::class.java) {recoveryErasureJournal(payload,"b".repeat(64),100,200)}
        assertThrows(IllegalArgumentException::class.java) {recoveryErasureJournal(payload,sha,201,200)}
    }
}
