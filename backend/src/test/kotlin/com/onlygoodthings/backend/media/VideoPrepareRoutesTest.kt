package com.onlygoodthings.backend.media

import com.onlygoodthings.backend.auth.AuthPrincipal
import com.onlygoodthings.shared.domain.UserRole
import io.ktor.client.request.*
import io.ktor.client.statement.bodyAsText
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
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import java.util.Base64

class VideoPrepareRoutesTest {
    @TempDir lateinit var root: Path
    @Test fun chosenRangeIsAuthenticatedOwnedAndServedWithAppleRanges() {
        assumeTrue(runCatching { ProcessBuilder("ffmpeg","-version").redirectOutput(ProcessBuilder.Redirect.DISCARD).start().waitFor()==0 }.getOrDefault(false))
        val source=root.resolve("original.mp4")
        val encoder=ProcessBuilder("ffmpeg","-v","error","-y","-f","lavfi","-i","testsrc2=size=160x120:rate=24:duration=3",
            "-f","lavfi","-i","sine=frequency=440:duration=3","-c:v","libx264","-threads","1","-c:a","aac",source.toString()).inheritIO().start()
        assertEquals(0,encoder.waitFor())
        val input=Files.readAllBytes(source);val owners=mutableListOf<Pair<String,String>>()
        testApplication {
            environment { config=MapApplicationConfig() }
            application {
                install(ContentNegotiation) {json()}
                install(StatusPages) {exception<IllegalArgumentException> {call,_->call.respond(HttpStatusCode.BadRequest)}}
                install(Authentication) {bearer("qa") {authenticate { if(it.token=="session")AuthPrincipal("owner","firebase",UserRole.USER,null) else null }}}
                val store=MediaStore(root,"http://localhost")
                routing {publicMediaRoutes(store);authenticate("qa") {mediaRoutes(store,onUploaded={user,name->owners.add(user to name)})}}
            }
            val path="/api/v1/media/video/prepare"
            assertEquals(HttpStatusCode.Unauthorized,client.post(path).status)
            fun body(start:Long,end:Long)=buildJsonObject {put("bytesBase64",Base64.getEncoder().encodeToString(input));put("startMs",start);put("endMs",end)}.toString()
            val prepared=client.post(path) {bearerAuth("session");contentType(ContentType.Application.Json);setBody(body(1000,2000))}
            assertEquals(HttpStatusCode.OK,prepared.status)
            val data=Json.parseToJsonElement(prepared.bodyAsText()).jsonObject["data"]!!.jsonObject
            val filename=data["filename"]!!.jsonPrimitive.content
            assertEquals(listOf("owner" to filename),owners)
            val range=client.get("/media/u/$filename") {header(HttpHeaders.Range,"bytes=0-1")}
            assertEquals(HttpStatusCode.PartialContent,range.status)
            assertEquals(HttpStatusCode.BadRequest,client.post(path) {bearerAuth("session");contentType(ContentType.Application.Json);setBody(body(0,121000))}.status)
            assertEquals(1,owners.size);assertArrayEquals(input,Files.readAllBytes(source))
        }
    }
}
