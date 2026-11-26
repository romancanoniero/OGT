package com.onlygoodthings.backend.social

import com.onlygoodthings.backend.infra.Database
import com.onlygoodthings.shared.domain.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import java.util.UUID

@Tag("business-integration")
class PublicationHashtagsIntegrationTest {
    private fun database():Database {
        val url=System.getenv("OGT_BUSINESS_TEST_JDBC").orEmpty()
        require(url.matches(Regex("jdbc:postgresql://127\\.0\\.0\\.1:19432/ogt_business_qa_[a-z0-9]+")))
        return Database(url,"ogt","ogt_qa_local_only")
    }
    private fun user(db:Database,name:String):String = UUID.randomUUID().toString().also { id ->
        db.withConnection { c ->c.prepareStatement("INSERT INTO users(id,firebase_uid,display_name,invite_code,email) VALUES(?::uuid,?,?,?,?)").use { q ->
            q.setString(1,id);q.setString(2,"hashtags-$id");q.setString(3,name);q.setString(4,id);q.setString(5,"$id@example.test");q.executeUpdate()
        } }
    }
    private val media=listOf(PostMediaItem("qa-media",MediaKind.IMAGE,"https://example.test/hashtags.jpg"))

    @Test fun historicalHashtagBackfillPreservesAllOtherFieldsIncludingEditTimeAndLeavesTheTriggerActive() {
        database().use { db ->
            val author=user(db,"Historia QA");val id=UUID.randomUUID().toString()
            db.withConnection { c ->
                c.prepareStatement("INSERT INTO social_posts(id,author_kind,author_user_id,body,media_urls,topic,publication_kind,created_at,updated_at) VALUES(?::uuid,'USER',?::uuid,'Historia anterior',ARRAY['https://example.test/backfill.jpg'],'Ternura','TERNURA','2020-01-01T00:00:00Z','2020-01-01T00:00:00Z')").use { q ->q.setString(1,id);q.setString(2,author);q.executeUpdate() }
                fun fingerprint()=c.prepareStatement("SELECT (to_jsonb(p)-'hashtags')::text FROM social_posts p WHERE id=?::uuid").use { q ->q.setString(1,id);q.executeQuery().use { r ->r.next();r.getString(1) } }
                val before=fingerprint()
                val migration=java.nio.file.Files.readString(java.nio.file.Path.of("../database/migrations/41_publication_hashtags.sql"))
                repeat(2) {
                    c.createStatement().use { it.execute(migration) }
                    assertEquals(before,fingerprint())
                    c.createStatement().use { q ->q.executeQuery("SELECT tgenabled FROM pg_trigger WHERE tgrelid='social_posts'::regclass AND tgname='trg_posts_updated'").use { r ->r.next();assertEquals("O",r.getString(1)) } }
                }
                c.prepareStatement("SELECT jsonb_array_length(hashtags) FROM social_posts WHERE id=?::uuid").use { q ->q.setString(1,id);q.executeQuery().use { r ->r.next();assertEquals(1,r.getInt(1)) } }
                c.prepareStatement("UPDATE social_posts SET body='Una edición real' WHERE id=?::uuid").use { q ->q.setString(1,id);q.executeUpdate() }
                c.prepareStatement("SELECT updated_at > '2020-01-02T00:00:00Z' FROM social_posts WHERE id=?::uuid").use { q ->q.setString(1,id);q.executeQuery().use { r ->r.next();assertTrue(r.getBoolean(1)) } }
            }
        }
    }
    private fun publish(posts:SocialSqlRepository,author:String,tags:List<String>,name:String?=null,hero:String?=null,request:String?=null)=
        posts.publish(author,"Una historia para compartir","Comunidad",media,null,null,hero,emptyList(),publicationKind="POST",requestId=request,honoreeFullName=name,hashtags=tags)

