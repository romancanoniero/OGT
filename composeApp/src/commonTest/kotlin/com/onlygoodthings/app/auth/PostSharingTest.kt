package com.onlygoodthings.app.auth

import com.onlygoodthings.shared.data.SessionStore
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.*
import io.ktor.http.content.TextContent
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import kotlin.test.*

class PostSharingTest {
    private val headers=headersOf(HttpHeaders.ContentType,"application/json")
    private val thread="""{"success":true,"data":{"matchId":"thread-qa","peerUserId":"peer-qa","peerName":"Persona QA","tag":"mensaje","tagLabel":"Mensaje"}}"""
    @Test fun theCapturedAccountSendsOnlyAfterConversationConfirmationAndKeepsTheLink()=runBlocking {
        val paths=mutableListOf<String>();val link="https://onlygoodthings.lat/p/qa-post"
        HttpClient(MockEngine { request ->
            assertEquals("Bearer captured-owner-token",request.headers[HttpHeaders.Authorization]);paths+=request.url.encodedPath
            val data=Json.parseToJsonElement((request.body as TextContent).text).jsonObject
            when(request.url.encodedPath){
                "/api/v1/timebank/start"->{assertEquals("peer-qa",data["userId"]!!.jsonPrimitive.content);respond(thread,HttpStatusCode.OK,headers)}
                else->{assertEquals("thread-qa",data["matchId"]!!.jsonPrimitive.content);val body=data["body"]!!.jsonPrimitive.content;assertTrue(body.startsWith(link));assertEquals(800,body.length);respond("""{"success":true,"data":{"id":"msg-qa","matchId":"thread-qa","senderId":"owner-qa","senderName":"QA","body":"ok","createdAtEpochMs":1}}""",HttpStatusCode.OK,headers)}
            }
        }){install(ContentNegotiation){json()}}.use { client ->
            val captured=SessionStore().apply {firebaseJwt="captured-owner-token";apiBaseUrl="https://qa.example.test"}
            AuthSessionApi(captured,client).sharePostTo("peer-qa","Una historia ".repeat(100),link){true}
        }
        assertEquals(listOf("/api/v1/timebank/start","/api/v1/timebank/message/send"),paths)
    }
    @Test fun aChangedOwnerOrFailedConversationNeverSendsThePost()=runBlocking {
        for(failed in listOf(false,true)){
            var current=true;var calls=0
            HttpClient(MockEngine { request ->
                calls++;assertEquals("/api/v1/timebank/start",request.url.encodedPath);current=false
                respond(if(failed)"""{"success":false,"message":"No disponible"}""" else thread,if(failed)HttpStatusCode.BadRequest else HttpStatusCode.OK,headers)
            }){install(ContentNegotiation){json()}}.use { client ->
                val captured=SessionStore().apply {firebaseJwt="owner-token";apiBaseUrl="https://qa.example.test"}
                assertFailsWith<Exception>{AuthSessionApi(captured,client).sharePostTo("peer-qa","Texto","https://onlygoodthings.lat/p/qa"){current}}
            }
            assertEquals(1,calls)
        }
    }
}
