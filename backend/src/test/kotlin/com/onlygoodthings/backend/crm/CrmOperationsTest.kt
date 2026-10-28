package com.onlygoodthings.backend.crm

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import java.nio.file.Files

class CrmOperationsTest {
    @Test fun validRecentSnapshotStaleSnapshotAndCorruptionHaveDistinctSafeStates() {
        val folder=Files.createTempDirectory("ogt-operations-test");val path=folder.resolve("status.json")
        try {
            val reader=CrmOperations(path)
            assertEquals("unavailable",reader.read(1000).status)
            Files.writeString(path,"""{"schemaVersion":1,"checkedAtEpochSeconds":1000,"status":"healthy","checks":{"database":{"ok":true,"paymentIncidents":0}},"alerts":[],"failures":{}}""")
            assertEquals("healthy",reader.read(1100).status);assertFalse(reader.read(1100).stale)
            assertEquals("stale",reader.read(1300).status);assertTrue(reader.read(1300).stale)
            assertEquals("stale",reader.read(900).status)
            Files.writeString(path,"PRIVATE_CREDENTIAL_DO_NOT_RETURN")
            assertEquals(CrmOperationsSnapshot(),reader.read(1000))
            Files.writeString(path,"x".repeat(65537));assertEquals(CrmOperationsSnapshot(),reader.read(1000))
        }finally{Files.deleteIfExists(path);Files.deleteIfExists(folder)}
    }
    @Test fun wrongSchemaUnsafeFieldsAndSymlinksAreNotExposed() {
        val folder=Files.createTempDirectory("ogt-operations-invalid");val path=folder.resolve("status.json");val other=folder.resolve("private")
        try {
            Files.writeString(path,"""{"schemaVersion":2,"status":"healthy"}""");assertEquals("unavailable",CrmOperations(path).read().status)
            Files.writeString(path,"""{"status":"healthy","checks":{"private-file":{"ok":true}}}""");assertEquals("unavailable",CrmOperations(path).read().status)
            Files.delete(path);Files.writeString(other,"secret");Files.createSymbolicLink(path,other)
            assertEquals("unavailable",CrmOperations(path).read().status)
        }finally{Files.deleteIfExists(path);Files.deleteIfExists(other);Files.deleteIfExists(folder)}
    }
}