    @Test fun unifiedPublishingValidatesTagsCreditsOwnershipAndRetriesWithoutChangingAdjacentFlows() {
        database().use { db ->
            val author=user(db,"Ana Autora");val hero=user(db,"Irma María Sartirana");val other=user(db,"Otra Persona")
            val posts=SocialSqlRepository(db)
            val saved=publish(posts,author,listOf("#Ternura","Perros","Huerta_En_Casa","ternura"),request="hashtags_retry_1234")
            assertEquals("POST",saved.publicationKind)
            assertEquals(listOf("ternura","perros","huerta_en_casa"),saved.hashtags.map { it.key })
            assertNull(saved.honoreeName)
            assertEquals(saved.id,publish(posts,author,listOf("#Ternura","Perros","Huerta_En_Casa","ternura"),request="hashtags_retry_1234").id)
            assertEquals(saved.id,posts.publish(author,"Una historia para compartir","Comunidad",media.map { it.copy(id=UUID.randomUUID().toString()) },null,null,null,emptyList(),publicationKind="POST",requestId="hashtags_retry_1234",hashtags=listOf("#Ternura","Perros","Huerta_En_Casa","ternura")).id)
            assertThrows(IllegalArgumentException::class.java) { publish(posts,author,listOf("Gatos"),request="hashtags_retry_1234") }
            for(tags in listOf(emptyList(),listOf("<script>"),List(9) { "tema$it" }))assertThrows(IllegalArgumentException::class.java) { publish(posts,author,tags) }
            assertThrows(IllegalArgumentException::class.java) { publish(posts,author,listOf("Buena Acción"),"Ana Autora",author) }
            assertThrows(IllegalArgumentException::class.java) { publish(posts,author,listOf("buenaaccion")) }
            assertThrows(IllegalArgumentException::class.java) {
                posts.publish(author,"No se omite la regla cambiando el tipo","Ternura",media,null,null,null,emptyList(),publicationKind="TERNURA",hashtags=listOf("buenaaccion"))
            }
            val recognition=publish(posts,author,listOf("Buena Acción","Reforestación"),"Irma María Sartirana",hero)
            assertEquals(hero,recognition.protagonistUserId)
            assertEquals("Irma María Sartirana",recognition.honoreeName)
            assertNotNull(recognition.categoryId)
            assertThrows(IllegalArgumentException::class.java) { posts.editPost(saved.id,other,"Edición ajena",null,hashtags=listOf("Gatos")) }
            assertThrows(IllegalArgumentException::class.java) { posts.editPost(saved.id,author,"Otro relato",null,hashtags=listOf("buenaaccion")) }
            val edited=posts.editPost(saved.id,author,"Historia corregida",null,hashtags=listOf("ternura","Gatos"))
            assertEquals(listOf("ternura","gatos"),edited.hashtags.map { it.key })
            assertEquals(saved.mediaUrls,edited.mediaUrls)
            assertEquals("POST",edited.publicationKind)
            assertEquals("Plaza QA",posts.editPost(saved.id,author,"Con lugar",null,place="Plaza QA").place)
            assertEquals("",posts.editPost(saved.id,author,"Sin lugar",null,place="").place)
            val legacy=posts.publish(author,"Ternura anterior","Ternura",media,null,null,null,emptyList(),publicationKind="TERNURA")
            assertEquals("TERNURA",legacy.publicationKind)
            assertEquals("Ternura",posts.editPost(legacy.id,author,"Texto histórico corregido",null).topic)
            val gathering=posts.publish(author,"Encuentro barrial","Reforestación",media,null,null,null,emptyList(),publicationKind="CONVOCATORIA",place="Plaza QA",eventStartsAtEpochMs=System.currentTimeMillis()+86_400_000)
            assertEquals("CONVOCATORIA",gathering.publicationKind)
            assertNotNull(gathering.eventStartsAtEpochMs)
        }
    }

