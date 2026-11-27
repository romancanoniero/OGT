package com.onlygoodthings.app.data

import com.onlygoodthings.app.platform.OgtPickedMedia
import com.onlygoodthings.shared.data.SessionStore
import com.onlygoodthings.shared.data.local.*
import com.onlygoodthings.shared.data.remote.RestAnimalsRepository
import com.onlygoodthings.shared.domain.*
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.*
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.*
import io.ktor.http.content.TextContent
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*
import kotlin.test.*

class AnimalMediaUploadTest {
    private fun photo(url: String, order: Int = 0) = LocalPostMedia("m$order", "draft", MediaKind.IMAGE, url,
        sortOrder = order, assetKey = "", altText = "Adopción", frameX = 0.2, frameZoom = 1.5)

    @Test fun uploadsOnlyLocalFilesAndPreservesOrderAndExistingRemoteMedia() = runBlocking<Unit> {
        val existing = photo("https://media.test/old.jpg")
        val local = photo("file:///cache/pet.jpg", 1)
        val result = uploadAnimalMedia(listOf(existing, local)) { items, alt ->
            assertEquals("/cache/pet.jpg", items.single().path)
            assertTrue(items.single().fromDevice)
            assertEquals("Adopción", alt)
            listOf(PostMediaItem("uploaded", MediaKind.IMAGE, "https://media.test/pet.jpg"))
        }
        assertEquals(existing, result[0])
        assertEquals(local.copy(url = "https://media.test/pet.jpg"), result[1])
    }

    @Test fun videoUploadsItsPreparedCopyAndPosterRatherThanTheSource() = runBlocking<Unit> {
        val video = photo("file:///cache/trim.mp4").copy(kind = MediaKind.VIDEO, posterUrl = "file:///cache/poster.jpg")
        val result = uploadAnimalMedia(listOf(video)) { items, _ ->
            val item = items.single()
            assertTrue(item.preparedForUpload)
            assertEquals("/cache/trim.mp4", item.path)
            assertEquals("/cache/poster.jpg", item.posterPath)
            assertNull(item.sourcePath)
            listOf(PostMediaItem("v", MediaKind.VIDEO, "https://media.test/trim.mp4", "https://media.test/poster.jpg"))
        }
        assertEquals("https://media.test/poster.jpg", result.single().posterUrl)
    }

    @Test fun deviceOnlyUploadResponsesAndBundledAssetsCannotBecomePublishedMedia() = runBlocking<Unit> {
        assertFailsWith<IllegalArgumentException> {
            uploadAnimalMedia(listOf(photo("file:///pet.jpg"))) { _, _ -> listOf(PostMediaItem("p", MediaKind.IMAGE, "file:///pet.jpg")) }
        }
        assertFailsWith<IllegalArgumentException> {
            uploadAnimalMedia(listOf(photo("asset://sample.jpg"))) { _, _ -> error("Must not upload a sample") }
        }
    }

    @Test fun publishAndRetrySendOnlyUploadedUrlsAndBindTheSameLocalDraft() = runBlocking<Unit> {
        val db = OgtLocalDatabase()
        val me = LocalUser("owner", "firebase-owner", null, null, "Persona QA", null, UserRole.USER, 0, "QA", "Barrio", "", "", -34.6, -58.4)
        db.users += me
        val post = db.publishAnimalListing(me, "ADOPTION", "Monchis QA", "CAT", "MEDIUM", "Historia QA", "Barrio", ageLabel = "2 años", temperament = "Tranquilo")!!
        db.postMedia += photo("file:///missing-in-test/pet.jpg").copy(postId = post.id)
        var uploads = 0
        var requests = 0
        val remote = AnimalListingDto("11111111-1111-4111-8111-111111111111", "22222222-2222-4222-8222-222222222222",
            me.id, me.firebaseUid, "ADOPTION", "CAT", "MEDIUM", "LOW", "Monchis QA", "Historia QA", "Barrio", 0, false,
            petName = "Monchis QA", ageLabel = "2 años", temperament = "Tranquilo", createdAtEpochMs = 1234,
            media = listOf(PostMediaItem("p", MediaKind.IMAGE, "https://media.test/pet.jpg")))
        HttpClient(MockEngine { request ->
            requests++
            assertEquals(1, uploads, "Upload must complete before publish/update")
            val body = Json.parseToJsonElement((request.body as TextContent).text).jsonObject
            assertEquals(remote.media.single().url, body["media"]!!.jsonArray.single().jsonObject["url"]!!.jsonPrimitive.content)
            respond(Json.encodeToString(ApiResponse.ok(remote)), HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
        }) { install(ContentNegotiation) { json() } }.use { client ->
            val api = RestAnimalsRepository(client, SessionStore())
            val upload: suspend (List<OgtPickedMedia>, String) -> List<PostMediaItem> = { _, _ -> uploads++; remote.media }
            assertFailsWith<IllegalStateException> {
                persistUnpublishedAnimal(db, api, me, post, { true }) { _, _ -> error("Upload failed") }
            }
            assertEquals(0, requests)
            assertEquals(post.id, db.posts.single().id)
            assertTrue(db.mediaOf(post.id).single().url.startsWith("file:"))
            var ownerUnchanged = true
            assertFailsWith<IllegalStateException> {
                persistUnpublishedAnimal(db, api, me, post, { ownerUnchanged }) { _, _ -> ownerUnchanged = false; remote.media }
            }
            assertEquals(0, requests, "An account change during upload must prevent publication")
            val live = persistUnpublishedAnimal(db, api, me, post, { true }, upload)
            assertEquals(remote.postId, live.id)
            assertEquals(1, db.posts.size)
            assertEquals(remote.media.single().url, db.mediaOf(live.id).single().url)
            // Editing a server photo must not re-upload it or turn it into asset://https://…
            persistAnimalOnServer(db, api, me, live, emptyList(), null, { true }) { _, _ -> error("Must keep remote photo") }
            assertEquals(1, uploads)
            assertEquals(2, requests)
        }
    }
}
