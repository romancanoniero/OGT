package com.onlygoodthings.backend.modules

import com.onlygoodthings.shared.domain.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class AdoptionValidationTest {
    private val cat = AdoptionTag("species-cat", "Gatos", "gatos", "SPECIES", "CAT")
    private val some = AdoptionTag("vaccination-some", "Algunos vacunados", "algunosvacunados", "VACCINATION", "SOME")
    private val catalog = listOf(cat, some)
    private val request = mapOf<String, Any?>("kind" to "ADOPTION", "title" to "HERMANITOS buscan hogar", "description" to "Presentación", "place" to "Caballito",
        "adoptionDetails" to mapOf("tags" to listOf(mapOf("id" to cat.id), mapOf("id" to some.id)), "availableCount" to 2),
        "media" to listOf(mapOf("url" to "https://media.test/cats.jpg")))

    @Test fun titleIsNotNormalizedAsAPersonNameAndOptionalFieldsStayOptional() {
        val write = animalWriteFrom(request, null, catalog)
        assertEquals("HERMANITOS buscan hogar", write.title)
        assertEquals("CAT", write.species);assertEquals("", write.size);assertEquals("", write.ageLabel)
        assertFalse(write.vaccinated);assertEquals("SOME", write.adoptionDetails!!.tags.last().legacyValue)
        assertEquals(2, write.adoptionDetails!!.availableCount)
    }
    @Test fun invalidModernDraftsAndUnsafeAttributionsAreRejectedBeforeAnyDatabaseWrite() {
        for (field in listOf("title", "description", "place")) assertThrows(IllegalArgumentException::class.java) { animalWriteFrom(request + (field to ""), null, catalog) }
        assertThrows(IllegalArgumentException::class.java) { animalWriteFrom(request + ("title" to "x".repeat(121)), null, catalog) }
        assertThrows(IllegalArgumentException::class.java) { animalWriteFrom(request + ("media" to emptyList<Any>()), null, catalog) }
        assertThrows(IllegalArgumentException::class.java) { animalWriteFrom(request + ("attributionUrl" to "javascript:alert(1)"), null, catalog) }
        assertNull(animalWriteFrom(request + ("attributionUrl" to "  "), null, catalog).attributionUrl)
    }
}
