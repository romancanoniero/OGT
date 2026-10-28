package com.onlygoodthings.backend.business

import com.onlygoodthings.backend.infra.Database
import com.onlygoodthings.backend.infra.IdentityStore
import com.onlygoodthings.backend.crm.crmModerateUsers
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Assertions.*
import java.util.UUID
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@Tag("business-integration")
class CsrTeamIntegrationTest {
    private fun id()=UUID.randomUUID().toString()
    private fun database():Database {
        val url=System.getenv("OGT_BUSINESS_TEST_JDBC").orEmpty()
        require(url.matches(Regex("jdbc:postgresql://127\\.0\\.0\\.1:19432/ogt_business_qa_[a-z0-9]+")))
        return Database(url,"ogt","ogt_qa_local_only")
    }
    private data class Fixture(val owner:String,val member:String,val outsider:String,val company:String)
    private fun fixture(db:Database):Fixture {
        val f=Fixture(id(),id(),id(),id())
        db.withConnection{c ->
            for(user in listOf(f.owner,f.member,f.outsider))c.prepareStatement("INSERT INTO users(id,firebase_uid,email,display_name,invite_code) VALUES(?::uuid,?,?,'Persona QA',?)").use{q ->q.setString(1,user);q.setString(2,"team-qa-$user");q.setString(3,"$user@example.test");q.setString(4,user);q.executeUpdate()}
            c.prepareStatement("INSERT INTO companies(id,legal_name,tax_id,verification_status) VALUES(?::uuid,'Equipo de prueba',?,'VERIFIED')").use{q ->q.setString(1,f.company);q.setString(2,id());q.executeUpdate()}
            c.prepareStatement("INSERT INTO company_admins(company_id,user_id,is_primary) VALUES(?::uuid,?::uuid,true)").use{q ->q.setString(1,f.company);q.setString(2,f.owner);q.executeUpdate()}
        };return f
    }
    private fun CsrTeam.join(f:Fixture,role:String="VIEWER"):String {
        val invitation=invite(f.owner,f.company,id(),f.member+"@example.test",role)
        decide(f.member,id(),invitation.id,false,"ACCEPT");return invitation.id
    }
    @Test fun invitationNeedsAcceptanceAndAnExactTenantRole():Unit=database().use {db ->
        val f=fixture(db);val r=CsrRepository(db);val t=CsrTeam(r);val request=id()
        val invitation=t.invite(f.owner,f.company,request,f.member+"@EXAMPLE.TEST","VIEWER")
        assertEquals(invitation,t.invite(f.owner,f.company,request,f.member+"@example.test","VIEWER"))
        assertThrows(BusinessAccessDenied::class.java){r.campaigns(f.member,f.company)}
        assertTrue(t.inbox(f.outsider).isEmpty());assertEquals(request,t.inbox(f.member).single().id)
        assertThrows(BusinessAccessDenied::class.java){t.decide(f.outsider,id(),request,false,"ACCEPT")}
        assertThrows(BusinessAccessDenied::class.java){t.decide(f.outsider,id(),request,false,"CANCEL")}
        val accept=id();assertEquals(t.decide(f.member,accept,request,false,"ACCEPT"),t.decide(f.member,accept,request,false,"ACCEPT"))
        assertTrue(r.campaigns(f.member,f.company).isEmpty());assertTrue(t.inbox(f.member).isEmpty())
        assertThrows(BusinessAccessDenied::class.java){r.reserve(f.member,f.company,id(),id(),100)}
        assertThrows(BusinessAccessDenied::class.java){t.invite(f.member,f.company,id(),f.outsider+"@example.test","ADMIN")}
        assertThrows(IllegalArgumentException::class.java){t.invite(f.owner,f.company,id(),f.outsider+"@example.test","OWNER")}
        assertThrows(IllegalArgumentException::class.java){t.member(f.owner,f.company,id(),f.owner,null)}
        assertThrows(IllegalArgumentException::class.java){t.member(f.owner,f.company,id(),f.owner,"VIEWER")}
    }
    @Test fun demotionAndRemovalOverrideEvenStaleLegacyGrants():Unit=database().use {db ->
        val f=fixture(db);val r=CsrRepository(db);val t=CsrTeam(r);t.join(f,"ADMIN")
        t.member(f.owner,f.company,id(),f.member,"VIEWER")
        // Simulate an old maintenance script reintroducing a stale legacy grant.
        db.withConnection{c ->c.prepareStatement("INSERT INTO company_admins(company_id,user_id,is_primary) VALUES(?::uuid,?::uuid,true)").use{q ->q.setString(1,f.company);q.setString(2,f.member);q.executeUpdate()}}
        assertEquals("VIEWER",t.desk(f.member,f.company).role)
        assertThrows(BusinessAccessDenied::class.java){r.saveCampaign(f.member,f.company,id(),null,"Una campaña","Descripción de prueba",0,0)}
        t.member(f.owner,f.company,id(),f.member,null)
        db.withConnection{c ->c.prepareStatement("INSERT INTO company_admins(company_id,user_id,is_primary) VALUES(?::uuid,?::uuid,true)").use{q ->q.setString(1,f.company);q.setString(2,f.member);q.executeUpdate()}}
        assertThrows(BusinessAccessDenied::class.java){r.campaigns(f.member,f.company)}
        assertThrows(BusinessAccessDenied::class.java){t.desk(f.member,f.company)}
        db.withConnection{c ->c.createStatement().use{it.execute(java.nio.file.Files.readString(java.nio.file.Path.of("../database/migrations/23_crm_foundations.sql")));it.execute(java.nio.file.Files.readString(java.nio.file.Path.of("../database/migrations/28_crm_user_moderation.sql")));it.execute(java.nio.file.Files.readString(java.nio.file.Path.of("../database/migrations/35_company_teams.sql")))}}
        assertThrows(BusinessAccessDenied::class.java){r.campaigns(f.member,f.company)}
        assertEquals("OWNER",t.desk(f.owner,f.company).role)
    }
    @Test fun adminsCannotManageOwnersOrPeerAdminsAndTheirOffersDieWhenDemoted():Unit=database().use {db ->
        val f=fixture(db);val t=CsrTeam(CsrRepository(db));t.join(f,"ADMIN")
        assertThrows(BusinessAccessDenied::class.java){t.propose(f.member,f.company,id(),f.owner)}
        assertThrows(BusinessAccessDenied::class.java){t.invite(f.member,f.company,id(),f.outsider+"@example.test","ADMIN")}
        val invitation=t.invite(f.member,f.company,id(),f.outsider+"@example.test","FINANCE")
        t.member(f.owner,f.company,id(),f.member,"VIEWER")
        assertTrue(t.inbox(f.outsider).isEmpty())
        assertThrows(IllegalArgumentException::class.java){t.decide(f.outsider,id(),invitation.id,false,"ACCEPT")}
        val fresh=t.invite(f.owner,f.company,id(),f.outsider+"@example.test","ADMIN")
        t.decide(f.outsider,id(),fresh.id,false,"ACCEPT")
        t.member(f.owner,f.company,id(),f.member,"ADMIN")
        assertThrows(BusinessAccessDenied::class.java){t.member(f.member,f.company,id(),f.outsider,"VIEWER")}
        assertThrows(BusinessAccessDenied::class.java){t.member(f.member,f.company,id(),f.outsider,null)}
    }
    @Test fun ownershipIsAcceptedAtomicallyAndFormerOwnerCanEraseOnlyTheirOwnData():Unit=database().use {db ->
        val f=fixture(db);val r=CsrRepository(db);val t=CsrTeam(r);t.join(f)
        assertThrows(IllegalArgumentException::class.java){IdentityStore(db).eraseAccount(f.owner)}
        val transfer=t.propose(f.owner,f.company,id(),f.member)
        assertEquals("OWNER",t.desk(f.owner,f.company).role)
        assertThrows(IllegalArgumentException::class.java){IdentityStore(db).eraseAccount(f.owner)}
        assertThrows(BusinessAccessDenied::class.java){t.decide(f.outsider,id(),transfer.id,true,"ACCEPT")}
        val accept=id();val first=t.decide(f.member,accept,transfer.id,true,"ACCEPT")
        assertEquals(first,t.decide(f.member,accept,transfer.id,true,"ACCEPT"))
        assertEquals("ADMIN",t.desk(f.owner,f.company).role);assertEquals("OWNER",t.desk(f.member,f.company).role)
        val exported=IdentityStore(db).exportAccount(f.owner)!!.companyTeam
        assertTrue(exported.any{it.kind=="TRANSFER"&&it.status=="ACCEPTED"})
        IdentityStore(db).eraseAccount(f.owner)
        assertEquals("OWNER",t.desk(f.member,f.company).role);assertTrue(r.finance(f.member,f.company).reconciled)
        assertThrows(IllegalArgumentException::class.java){IdentityStore(db).eraseAccount(f.member)}
        db.withConnection{c ->c.prepareStatement("SELECT count(*) FROM crm_audit_events WHERE company_id=?::uuid AND action='TEAM_TRANSFER_ACCEPT'").use{q ->q.setString(1,f.company);q.executeQuery().use{it.next();assertEquals(1,it.getInt(1))}}}
    }
    @Test fun expiredDeclinedRevokedAndCancelledOffersNeverGrantAccess():Unit=database().use {db ->
        val f=fixture(db);val t=CsrTeam(CsrRepository(db))
        val invitation=t.invite(f.owner,f.company,id(),f.member+"@example.test","VIEWER")
        t.decide(f.member,id(),invitation.id,false,"DECLINE")
        assertThrows(IllegalArgumentException::class.java){t.decide(f.member,id(),invitation.id,false,"ACCEPT")}
        val expired=t.invite(f.owner,f.company,id(),f.member+"@example.test","VIEWER")
        db.withConnection{c ->c.prepareStatement("UPDATE csr_team_invitations SET expires_at=now()-interval '1 second' WHERE id=?::uuid").use{q ->q.setString(1,expired.id);q.executeUpdate()}}
        assertTrue(t.inbox(f.member).isEmpty());assertThrows(IllegalArgumentException::class.java){t.decide(f.member,id(),expired.id,false,"ACCEPT")}
        t.join(f);val transfer=t.propose(f.owner,f.company,id(),f.member)
        t.decide(f.owner,id(),transfer.id,true,"CANCEL")
        assertThrows(IllegalArgumentException::class.java){t.decide(f.member,id(),transfer.id,true,"ACCEPT")}
        assertEquals("OWNER",t.desk(f.owner,f.company).role)
        val next=t.propose(f.owner,f.company,id(),f.member)
        t.member(f.owner,f.company,id(),f.member,null)
        assertThrows(IllegalArgumentException::class.java){t.decide(f.member,id(),next.id,true,"ACCEPT")}
    }
    @Test fun erasingANonOwnerRevokesTheirPendingOffersAndCancelsTheirIncomingTransfer():Unit=database().use {db ->
        val f=fixture(db);val t=CsrTeam(CsrRepository(db));t.join(f,"ADMIN")
        val offer=t.invite(f.member,f.company,id(),f.outsider+"@example.test","VIEWER")
        t.propose(f.owner,f.company,id(),f.member)
        IdentityStore(db).eraseAccount(f.member)
        assertTrue(t.inbox(f.outsider).isEmpty());assertTrue(t.desk(f.owner,f.company).transfers.isEmpty())
        assertEquals("OWNER",t.desk(f.owner,f.company).role)
        assertThrows(IllegalArgumentException::class.java){t.decide(f.outsider,id(),offer.id,false,"ACCEPT")}
    }
    @Test fun concurrentAcceptCancelAndOwnerErasureNeverLeaveAnOrphan():Unit=database().use {db ->
        repeat(3){
            val f=fixture(db);val t=CsrTeam(CsrRepository(db));t.join(f)
            val transfer=t.propose(f.owner,f.company,id(),f.member);val gate=CountDownLatch(1)
            val accept=CompletableFuture.supplyAsync{gate.await();runCatching{t.decide(f.member,id(),transfer.id,true,"ACCEPT")}.isSuccess}
            val erase=CompletableFuture.supplyAsync{gate.await();runCatching{crmModerateUsers(db,f.owner,listOf(f.owner),"DELETE","Solicitud de baja QA concurrente",selfDelete=true)}.isSuccess}
            gate.countDown();assertTrue(accept.get(10,TimeUnit.SECONDS));erase.get(10,TimeUnit.SECONDS)
            assertEquals("OWNER",t.desk(f.member,f.company).role)
            val another=t.invite(f.member,f.company,id(),f.outsider+"@example.test","VIEWER");val next=CountDownLatch(1)
            val attempts=listOf(CompletableFuture.supplyAsync{next.await();runCatching{t.decide(f.outsider,id(),another.id,false,"ACCEPT")}.isSuccess},CompletableFuture.supplyAsync{next.await();runCatching{t.decide(f.member,id(),another.id,false,"CANCEL")}.isSuccess})
            next.countDown();assertEquals(1,attempts.count{it.get(10,TimeUnit.SECONDS)})
        }
    }
}
