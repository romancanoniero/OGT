package com.onlygoodthings.backend.crm

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import java.nio.file.Files

class CrmOperationsTest {
    @Test fun completeMediaInventoryRequiresConsistentTotalsAndRealVolumeCapacity() {
        val folder=Files.createTempDirectory("ogt-media-snapshot");val path=folder.resolve("status.json")
        val media="""{"ok":true,"complete":true,"files":0,"mediaBytes":0,"allocatedBytes":0,"uploadImageFiles":0,"uploadImageBytes":0,"uploadVideoFiles":0,"uploadVideoBytes":0,"storyFiles":0,"storyBytes":0,"posterFiles":0,"posterBytes":0,"otherFiles":0,"otherBytes":0,"diskTotalBytes":100,"diskFreeBytes":60,"diskUsedPercent":40}"""
        fun write(value:String){Files.writeString(path,"""{"schemaVersion":1,"checkedAtEpochSeconds":1000,"status":"healthy","checks":{"media":$value}}""")}
        try {
            write(media);assertEquals(0L,CrmOperations(path).read(1000).checks["media"]!!.mediaBytes)
            write(media.replace("\"mediaBytes\":0","\"mediaBytes\":999"));assertEquals("unavailable",CrmOperations(path).read(1000).status)
            write(media.replace("\"diskFreeBytes\":60","\"diskFreeBytes\":101"));assertEquals("unavailable",CrmOperations(path).read(1000).status)
            write("""{"ok":false,"complete":false}""");val partial=CrmOperations(path).read(1000).checks["media"]!!
            assertFalse(partial.complete!!);assertNull(partial.mediaBytes);assertNull(partial.diskFreeBytes)
        } finally {Files.deleteIfExists(path);Files.deleteIfExists(folder)}
    }
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
