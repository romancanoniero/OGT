package com.onlygoodthings.backend.modules

import com.onlygoodthings.backend.auth.AuthPrincipal
import com.onlygoodthings.shared.domain.UserRole
import com.sun.net.httpserver.HttpServer
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
import kotlinx.coroutines.*
import kotlinx.serialization.json.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.net.InetSocketAddress
import java.util.concurrent.CopyOnWriteArrayList

class PublicationGeocoderTest {
    private val fixture="""{"features":[{"geometry":{"coordinates":[-58.3733,-34.6083]},"properties":{"name":"Plaza de Mayo","city":"Buenos Aires","country":"Argentina"}}]}"""
    private fun server(body:()->Pair<Int,String>,action:(PublicationGeocoder,MutableList<String>)->Unit) {
        val paths=CopyOnWriteArrayList<String>()
        val server=HttpServer.create(InetSocketAddress("127.0.0.1",0),0)
        server.createContext("/") { exchange ->
            paths+=exchange.requestURI.toString()
            val (status,text)=body();val bytes=text.toByteArray()
            exchange.sendResponseHeaders(status,bytes.size.toLong());exchange.responseBody.use { it.write(bytes) }
        }
        server.start()
        try { action(PublicationGeocoder("http://127.0.0.1:${server.address.port}",minimumIntervalMs=0),paths) }
        finally { server.stop(0) }
    }

    @Test fun `concurrent autocomplete is cached and uses a configurable Photon endpoint`() = server({200 to fixture}) { service,paths -> runBlocking {
        val hits=coroutineScope { (1..5).map { async { service.search("Plaza de Mayo & Buenos Aires") } }.awaitAll() }
        assertEquals(1,paths.size)
        assertTrue(paths.single().startsWith("/api/?q=Plaza+de+Mayo+%26+Buenos+Aires&lang=default&limit=5"))
        assertEquals("Plaza de Mayo, Buenos Aires, Argentina",hits.first().single().label)
        assertEquals(-34.6083,hits.first().single().latitude)
        assertEquals(hits.first(),service.search("  Plaza de Mayo & Buenos Aires  "))
        assertEquals(1,paths.size)
    } }

    @Test fun `reverse preserves coordinate order and rejects invalid input before contacting upstream`() = server({200 to fixture}) { service,paths -> runBlocking {
        val place=service.reverse(-34.6083,-58.3733,"en")!!
        assertEquals(-58.3733,place.longitude)
        assertTrue(paths.single().startsWith("/reverse?lat=-34.6083&lon=-58.3733&lang=en"))
        for((lat,lng) in listOf(91.0 to 0.0,0.0 to 181.0,Double.NaN to 0.0)) {
            try { service.reverse(lat,lng);fail("Invalid coordinates accepted") } catch(_:IllegalArgumentException) { }
        }
        assertEquals(emptyList<Any>(),service.search("ab"))
        assertEquals(1,paths.size)
    } }

    @Test fun `upstream failures are not cached and malformed places never become suggestions`() {
        var upstreamFailed=true
        server({if(upstreamFailed)503 to "unavailable" else 200 to fixture}) { service,paths -> runBlocking {
            try { service.search("Plaza");fail("Expected failure") } catch(_:IllegalStateException) { }
            upstreamFailed=false
            assertEquals(1,service.search("Plaza").size);assertEquals(2,paths.size)
        } }
        val invalid="""{"features":[{"geometry":{"coordinates":[0,91]},"properties":{"name":"Invalid"}},{"geometry":{"coordinates":[1,2]},"properties":{}},null]}"""
        assertTrue(parsePhotonPlaces(invalid).isEmpty())
    }

    @Test fun `HTTP place endpoints keep authentication validation and recoverable upstream errors`() = server({200 to fixture}) { service,paths -> testApplication {
        environment { config=MapApplicationConfig() }
        application {
            install(ContentNegotiation) { json() }
            install(StatusPages) { exception<IllegalArgumentException> { call,_ -> call.respond(HttpStatusCode.BadRequest) } }
            install(Authentication) { bearer("qa") { authenticate { if(it.token=="qa-place")AuthPrincipal("qa","qa",UserRole.USER,null) else null } } }
            routing { authenticate("qa") { publicationPlaceRoutes(service) } }
        }
        for(path in listOf("search","reverse")) {
            assertEquals(HttpStatusCode.Unauthorized,client.post("/api/v1/geo/places/$path") { contentType(ContentType.Application.Json);setBody("{}") }.status)
        }
        val result=client.post("/api/v1/geo/places/search") { bearerAuth("qa-place");contentType(ContentType.Application.Json);setBody("""{"query":"Plaza de Mayo"}""") }
        assertEquals(HttpStatusCode.OK,result.status)
        assertEquals("Plaza de Mayo, Buenos Aires, Argentina",Json.parseToJsonElement(result.bodyAsText()).jsonObject["data"]!!.jsonArray.single().jsonObject["label"]!!.jsonPrimitive.content)
        val invalid=client.post("/api/v1/geo/places/reverse") { bearerAuth("qa-place");contentType(ContentType.Application.Json);setBody("""{"latitude":91,"longitude":0}""") }
        assertEquals(HttpStatusCode.BadRequest,invalid.status);assertEquals(1,paths.size)
    } }
}
