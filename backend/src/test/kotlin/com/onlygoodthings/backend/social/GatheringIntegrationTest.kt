package com.onlygoodthings.backend.social

import com.onlygoodthings.backend.infra.Database
import com.onlygoodthings.shared.domain.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import java.util.UUID

@Tag("business-integration")
class GatheringIntegrationTest {
    @Test fun gatheringPersistsItsScheduleWithoutRequiringAnotherPersonAndPreservesLegacyContracts() {
        val url=System.getenv("OGT_BUSINESS_TEST_JDBC").orEmpty()
        require(url.matches(Regex("jdbc:postgresql://127\\.0\\.0\\.1:19432/ogt_business_qa_[a-z0-9]+")))
        Database(url,"ogt","ogt_qa_local_only").use { db ->
            val author=UUID.randomUUID().toString();val stranger=UUID.randomUUID().toString()
            db.withConnection { c ->for(id in listOf(author,stranger))c.prepareStatement("INSERT INTO users(id,firebase_uid,display_name,invite_code,email) VALUES(?::uuid,?,'Organizador QA',?,?)").use{q->q.setString(1,id);q.setString(2,id);q.setString(3,id);q.setString(4,"$id@example.test");q.executeUpdate()} }
            val posts=SocialSqlRepository(db);val media=listOf(PostMediaItem("photo",MediaKind.IMAGE,"https://example.test/event.jpg"));val start=System.currentTimeMillis()+86400000;val key=UUID.randomUUID().toString()
            fun publish(place:String="Parque comunitario",date:Long=start,kind:String="CONVOCATORIA",request:String=key)=posts.publish(author,"Nos juntamos a plantar","Reforestación",media,null,null,null,emptyList(),publicationKind=kind,place=place,eventStartsAtEpochMs=date,requestId=request)
            val post=publish();assertEquals("CONVOCATORIA",post.publicationKind);assertNull(post.protagonistUserId);assertNull(post.honoreeName);assertEquals("Parque comunitario",post.place);assertEquals(start,post.eventStartsAtEpochMs);assertEquals(post.id,publish().id)
            assertThrows(IllegalArgumentException::class.java){publish(date=start+1)}
            assertThrows(IllegalArgumentException::class.java){publish(place="",request=UUID.randomUUID().toString())}
            assertThrows(IllegalArgumentException::class.java){publish(date=1,request=UUID.randomUUID().toString())}
            assertThrows(IllegalArgumentException::class.java){publish(kind="ACTION",request=UUID.randomUUID().toString())}
            assertThrows(IllegalArgumentException::class.java){posts.editPost(post.id,stranger,"Cambio ajeno",null,place="Otro parque",eventStartsAtEpochMs=start)}
            val edited=posts.editPost(post.id,author,"Traigan guantes",post.topic,place="Entrada del parque",eventStartsAtEpochMs=start)
            assertEquals(post.media,edited.media);assertEquals("Entrada del parque",edited.place)
            assertEquals(start,posts.editPost(post.id,author,"Nuevo relato",null).eventStartsAtEpochMs)
            val legacy=posts.publish(author,"Convocatoria anterior","Reforestación",media,null,null,null,emptyList(),publicationKind="CONVOCATORIA")
            assertNull(legacy.eventStartsAtEpochMs);assertEquals("Texto corregido",posts.editPost(legacy.id,author,"Texto corregido",null).body)
            val action=posts.publish(author,"Alguien plantó un árbol","Reforestación",media,null,null,null,emptyList(),honoreeName="Vecina Reconocida",publicationKind="ACTION")
            assertEquals("ACTION",action.publicationKind);assertFalse(isGatheringPost(action.topic,action.publicationKind));assertNull(action.eventStartsAtEpochMs)
            assertThrows(IllegalArgumentException::class.java){posts.editPost(action.id,author,"Cambio de tipo no permitido",null,place="Parque",eventStartsAtEpochMs=start)}
        }
    }
}
