package com.onlygoodthings.shared.data.remote

import com.onlygoodthings.shared.data.SessionStore
import com.onlygoodthings.shared.domain.*
import com.sun.net.httpserver.HttpServer
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import java.net.InetSocketAddress
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.test.*

class PetDiscoveryRequestsTest {
 @Test fun nativeReadsSupplyOnlyTheExplicitCurrentFixAndOmitItWhenGpsIsUnavailable()=runBlocking {
  val requests=CopyOnWriteArrayList<Pair<String,JsonObject>>()
  val server=HttpServer.create(InetSocketAddress("127.0.0.1",0),0)
  server.createContext("/api/v1/") { exchange ->
   val path=exchange.requestURI.path
   requests+=path to Json.parseToJsonElement(exchange.requestBody.bufferedReader().readText()).jsonObject
   val data=if(path.endsWith("feed-scope"))"{}" else "[]"
   val response="{\"success\":true,\"data\":$data}".toByteArray()
   exchange.responseHeaders.set("Content-Type","application/json");exchange.sendResponseHeaders(200,response.size.toLong())
   exchange.responseBody.use { it.write(response) }
  }
  server.start()
  val client=HttpClient(CIO) { install(ContentNegotiation) { json() } }
  try {
   val session=SessionStore().apply { apiBaseUrl="http://127.0.0.1:${server.address.port}" }
   val feed=RestFeedRepository(client,session);val animals=RestAnimalsRepository(client,session)
   val point=GeoPoint(-34.62,-58.44)
   feed.discoveryScope(point);feed.loadNearbyFeed(null,20,FeedMode.HOME,null,20,point);animals.openListings(point)
   assertEquals(3,requests.size)
   for((_,body) in requests) { assertEquals(point.latitude,body.getValue("latitude").jsonPrimitive.double);assertEquals(point.longitude,body.getValue("longitude").jsonPrimitive.double) }
   requests.clear()
   feed.discoveryScope();feed.loadNearbyFeed(null,20,FeedMode.HOME,null,20);animals.openListings()
   assertEquals(3,requests.size)
   for((_,body) in requests) { assertFalse("latitude" in body);assertFalse("longitude" in body) }
  } finally { client.close();server.stop(0) }
 }
}
