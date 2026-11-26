package com.onlygoodthings.backend.social

import com.onlygoodthings.backend.infra.Database
import com.onlygoodthings.shared.domain.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import java.util.UUID

@Tag("business-integration")
class PostInvitationIntegrationTest {
    private fun database():Database {
        val url=System.getenv("OGT_BUSINESS_TEST_JDBC").orEmpty()
        require(url.matches(Regex("jdbc:postgresql://127\\.0\\.0\\.1:19432/ogt_business_qa_[a-z0-9]+")))
        return Database(url,"ogt","ogt_qa_local_only")
    }
    private fun user(db:Database,name:String,email:String="${UUID.randomUUID()}@example.test"):String {
        val id=UUID.randomUUID().toString()
        db.withConnection { c ->c.prepareStatement("INSERT INTO users(id,firebase_uid,email,display_name,invite_code) VALUES(?::uuid,?,?,?,?)").use { q ->q.setString(1,id);q.setString(2,"invite-$id");q.setString(3,email);q.setString(4,name);q.setString(5,id);q.executeUpdate() } }
        return id
    }
    private val media=listOf(PostMediaItem("photo",MediaKind.IMAGE,"https://example.test/photo.jpg"))
    @Test fun categoriesFullNamesAndInvitationsAreAtomicIdempotentAndPreserveHistoricalPosts()=database().use { db ->
        val author=user(db,"Autora Prueba");val hero=user(db,"Irma Perfil");val categories=RecognitionCategories(db)
        val created=categories.save(author,mapOf("label" to "Impacto ${UUID.randomUUID()}","icon" to "🌱","sortOrder" to 8))
        val posts=SocialSqlRepository(db);val invites=PostInvitations(db);val request=UUID.randomUUID().toString()
        val drafts=listOf(PostInvitationDraft("Irma Sartirana","RECOGNITION","IN_APP",recipientUserId=hero),PostInvitationDraft("Lucía Vecina",channel="SHARE"))
        fun publish(ds:List<PostInvitationDraft> = drafts,key:String=request)=posts.publish(author,"Ayudó al barrio",created.label,media,null,null,hero,emptyList(),honoreeName="Irma Sartirana",honoreeFullName="Irma Sartirana",publicationKind="ACTION",categoryId=created.id,requestId=key,invitations=ds)
        val post=publish();assertEquals("Irma Sartirana",post.honoreeName);assertEquals(created.id,post.categoryId)
        assertEquals(post.id,publish().id);assertEquals(2,invites.mine(author,post.id).size)
        assertEquals(1,invites.inbox(hero).size)
        invites.accept(invites.inbox(hero).single().token!!,hero)
        assertEquals("Irma Sartirana",posts.byId(post.id,hero)!!.honoreeName)
        assertThrows(IllegalArgumentException::class.java){publish(emptyList())}
        val before=posts.postsByAuthor(author,author,100,null).size
        assertThrows(IllegalArgumentException::class.java){publish(listOf(PostInvitationDraft("X",channel="EMAIL",contact="bad")),UUID.randomUUID().toString())}
        assertEquals(before,posts.postsByAuthor(author,author,100,null).size)
        val inactive=categories.save(author,mapOf("id" to created.id,"version" to created.version,"label" to created.label,"active" to false))
        assertFalse(categories.list().any{it.id==created.id});assertTrue(categories.list(true).any{it.id==created.id})
        assertThrows(IllegalArgumentException::class.java){publish(emptyList(),UUID.randomUUID().toString())}
        val edited=posts.editPost(post.id,author,"Relato corregido",created.label,honoreeFullName="Irma María Sartirana",categoryId=created.id)
        assertEquals("Irma María Sartirana",edited.honoreeName);assertEquals(hero,edited.protagonistUserId)
        assertThrows(IllegalArgumentException::class.java){categories.save(author,mapOf("id" to created.id,"version" to created.version,"label" to created.label))}
        assertEquals(inactive.version,created.version+1)
    }
    @Test fun linksRequireRecipientPreventCreditHijackingAndSupportRevocationDeclineAndExpiry()=database().use { db ->
        val author=user(db,"Autora Invitaciones");val recipient=user(db,"Irma Sartirana");val wrong=user(db,"Otra Persona")
        val posts=SocialSqlRepository(db);val invites=PostInvitations(db)
        val post=posts.publish(author,"Ayudó al barrio","Limpieza",media,null,null,null,emptyList(),honoreeName="Irma Sartirana",publicationKind="ACTION")
        val row=invites.create(author,post.id,listOf(PostInvitationDraft("Irma Sartirana","RECOGNITION","IN_APP",recipientUserId=recipient))).single()
        val token=row.token!!;val public=invites.peek(token)!!
        assertNull(public.token);assertNull(public.contact);assertEquals("",public.channel);assertEquals("",public.maskedContact)
        assertThrows(IllegalArgumentException::class.java){invites.mine(wrong,post.id)}
        assertThrows(IllegalArgumentException::class.java){invites.accept(token,wrong)}
        assertEquals("ACCEPTED",invites.accept(token,recipient).status)
        assertEquals("ACCEPTED",invites.accept(token,recipient).status)
        assertEquals(recipient,posts.byId(post.id,recipient)!!.protagonistUserId)
        assertThrows(IllegalArgumentException::class.java){invites.accept(token,recipient,true)}
        val participation=invites.create(author,post.id,listOf(PostInvitationDraft("Otra Persona",channel="SHARE"))).first{it.purpose=="PARTICIPATION"}
        invites.shareOpened(author,participation.id)
        assertEquals("SHARE_OPENED",invites.mine(author,post.id).first{it.id==participation.id}.deliveryStatus)
        assertEquals("ACCEPTED",invites.accept(participation.token!!,wrong).status)
        assertEquals(recipient,posts.byId(post.id,wrong)!!.protagonistUserId)
        val withdrawn=invites.create(author,post.id,listOf(PostInvitationDraft("Persona Nueva",channel="SHARE"))).first{it.recipientName=="Persona Nueva"}
        invites.revoke(author,withdrawn.id);assertNull(invites.peek(withdrawn.token!!))
        val resent=invites.create(author,post.id,listOf(PostInvitationDraft("Persona Nueva",channel="SHARE"))).first{it.recipientName=="Persona Nueva"&&it.status=="PENDING"}
        assertNotEquals(withdrawn.id,resent.id)
        assertEquals("DECLINED",invites.accept(resent.token!!,wrong,true).status);assertNull(invites.peek(resent.token!!))
        val exp=invites.create(author,post.id,listOf(PostInvitationDraft("Otra Nueva",channel="EMAIL",contact="new@example.test"))).first{it.recipientName=="Otra Nueva"}
        db.withConnection { c->c.prepareStatement("UPDATE post_invitations SET expires_at=now()-interval '1 second' WHERE id=?::uuid").use { q->q.setString(1,exp.id);q.executeUpdate() } }
        assertNull(invites.peek(exp.token!!));assertThrows(IllegalArgumentException::class.java){invites.accept(exp.token!!,wrong)}
        db.withConnection { c->c.prepareStatement("INSERT INTO user_blocks(blocker_id,blocked_id) VALUES(?::uuid,?::uuid)").use { q->q.setString(1,author);q.setString(2,wrong);q.executeUpdate() } }
        assertThrows(IllegalArgumentException::class.java){invites.create(author,post.id,listOf(PostInvitationDraft("Otra Persona",channel="IN_APP",recipientUserId=wrong)))}
        Unit
    }
    @Test fun emailAndPhoneInvitationsBindToTheirRecipientAfterRegistration()=database().use { db ->
        val author=user(db,"Autora Contactos");val wrong=user(db,"Otra Cuenta")
        val posts=SocialSqlRepository(db);val invites=PostInvitations(db)
        val post=posts.publish(author,"Una buena acción","Limpieza",media,null,null,null,emptyList(),honoreeName="Persona Invitada",publicationKind="ACTION")
        val email="${UUID.randomUUID()}@example.test"
        val phone="+5411"+UUID.randomUUID().toString().filter(Char::isDigit).take(8)
        val rows=invites.create(author,post.id,listOf(PostInvitationDraft("Persona Email",channel="EMAIL",contact=email),PostInvitationDraft("Persona Teléfono",channel="SMS",contact=phone)))
        val mail=rows.single{it.channel=="EMAIL"};val sms=rows.single{it.channel=="SMS"}
        val newUser=user(db,"Persona Invitada",email)
        db.withConnection { c->c.prepareStatement("UPDATE users SET phone_e164=? WHERE id=?::uuid").use{ q->q.setString(1,phone);q.setString(2,newUser);q.executeUpdate() } }
        for(row in listOf(mail,sms)) {
            assertThrows(IllegalArgumentException::class.java){invites.accept(row.token!!,wrong)}
            val public=invites.peek(row.token!!)!!;assertNull(public.contact);assertEquals("",public.maskedContact)
            assertEquals(post.id,invites.accept(row.token!!,newUser).postId)
        }
        assertNull(posts.byId(post.id,newUser)!!.protagonistUserId)
    }

}
