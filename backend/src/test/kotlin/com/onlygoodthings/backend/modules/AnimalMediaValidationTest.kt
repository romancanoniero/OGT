package com.onlygoodthings.backend.modules

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class AnimalMediaValidationTest {
    private val draft = mapOf<String, Any?>("kind" to "ADOPTION", "species" to "CAT", "petName" to "Monchis QA", "description" to "Historia QA")
    private fun withMedia(url: String, poster: String? = null) = draft + ("media" to listOf(mapOf("url" to url, "posterUrl" to poster)))

    @Test fun `twenty media remain in order and twenty one are rejected`() {
        val rows = (0 until 20).map { mapOf("kind" to if(it%2==0)"IMAGE" else "VIDEO", "url" to "https://media.test/$it.jpg") }
        val saved = animalWriteFrom(draft + ("media" to rows), null)
        assertEquals(rows.map { it["url"] }, saved.media.map { it.url })
        assertThrows(IllegalArgumentException::class.java) { animalWriteFrom(draft + ("media" to (rows + rows.first())), null) }
    }

    @Test fun `device paths and unuploaded posters are rejected before database writes`() {
        for (url in listOf("file:///var/mobile/pet.jpg", "content://photos/1", "asset://pet.jpg", "data:image/jpeg;base64,a", "/cache/pet.jpg", "//device/pet.jpg", "https://", "https://media.test/a\n.jpg", "https://user:password@media.test/a.jpg")) {
            assertThrows(IllegalArgumentException::class.java) { animalWriteFrom(withMedia(url), null) }
        }
        assertThrows(IllegalArgumentException::class.java) { animalWriteFrom(withMedia("https://media.test/pet.mp4", "file:///poster.jpg"), null) }
    }

    @Test fun `remote media and existing empty lists retain compatible animal fields`() {
        for (url in listOf("https://media.test/pet.jpg", "http://localhost:19083/media/u/11111111-1111-4111-8111-111111111111.jpg", "/media/u/11111111-1111-4111-8111-111111111111.jpg")) {
            val write = animalWriteFrom(withMedia(url), null)
            assertEquals(url, write.media.single().url)
            assertEquals("ADOPTION", write.kind)
            assertEquals("CAT", write.species)
        }
        assertTrue(animalWriteFrom(draft, null).media.isEmpty())
        val lost = draft + mapOf("kind" to "LOST", "marks" to "Collar", "lastSeenPlace" to "Plaza", "latitude" to -34.6, "longitude" to -58.4)
        assertEquals("LOST", animalWriteFrom(lost, null).kind)
        assertThrows(IllegalArgumentException::class.java) { animalWriteFrom(lost + withMedia("file:///pet.jpg").filterKeys { it == "media" }, null) }
    }
}
