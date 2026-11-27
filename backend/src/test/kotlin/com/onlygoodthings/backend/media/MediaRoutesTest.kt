package com.onlygoodthings.backend.media

import com.onlygoodthings.backend.auth.AuthPrincipal
import com.onlygoodthings.shared.domain.UserRole
import io.ktor.client.request.*
import io.ktor.client.statement.bodyAsText
import io.ktor.client.statement.bodyAsBytes
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.config.MapApplicationConfig
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.response.respond
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import kotlinx.serialization.json.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import java.util.Base64

class MediaRoutesTest {
    @TempDir lateinit var root: Path
    @Test fun `public videos support the byte ranges requested by Apple without bypassing revocation`() = testApplication {
        val store = MediaStore(root, "http://localhost")
        val bytes = ByteArray(512) { it.toByte() }
        val video = store.save(bytes, "video.mp4", "video/mp4")
        val revoked = store.save(bytes, "revoked.mp4", "video/mp4")
        environment { config = MapApplicationConfig() }
        application {
            install(ContentNegotiation) { json() }
            routing { publicMediaRoutes(store) { it != revoked.filename } }
        }
        val path = "/media/u/${video.filename}"
        val entire = client.get(path)
        assertEquals(HttpStatusCode.OK, entire.status)
        assertArrayEquals(bytes, entire.bodyAsBytes())
        val conditional = client.get(path) { header(HttpHeaders.Range, "bytes=0-1"); header(HttpHeaders.IfRange, "unknown-validator") }
        assertEquals(HttpStatusCode.OK, conditional.status)
        assertArrayEquals(bytes, conditional.bodyAsBytes())
        for ((range, expected) in listOf(
            "bytes=0-1" to (0..1), "bytes=128-255" to (128..255),
            "bytes=500-" to (500..511), "bytes=-12" to (500..511),
            "bytes=500-999" to (500..511),
        )) {
            val response = client.get(path) { header(HttpHeaders.Range, range) }
            assertEquals(HttpStatusCode.PartialContent, response.status, range)
            assertEquals("bytes", response.headers[HttpHeaders.AcceptRanges])
            assertEquals("bytes ${expected.first}-${expected.last}/512", response.headers[HttpHeaders.ContentRange])
            assertEquals("video/mp4", response.headers[HttpHeaders.ContentType]?.substringBefore(';'))
            assertArrayEquals(bytes.copyOfRange(expected.first, expected.last + 1), response.bodyAsBytes())
        }
        for (range in listOf("bytes=512-", "bytes=3-2", "bytes=-0", "bytes=999999999999999999999-")) {
            val response = client.get(path) { header(HttpHeaders.Range, range) }
            assertEquals(HttpStatusCode.RequestedRangeNotSatisfiable, response.status, range)
            assertEquals("bytes */512", response.headers[HttpHeaders.ContentRange])
        }
        assertEquals(HttpStatusCode.NotFound, client.get("/media/u/${revoked.filename}") {
            header(HttpHeaders.Range, "bytes=0-1")
        }.status)
        assertEquals(HttpStatusCode.NotFound, client.get("/media/u/00000000-0000-0000-0000-000000000000.mp4") {
            header(HttpHeaders.Range, "bytes=0-1")
        }.status)
    }
    @Test fun `authenticated HTTP upload returns a public compressed image and rejects bad input`() = testApplication {
        environment { config = MapApplicationConfig() }
        application {
            install(ContentNegotiation) { json() }
            install(StatusPages) { exception<IllegalArgumentException> { call, _ -> call.respond(HttpStatusCode.BadRequest) } }
            install(Authentication) {
                bearer("qa") { authenticate { if (it.token == "qa-session") AuthPrincipal("qa", "qa", UserRole.USER, null) else null } }
            }
            routing {
                val store = MediaStore(root, "http://localhost")
                publicMediaRoutes(store)
                authenticate("qa") { mediaRoutes(store) }
            }
        }
        val unauthorized = client.post("/api/v1/media/upload") { contentType(ContentType.Application.Json); setBody("{}") }
        assertEquals(HttpStatusCode.Unauthorized, unauthorized.status)
        val original = javaClass.getResourceAsStream("/images/rotated.jpg")!!.use { it.readBytes() }
        val payload = buildJsonObject {
            put("filename", "portrait.jpg"); put("contentType", "image/jpeg")
            put("bytesBase64", Base64.getEncoder().encodeToString(original))
        }
        val uploaded = client.post("/api/v1/media/upload") {
            bearerAuth("qa-session"); contentType(ContentType.Application.Json); setBody(payload.toString())
        }
        assertEquals(HttpStatusCode.OK, uploaded.status)
        val data = Json.parseToJsonElement(uploaded.bodyAsText()).jsonObject["data"]!!.jsonObject
        val filename = data["filename"]!!.jsonPrimitive.content
        val public = client.get("/media/u/$filename")
        assertEquals(HttpStatusCode.OK, public.status)
        assertEquals("image/jpeg", public.headers[HttpHeaders.ContentType]?.substringBefore(';'))
        val bytes = java.nio.file.Files.readAllBytes(root.resolve("u").resolve(filename))
        assertTrue(bytes.size < original.size)
        val bad = client.post("/api/v1/media/upload") {
            bearerAuth("qa-session"); contentType(ContentType.Application.Json)
            setBody("""{"filename":"bad.jpg","contentType":"image/jpeg","bytesBase64":"not-base64!"}""")
        }
        assertEquals(HttpStatusCode.BadRequest, bad.status)
        for (invalid in listOf("{}", "{broken-json", """{"filename":"bad.svg","bytesBase64":"YWJj"}""")) {
            val rejected = client.post("/api/v1/media/upload") {
                bearerAuth("qa-session"); contentType(ContentType.Application.Json); setBody(invalid)
            }
            assertEquals(HttpStatusCode.BadRequest, rejected.status)
        }
        assertEquals(HttpStatusCode.NotFound, client.get("/media/u/not-found.jpg").status)
    }
    @Test fun `revocation and cleanup racing a read return not found rather than an error`() = testApplication {
        val store=MediaStore(root,"http://localhost")
        val original=javaClass.getResourceAsStream("/images/rotated.jpg")!!.use { it.readBytes() }
        val revoked=store.save(original,"portrait.jpg","image/jpeg")
        val racing=store.save(original,"portrait.jpg","image/jpeg")
        environment { config=MapApplicationConfig() }
        application {
            routing { publicMediaRoutes(store) { name ->
                if(name==racing.filename)store.deleteLocal(racing.url)
                name!=revoked.filename
            } }
        }
        assertEquals(HttpStatusCode.NotFound,client.get("/media/u/${revoked.filename}").status)
        assertNotNull(store.resolvePublic(revoked.filename))
        assertEquals(HttpStatusCode.NotFound,client.get("/media/u/${racing.filename}").status)
    }

}
