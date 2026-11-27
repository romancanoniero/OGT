package com.onlygoodthings.app.data

import com.onlygoodthings.app.platform.OgtPickedMedia
import com.onlygoodthings.app.ui.screens.replaceAnimalMedia
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

class AdoptionPersistenceTest {
    @Test fun modernFieldsCreditsFramingAndCountersSurviveNativeSaveAndRetry() = runBlocking<Unit> {
        val db=OgtLocalDatabase()
        val me=LocalUser("owner","firebase-owner",null,null,"Persona QA",null,UserRole.USER,0,"QA","Barrio","","",-34.6,-58.4)
        db.users+=me
        val details=AdoptionDetails(presentation="Presentación del grupo",tags=listOf(AdoptionTag("species-cat","Gatos","gatos","SPECIES","CAT"),AdoptionTag("vaccination-some","Algunos vacunados","algunosvacunados","VACCINATION","SOME")),availableCount=3)
        val post=db.publishAnimalListing(me,"ADOPTION","Hermanitos","CAT","","Presentación del grupo","Barrio",title="Tres HERMANITOS buscan hogar",adoptionDetails=details)!!
        val previous=LocalPostMedia("existing-media",post.id,MediaKind.IMAGE,"https://media.test/animals.jpg",sortOrder=0,assetKey="",altText="Foto original",durationMs=1000,frameX=.2,frameY=.6,frameZoom=1.7)
        db.postMedia+=previous;db.posts[0]=db.posts[0].copy(impactCount=4,commentCount=2)
        db.replaceAnimalMedia(post.id,"Adopción",listOf(OgtPickedMedia(previous.id,previous.kind,previous.url,false)))
        assertEquals(previous,db.mediaOf(post.id).single())
        val listingId="11111111-1111-4111-8111-111111111111";val postId="22222222-2222-4222-8222-222222222222"
        var calls=0
        HttpClient(MockEngine { request ->
            calls++;val json=Json.parseToJsonElement((request.body as TextContent).text).jsonObject
            assertEquals("Tres HERMANITOS buscan hogar",json["title"]!!.jsonPrimitive.content)
            assertEquals("",json["ageLabel"]!!.jsonPrimitive.content)
            assertEquals("",json["attributionUrl"]!!.jsonPrimitive.content)
            val sent=json["media"]!!.jsonArray.single().jsonObject
            assertEquals(previous.id,sent["id"]!!.jsonPrimitive.content);assertEquals(.2,sent["frameX"]!!.jsonPrimitive.double)
            assertEquals(1000,sent["durationMs"]!!.jsonPrimitive.int)
            val sentDetails=Json.decodeFromJsonElement(AdoptionDetails.serializer(),json["adoptionDetails"]!!)
            assertEquals(details.presentation,sentDetails.presentation);assertEquals(3,sentDetails.availableCount)
            assertEquals(if(calls==1)"/api/v1/animals/publish" else "/api/v1/animals/update",request.url.encodedPath)
            val remote=AnimalListingDto(listingId,postId,me.id,me.firebaseUid,"ADOPTION","CAT","","LOW","Tres HERMANITOS buscan hogar","Presentación del grupo","Barrio",0,false,
                petName="Hermanitos",adoptionDetails=details,createdAtEpochMs=1234,media=listOf(PostMediaItem(previous.id,previous.kind,previous.url,durationMs=1000,frameX=.2,frameY=.6,frameZoom=1.7)))
            respond(Json.encodeToString(ApiResponse.ok(remote)),HttpStatusCode.OK,headersOf(HttpHeaders.ContentType,"application/json"))
        }) { install(ContentNegotiation) { json() } }.use { client ->
            val api=RestAnimalsRepository(client,SessionStore())
            val live=persistAnimalOnServer(db,api,me,db.post(post.id)!!,emptyList(),null,{true}) { _,_->error("Existing photos must not upload again") }
            assertEquals(details,db.animals.single().adoptionDetails);assertEquals(4,live.impactCount);assertEquals(2,live.commentCount)
            assertEquals(1234,db.animals.single().createdAtEpochMs)
            persistAnimalOnServer(db,api,me,live,emptyList(),null,{true}) { _,_->error("No reupload on retry") }
            assertEquals(2,calls);assertEquals(1,db.posts.size);assertNull(db.posts.single().attributionUrl)
        }
    }
}
