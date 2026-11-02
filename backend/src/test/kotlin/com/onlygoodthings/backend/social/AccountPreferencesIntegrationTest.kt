package com.onlygoodthings.backend.social

import com.onlygoodthings.backend.infra.Database
import com.onlygoodthings.backend.infra.IdentityStore
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import java.util.UUID

@Tag("business-integration")
class AccountPreferencesIntegrationTest {
    private fun database(): Database {
        val url=System.getenv("OGT_BUSINESS_TEST_JDBC").orEmpty()
        require(url.matches(Regex("jdbc:postgresql://127\\.0\\.0\\.1:19432/ogt_business_qa_[a-z0-9]+"))) { "Only an isolated QA database is allowed" }
        return Database(url,"ogt","ogt_qa_local_only")
    }
    private fun user(db:Database):String {
        val id=UUID.randomUUID().toString()
        db.withConnection { c ->c.prepareStatement("INSERT INTO users(id,firebase_uid,email,display_name,invite_code) VALUES(?::uuid,?,?,'Persona de ajustes',?)").use { q ->
            q.setString(1,id);q.setString(2,"settings-$id");q.setString(3,"$id@example.test");q.setString(4,id);q.executeUpdate()
        } }
        return id
    }
    @Test fun settingsPersistByOwnerAndVisibilityProtectsProfilesWithoutDroppingOwnAccess():Unit=database().use { db ->
        val a=user(db);val b=user(db);val repository=SocialSqlRepository(db)
        val original=repository.ownSettings(b)
        repository.updateSettings(a,mapOf("publicProfileVisible" to false,"animalAlertPush" to false,"radarEnabled" to false))
        repository.updateSettings(a,mapOf("quietHoursEnabled" to false))
        val restored=SocialSqlRepository(db).ownSettings(a)
        assertFalse(restored.publicProfileVisible);assertFalse(restored.animalAlertPush);assertFalse(restored.radarEnabled);assertFalse(restored.quietHoursEnabled)
        assertEquals(original,repository.ownSettings(b))
        assertNotNull(repository.neighborCard(a,a));assertNull(repository.neighborCard(b,a))
        repository.follow(b,a);assertNotNull(repository.neighborCard(b,a))
        assertThrows(IllegalArgumentException::class.java) { repository.updateSettings(a,mapOf("userId" to b,"publicProfileVisible" to true)) }
        assertFalse(repository.ownSettings(a).publicProfileVisible)
    }
    @Test fun exportedMediaAndContentBelongToTheCallingAccount():Unit=database().use { db ->
        val a=user(db);val b=user(db);val ids=listOf(UUID.randomUUID().toString(),UUID.randomUUID().toString())
        db.withConnection { c ->listOf(a,b).forEachIndexed { index,user ->
            c.prepareStatement("INSERT INTO social_posts(id,author_kind,author_user_id,body) VALUES(?::uuid,'USER',?::uuid,?)").use { q ->q.setString(1,ids[index]);q.setString(2,user);q.setString(3,"Contenido de $user");q.executeUpdate() }
            c.prepareStatement("INSERT INTO post_media(post_id,kind,url) VALUES(?::uuid,'IMAGE',?)").use { q ->q.setString(1,ids[index]);q.setString(2,"https://example.test/$user.png");q.executeUpdate() }
        } }
        val exported=IdentityStore(db).exportAccount(a)!!
        assertEquals(a,exported.userId)
        assertEquals(listOf(ids[0]),exported.posts.map { it.id })
        assertEquals(1,exported.records["media"]!!.size)
        val json=exported.records.toString()
        assertTrue(json.contains("https://example.test/$a.png"));assertFalse(json.contains("https://example.test/$b.png"))
    }
}
