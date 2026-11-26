package com.onlygoodthings.backend.social

import com.onlygoodthings.backend.infra.Database
import com.onlygoodthings.shared.domain.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import java.util.UUID

@Tag("business-integration")
class GoodActionRecognitionIntegrationTest {
    @Test fun newRecognitionsRequireOthersAndPreserveAdjacentFlowsAndHistoricalPosts() {
        val url = System.getenv("OGT_BUSINESS_TEST_JDBC").orEmpty()
        require(url.matches(Regex("jdbc:postgresql://127\\.0\\.0\\.1:19432/ogt_business_qa_[a-z0-9]+")))
        Database(url, "ogt", "ogt_qa_local_only").use { db ->
            val author = UUID.randomUUID().toString()
            val hero = UUID.randomUUID().toString()
            db.withConnection { c ->
                listOf(author to "Ana Vecina", hero to "Marta Vecina").forEach { (id, name) ->
                    c.prepareStatement("INSERT INTO users(id,firebase_uid,email,display_name,invite_code) VALUES(?::uuid,?,?,?,?)").use { q ->
                        q.setString(1, id); q.setString(2, "recognition-$id"); q.setString(3, "$id@example.test")
                        q.setString(4, name); q.setString(5, id); q.executeUpdate()
                    }
                }
            }
            val posts = SocialSqlRepository(db)
            val media = listOf(PostMediaItem(id = UUID.randomUUID().toString(), kind = MediaKind.IMAGE, url = "https://example.test/action.jpg"))
            fun publish(topic: String = "Limpieza", person: String? = null, name: String? = null, kind: String? = null) =
                posts.publish(author, "Una ayuda para el barrio", topic, media, null, null, person, emptyList(),
                    honoreeName = name, attributionUrl = "https://example.test/original", publicationKind = kind)
            for (person in listOf(null, author, author.uppercase())) {
                assertThrows(IllegalArgumentException::class.java) { publish(person = person) }
            }
            assertThrows(IllegalArgumentException::class.java) { publish(person = author, name = "Marta Vecina") }
            assertThrows(IllegalArgumentException::class.java) { publish(name = "@ana.vecina") }
            assertTrue(posts.postsByAuthor(author, author, 100, null).isEmpty())

            val recognized = publish(person = hero, name = "Nombre que no corresponde")
            assertEquals(hero, recognized.protagonistUserId)
            assertEquals("Marta Vecina", recognized.honoreeName)
            assertEquals(author, recognized.authorId)
            val reread = posts.postsByAuthor(hero, author, 100, null).single { it.id == recognized.id }
            assertEquals(hero, reread.protagonistUserId)
            assertEquals("Marta Vecina", reread.honoreeName)
            val edited = posts.editPost(recognized.id, author, "Relato corregido", "Reciclaje")
            assertEquals(hero, edited.protagonistUserId)
            assertEquals("Marta Vecina", edited.honoreeName)
            assertEquals("https://example.test/original", edited.attributionUrl)

            val invited = publish(name = "Luis Invitado")
            assertEquals("Luis Invitado", invited.honoreeName)
            assertNull(invited.protagonistUserId)
            for (topic in listOf("Ternura", "En vida", "Post mortem", "Comunidad", "Anécdota")) {
                assertNotNull(publish(topic = topic).id)
            }
            assertNotNull(publish(topic = "Reforestación", kind = "CONVOCATORIA").id)

            // Simulate an already existing own action, without creating it through the new contract.
            val historical = UUID.randomUUID().toString()
            db.withConnection { c ->
                c.prepareStatement("INSERT INTO social_posts(id,author_kind,author_user_id,body,media_urls,topic) VALUES(?::uuid,'USER',?::uuid,'Historia previa',ARRAY['https://example.test/old.jpg'],'Limpieza')").use { q ->
                    q.setString(1, historical); q.setString(2, author); q.executeUpdate()
                }
            }
            val oldEdited = posts.editPost(historical, author, "Historia previa corregida", "Reciclaje")
            assertEquals(author, oldEdited.authorId)
            assertEquals("Historia previa corregida", oldEdited.body)
            assertThrows(IllegalArgumentException::class.java) { posts.editPost(historical, hero, "Edición ajena", null) }
            posts.deletePost(historical, author)
            assertTrue(posts.postsByAuthor(author, author, 100, null).none { it.id == historical })
        }
    }
}
