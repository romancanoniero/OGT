package com.onlygoodthings.backend.media

import com.onlygoodthings.backend.stories.StoryMediaStore
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import java.util.UUID
import java.util.concurrent.Executors

class DeduplicationTest {
    @TempDir lateinit var root: Path
    private fun picture()=javaClass.getResourceAsStream("/images/transparent.webp")!!.readBytes()
    @Test fun duplicateUploadsKeepIndependentUrlsAndShareOnlyPhysicalBytes() {
        val a=MediaStore(root,"https://example.test");val b=MediaStore(root,"https://example.test")
        val first=a.save(picture(),"first.webp","image/webp");val second=b.save(picture(),"second.webp","image/webp")
        assertNotEquals(first.id,second.id);assertNotEquals(first.url,second.url)
        val one=a.resolvePublic(first.filename)!!;val two=b.resolvePublic(second.filename)!!
        assertTrue(Files.isSameFile(one,two))
        val original=Files.readAllBytes(two)
        assertTrue(a.deleteLocal(first.url));assertArrayEquals(original,Files.readAllBytes(two))
        assertTrue(b.deleteLocal(second.url))
        Files.list(root.resolve("u/.objects")).use { assertEquals(listOf(".lock"),it.map { p->p.fileName.toString() }.toList()) }
    }
    @Test fun concurrentUploadsRemainReadableAndDoNotReplaceAnExistingId() {
        val store=MediaStore(root,"https://example.test");val workers=Executors.newFixedThreadPool(4)
        try {
            val files=(1..12).map { workers.submit<com.onlygoodthings.shared.domain.UploadedMedia> { store.save(picture(),"x.webp",null) } }.map { it.get() }
            assertEquals(12,files.map { it.id }.toSet().size)
            assertTrue(files.all { Files.isSameFile(store.resolvePublic(files.first().filename),store.resolvePublic(it.filename)) })
            assertThrows(java.nio.file.FileAlreadyExistsException::class.java) { store.save(picture(),"x.webp",null,files.first().id) }
            assertNotNull(store.resolvePublic(files.first().filename))
        } finally { workers.shutdownNow() }
    }
    @Test fun storyDeletionCannotDeleteAnotherStoryOrPublicMediaAndReplacingKeepsTheOtherCopy() {
        val story=StoryMediaStore(root,"https://example.test");val one=UUID.randomUUID().toString();val two=UUID.randomUUID().toString()
        story.write(one,picture());story.write(two,picture())
        assertTrue(Files.isSameFile(story.path(one),story.path(two)))
        story.write(one,byteArrayOf(1,2,3),poster=true)
        story.write(one,byteArrayOf(4,5,6))
        assertArrayEquals(picture(),Files.readAllBytes(story.path(two)))
        story.delete(one);assertTrue(Files.isRegularFile(story.path(two)))
        assertFalse(Files.exists(story.path(one,true)))
        assertNull(MediaStore(root,"https://example.test").resolvePublic(two+".mp4"))
        assertTrue(StoryMediaStore(root,"https://example.test").url(two,"owner",120000).contains("/media/stories/"))
    }
}
