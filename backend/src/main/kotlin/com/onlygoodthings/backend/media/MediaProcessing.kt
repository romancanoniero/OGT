package com.onlygoodthings.backend.media

import com.onlygoodthings.shared.domain.MediaKind
import java.sql.Connection

/** Recorded in the same transaction as the successful upload/publication. */
internal fun recordMediaProcessing(connection: Connection, source: String, kind: MediaKind, receivedBytes: Long, storedBytes: Long) {
    require(source in setOf("UPLOAD", "STORY", "STORY_POSTER"))
    require(receivedBytes in 1..MediaStore.MAX_BYTES.toLong() && storedBytes > 0)
    connection.prepareStatement("INSERT INTO media_processing_metrics(source,kind,received_bytes,stored_bytes) VALUES(?,?,?,?)").use { q ->
        q.setString(1,source);q.setString(2,kind.name);q.setLong(3,receivedBytes);q.setLong(4,storedBytes);q.executeUpdate()
    }
}
