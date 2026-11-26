package com.onlygoodthings.shared.domain

import kotlin.test.*
import com.onlygoodthings.shared.data.local.LocalFeedRepository
import com.onlygoodthings.shared.data.local.OgtLocalDatabase
import com.onlygoodthings.shared.data.local.OgtIds
import com.onlygoodthings.shared.data.local.seeded
import kotlinx.coroutines.runBlocking

class GoodActionRecognitionTest {
    @Test fun actionTopicsRequireAnotherPersonIncludingLegacyClients() {
        listOf("Reforestación", "Limpieza", "Reciclaje", "Ayuda comunitaria", "Ayuda vecinal", "Rescate Animal", "Movilidad Eco")
            .forEach { assertTrue(GoodActionRecognition.isAction(it)) }
        assertTrue(GoodActionRecognition.isAction("tema nuevo", "ACTION"))
        assertTrue(GoodActionRecognition.isAction(" reforestacion "))
    }

    @Test fun otherFlowsKeepTheirOwnRulesEvenWithAnOverlappingTopic() {
        listOf("Ternura", "En vida", "Post mortem", "Anécdota", "Comunidad", "Huerta comunitaria")
            .forEach { assertFalse(GoodActionRecognition.isAction(it)) }
        assertFalse(GoodActionRecognition.isAction("Reforestación", "CONVOCATORIA"))
        assertFailsWith<IllegalArgumentException> { GoodActionRecognition.isAction("Limpieza", "UNKNOWN") }
    }

    @Test fun missingOrSelfProtagonistIsRejectedEvenWithAnotherName() {
        assertFailsWith<IllegalArgumentException> { GoodActionRecognition.validate("me", null, "  ") }
        assertFailsWith<IllegalArgumentException> { GoodActionRecognition.validate("me", "ME", "Otra persona") }
    }

    @Test fun anotherAccountOrAnInvitedPersonIsAccepted() {
        GoodActionRecognition.validate("me", "other", null)
        GoodActionRecognition.validate("me", null, "Marta Vecina", "Ana Vecina")
        GoodActionRecognition.validate("me", "other", "Ana Vecina", "Ana Vecina")
    }

    @Test fun ownNameAndHandleCannotReplaceAnotherPerson() {
        listOf("Ána Vecina", "@ana.vecina", " ANA VECINA ").forEach {
            assertFailsWith<IllegalArgumentException> { GoodActionRecognition.validate("me", null, it, "Ana Vecina") }
        }
    }

    @Test fun localPublicationRetainsTheOtherPersonAndRejectedDraftsDoNotChangeTheFeed() = runBlocking {
        val db = OgtLocalDatabase.seeded()
        val author = OgtIds.Mariana
        val hero = db.users.first { it.id != author }
        val repository = LocalFeedRepository(db, author)
        val media = listOf(PostMediaItem("photo", MediaKind.IMAGE, "https://example.test/photo.jpg"))
        val before = db.posts.size
        assertFailsWith<IllegalArgumentException> { repository.publishPost("Una ayuda", "Limpieza", media, protagonistUserId = author) }
        assertEquals(before, db.posts.size)
        val post = repository.publishPost("Una ayuda", "Limpieza", media, protagonistUserId = hero.id)
        assertEquals(hero.id, post.protagonistUserId)
        assertEquals(hero.displayName, post.honoreeName)
        assertEquals(hero.displayName, db.creditName(db.post(post.id)!!))
        val edited = db.editOwnPost(author, post.id, "Un relato corregido")!!
        assertEquals(hero.displayName, db.creditName(edited))
        assertEquals(hero.id, db.toDomain(edited).protagonistUserId)
    }
}
