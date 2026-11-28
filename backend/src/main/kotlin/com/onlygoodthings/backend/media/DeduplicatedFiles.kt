package com.onlygoodthings.backend.media

import java.nio.channels.FileChannel
import java.nio.file.Files
import java.nio.file.LinkOption.NOFOLLOW_LINKS
import java.nio.file.Path
import java.nio.file.StandardCopyOption.*
import java.nio.file.StandardOpenOption.*
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/** Share physical bytes, never public URLs, ownership, signatures or deletion rights. */
internal class DeduplicatedFiles(private val directory: Path) {
    private val objects = directory.resolve(".objects")
    init {
        Files.createDirectories(directory)
        require(!Files.isSymbolicLink(objects))
        Files.createDirectories(objects)
    }

    private fun <T> locked(block: () -> T): T = locks.computeIfAbsent(directory.toRealPath()) { ReentrantLock() }.withLock {
        FileChannel.open(objects.resolve(".lock"), CREATE, WRITE).use { channel -> channel.lock().use { block() } }
    }

    fun write(destination: Path, bytes: ByteArray, replace: Boolean = false) = locked {
        require(destination.parent == directory && !Files.isSymbolicLink(destination))
        val prior = if (replace && Files.isRegularFile(destination, NOFOLLOW_LINKS)) hash(Files.readAllBytes(destination)) else null
        val digest = hash(bytes)
        val blob = objects.resolve(digest)
        require(!Files.isSymbolicLink(blob))
        if (!Files.exists(blob, NOFOLLOW_LINKS)) {
            val pending = Files.createTempFile(objects, ".pending-", ".tmp")
            try {
                Files.write(pending, bytes)
                FileChannel.open(pending, WRITE).use { it.force(true) }
                Files.move(pending, blob, ATOMIC_MOVE)
            } finally { Files.deleteIfExists(pending) }
        } else {
            check(Files.isRegularFile(blob, NOFOLLOW_LINKS) && Files.size(blob) == bytes.size.toLong() &&
                MessageDigest.isEqual(Files.readAllBytes(blob), bytes)) { "Stored media integrity mismatch" }
        }
        val alias = Files.createTempFile(objects, ".pending-", ".tmp")
        try {
            Files.delete(alias)
            Files.createLink(alias, blob)
            if (replace) Files.move(alias, destination, ATOMIC_MOVE, REPLACE_EXISTING)
            else {
                // createLink is exclusive even on platforms where ATOMIC_MOVE replaces a target.
                Files.createLink(destination, alias)
                Files.delete(alias)
            }
        } finally {
            Files.deleteIfExists(alias)
            collect(digest)
            if (prior != null && prior != digest) collect(prior)
        }
    }

    fun delete(destination: Path): Boolean = locked {
        require(destination.parent == directory && !Files.isSymbolicLink(destination))
        if (!Files.isRegularFile(destination, NOFOLLOW_LINKS)) return@locked false
        val digest = MessageDigest.getInstance("SHA-256").let { sum ->
            Files.newInputStream(destination).use { input ->
                val buffer=ByteArray(64*1024)
                while(true) {val count=input.read(buffer);if(count<0)break;sum.update(buffer,0,count)}
            }
            sum.digest().joinToString("") { "%02x".format(it) }
        }
        Files.delete(destination)
        collect(digest)
        true
    }

    private fun collect(digest: String) {
        val blob = objects.resolve(digest)
        if (Files.isRegularFile(blob, NOFOLLOW_LINKS) && (Files.getAttribute(blob, "unix:nlink", NOFOLLOW_LINKS) as Number).toInt() == 1)
            Files.delete(blob)
    }

    companion object {
        private val locks = ConcurrentHashMap<Path, ReentrantLock>()
        private fun hash(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
    }
}