    @Test fun crmRenameDeactivationAndFreeAliasesPreserveHistoricalPostsAndInterests() {
        database().use { db ->
            val author=user(db,"Control Hashtags")
            val catalog=RecognitionCategories(db);val posts=SocialSqlRepository(db)
            val label="Ciencia "+UUID.randomUUID().toString().take(8)
            val category=catalog.save(author,mapOf("label" to label,"requiresRecognition" to false))
            val saved=publish(posts,author,listOf(category.tagKey))
            assertEquals(category.id,saved.hashtags.single().categoryId)
            val renamed=catalog.save(author,mapOf("id" to category.id,"version" to category.version,"label" to "Nuevo "+label,"requiresRecognition" to false))
            assertEquals(category.tagKey,renamed.tagKey)
            assertEquals(label,posts.byId(saved.id,author)!!.hashtags.single().label)
            val renamedPost=publish(posts,author,listOf(renamed.tagKey))
            assertEquals(saved.hashtags.single().interestKey,renamedPost.hashtags.single().interestKey)
            catalog.save(author,mapOf("id" to renamed.id,"version" to renamed.version,"label" to renamed.label,"requiresRecognition" to false,"active" to false))
            assertThrows(IllegalArgumentException::class.java) { publish(posts,author,listOf(category.tagKey)) }
            assertEquals(saved.id,posts.editPost(saved.id,author,"Se conserva el hashtag inactivo",null,hashtags=listOf(category.tagKey)).id)
            assertTrue(catalog.list().none { it.id==category.id })
        }
    }

    @Test fun personalizedRetrievalFindsInterestsBeyondRecentInventoryAndFollowingRemainsChronological() {
        database().use { db ->
            val viewer=user(db,"Lector de ciencia");val stranger=user(db,"Otra lectora");val author=user(db,"Autora de ciencia");val noise=user(db,"Autor de cocina")
            val posts=SocialSqlRepository(db)
            val topic="ciencia"+UUID.randomUUID().toString().take(8)
            repeat(6) { index ->
                val seen=publish(posts,author,listOf(topic))
                db.withConnection { c ->c.createStatement().use { q ->q.executeUpdate("UPDATE social_posts SET created_at=now()-INTERVAL '72 hours' WHERE id='${seen.id}'") } }
                posts.clap(seen.id,viewer)
            }
            val target=publish(posts,author,listOf(topic))
            db.withConnection { c ->
                c.createStatement().use { q ->q.executeUpdate("UPDATE social_posts SET created_at=now()-INTERVAL '20 hours' WHERE id='${target.id}'") }
                c.prepareStatement("INSERT INTO social_posts(id,author_kind,author_user_id,body,media_urls,topic,publication_kind,hashtags) SELECT gen_random_uuid(),'USER',?::uuid,'Receta reciente',ARRAY['https://example.test/cooking.jpg'],'Cocina','POST','[{\"key\":\"cocina\",\"label\":\"Cocina\"}]'::jsonb FROM generate_series(1,215)").use { q ->q.setString(1,noise);q.executeUpdate() }
            }
            assertEquals(target.id,posts.feed(viewer,FeedMode.HOME,20,null).first().id)
            assertNotEquals(target.id,posts.feed(stranger,FeedMode.HOME,20,null).first().id)
            posts.follow(viewer,author);posts.follow(viewer,noise)
            val following=posts.feed(viewer,FeedMode.FOLLOWING,40,null)
            assertEquals(following.map { it.createdAtEpochMs }.sortedDescending(),following.map { it.createdAtEpochMs })
            assertEquals(noise,following.first().authorId)
            posts.recordFeedEvent(viewer,target.id,FeedEventKind.HIDE,null)
            assertTrue(posts.feed(viewer,FeedMode.HOME,40,null).none { it.id==target.id })
            posts.deletePost(target.id,author)
            assertTrue(posts.feed(viewer,FeedMode.HOME,40,null).none { it.id==target.id })
        }
    }
}
