package com.onlygoodthings.backend.business

import com.onlygoodthings.backend.infra.Database
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Assertions.*
import java.util.UUID
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@Tag("business-integration")
class CsrIntegrationTest {
    private fun id()=UUID.randomUUID().toString()
    private fun database():Database {
        require(System.getenv("OGT_BUSINESS_TEST_JDBC").orEmpty().matches(Regex("jdbc:postgresql://127\\.0\\.0\\.1:19432/ogt_business_qa_[a-z0-9]+"))){"Only a dedicated isolated QA database is allowed"}
        return Database(System.getenv("OGT_BUSINESS_TEST_JDBC"),"ogt","ogt_qa_local_only")
    }
    private data class Fixture(val actor:String,val reader:String,val outsider:String,val company:String,val other:String)
    private fun fixture(db:Database,balance:Long=50000):Fixture {
        val users=List(3){id()};val companies=List(2){id()}
        db.withConnection{c ->
            users.forEach{user ->c.prepareStatement("INSERT INTO users(id,firebase_uid,email,display_name,invite_code) VALUES(?::uuid,?,?,'Persona de prueba',?)").use{q ->q.setString(1,user);q.setString(2,"business-qa-$user");q.setString(3,"$user@example.test");q.setString(4,user);q.executeUpdate()}}
            companies.forEach{company ->c.prepareStatement("INSERT INTO companies(id,legal_name,verification_status,campaign_balance_cents) VALUES(?::uuid,'Empresa de prueba','VERIFIED',?)").use{q ->q.setString(1,company);q.setLong(2,balance);q.executeUpdate()}
                c.prepareStatement("INSERT INTO csr_ledger(company_id,kind,available_delta_cents,source_key,note) VALUES(?::uuid,'OPENING',?,'qa-opening','Synthetic QA fixture')").use{q ->q.setString(1,company);q.setLong(2,balance);q.executeUpdate()}}
            listOf(Triple(companies[0],users[0],"OWNER"),Triple(companies[0],users[1],"VIEWER"),Triple(companies[1],users[2],"OWNER")).forEach{(company,user,role) ->
                c.prepareStatement("INSERT INTO crm_company_members(company_id,user_id,member_role,status,accepted_at) VALUES(?::uuid,?::uuid,?,'ACTIVE',now())").use{q ->q.setString(1,company);q.setString(2,user);q.setString(3,role);q.executeUpdate()}
            }
        };return Fixture(users[0],users[1],users[2],companies[0],companies[1])
    }
    private fun CsrRepository.create(f:Fixture,budget:Long=50000)=saveCampaign(f.actor,f.company,id(),null,"Campaña de prueba","Descripción coherente de campaña",1,budget).id
    @Test fun tenantsRolesRetriesAndConcurrentReservationsAreAtomic():Unit=database().use{db ->
        val f=fixture(db);val r=CsrRepository(db);val campaign=r.create(f)
        assertThrows(BusinessAccessDenied::class.java){r.campaigns(f.outsider,f.company)}
        assertEquals(1,r.campaigns(f.reader,f.company).size)
        assertThrows(BusinessAccessDenied::class.java){r.reserve(f.reader,f.company,id(),campaign,100)}
        assertThrows(IllegalArgumentException::class.java){r.reserve(f.actor,f.company,id(),r.saveCampaign(f.outsider,f.other,id(),null,"Otra campaña","Otra descripción coherente",0,50000).id,100)}
        val request=id();val first=r.reserve(f.actor,f.company,request,campaign,10000)
        assertEquals(first,r.reserve(f.actor,f.company,request,campaign,10000))
        assertThrows(IllegalArgumentException::class.java){r.reserve(f.actor,f.company,request,campaign,10001)}
        val gate=CountDownLatch(1)
        val attempts=List(2){CompletableFuture.supplyAsync{gate.await();runCatching{r.reserve(f.actor,f.company,id(),campaign,30000)}.isSuccess}}
        gate.countDown();assertEquals(1,attempts.count{it.get(5,TimeUnit.SECONDS)})
        val summary=r.finance(f.actor,f.company);assertEquals(10000,summary.availableCents);assertEquals(40000,summary.reservedCents);assertTrue(summary.reconciled)
        assertEquals(50000,r.finance(f.outsider,f.other).availableCents)
        r.transition(f.actor,f.company,id(),campaign,"CANCELLED")
        assertEquals(50000,r.finance(f.actor,f.company).availableCents);assertEquals(0,r.finance(f.actor,f.company).reservedCents)
        assertThrows(IllegalArgumentException::class.java){r.transition(f.actor,f.company,id(),campaign,"ACTIVE")}
    }
    @Test fun verifiedOutcomesAndCouponsHaveOneWayAuditableLifecycles():Unit=database().use{db ->
        val f=fixture(db);val r=CsrRepository(db);val campaign=r.create(f)
        r.reserve(f.actor,f.company,id(),campaign,20000);r.transition(f.actor,f.company,id(),campaign,"ACTIVE")
        assertThrows(IllegalArgumentException::class.java){r.issueCoupon(f.actor,f.company,id(),campaign,1000,null,"Meta pendiente")}
        assertThrows(IllegalArgumentException::class.java){r.fundOutcome(f.actor,f.company,id(),campaign,id(),1000)}
        val outcome=id()
        db.withConnection{c ->c.prepareStatement("INSERT INTO action_outcomes(id,kind,verification_method,actor_user_id,company_id,source_table,source_id,points_awarded,verified) VALUES(?::uuid,'TIMEBANK_CLOSED','ORGANIZER',?::uuid,?::uuid,'qa_fixture',?::uuid,0,true)").use{q ->q.setString(1,outcome);q.setString(2,f.actor);q.setString(3,f.company);q.setString(4,id());q.executeUpdate()}}
        val request=id();r.fundOutcome(f.actor,f.company,request,campaign,outcome,10000);r.fundOutcome(f.actor,f.company,request,campaign,outcome,10000)
        assertThrows(IllegalArgumentException::class.java){r.fundOutcome(f.actor,f.company,id(),campaign,outcome,10000)}
        assertEquals(1,r.campaigns(f.actor,f.company).first().socialProgress)
        val coupon=r.issueCoupon(f.actor,f.company,id(),campaign,1250,null,"Gracias por ayudar")
        assertThrows(BusinessAccessDenied::class.java){r.companyCoupons(f.reader,f.company)}
        val gate=CountDownLatch(1);val claims=listOf(f.reader,f.outsider).map{user ->CompletableFuture.supplyAsync{gate.await();user to runCatching{r.claimCoupon(user,coupon.code!!)}.isSuccess}}
        gate.countDown();val results=claims.map{it.get(5,TimeUnit.SECONDS)};assertEquals(1,results.count{it.second})
        val winner=results.single{it.second}.first;assertEquals(coupon.id,r.ownCoupons(winner).single().id)
        assertThrows(BusinessAccessDenied::class.java){r.changeCoupon(f.outsider,f.company,id(),coupon.code!!,true)}
        val redeem=id();r.changeCoupon(f.actor,f.company,redeem,coupon.code!!,true);r.changeCoupon(f.actor,f.company,redeem,coupon.code,true)
        assertThrows(IllegalArgumentException::class.java){r.changeCoupon(f.actor,f.company,id(),coupon.code,true)}
        assertEquals("REDEEMED",r.ownCoupons(winner).single().status)
        val revoked=r.issueCoupon(f.actor,f.company,id(),campaign,1000,f.reader,"")
        r.changeCoupon(f.actor,f.company,id(),revoked.code!!,false)
        assertThrows(IllegalArgumentException::class.java){r.claimCoupon(f.reader,revoked.code)}
        r.transition(f.actor,f.company,id(),campaign,"COMPLETED")
        val summary=r.finance(f.actor,f.company);assertEquals(40000,summary.availableCents);assertEquals(10000,summary.spentCents);assertTrue(summary.reconciled)
        db.withConnection{c ->c.prepareStatement("SELECT community_points FROM users WHERE id=?::uuid").use{q ->q.setString(1,f.actor);q.executeQuery().use{it.next();assertEquals(0,it.getInt(1))}}}
    }
    private class FakeProvider:PaymentProvider {
        override val live=false;override val collector="12345"
        var reads=0;var failure=false;lateinit var current:ProviderPayment
        override fun preference(order:String,amount:Long)=PaymentPreference("qa-$order","https://sandbox.mercadopago.com.ar/checkout/v1/redirect?pref_id=qa-$order")
        override fun payment(id:String):ProviderPayment {reads++;if(failure)throw PaymentProviderFailure();return current}
        override fun paymentIds(order:String)=if(this::current.isInitialized && current.orderId==order)listOf(current.id) else emptyList()
    }
    @Test fun paymentsReconcileRetriesRefundsAndDebtWithoutDoubleCredit():Unit=database().use{db ->
        val f=fixture(db,0);val r=CsrRepository(db);val provider=FakeProvider();val p=CsrPayments(r,provider)
        val request=id();val order=p.checkout(f.actor,f.company,request,20000)
        assertEquals(order,p.checkout(f.actor,f.company,request,20000));assertEquals(0,r.finance(f.actor,f.company).availableCents)
        assertEquals(order.id,r.paymentOrders(f.actor,f.company).single().id)
        assertThrows(BusinessAccessDenied::class.java){r.paymentOrders(f.reader,f.company)}
        assertThrows(BusinessAccessDenied::class.java){r.paymentOrders(f.outsider,f.company)}
        val payment=System.nanoTime().toString();provider.current=ProviderPayment(payment,order.id,provider.collector,false,"ARS",20000,0,"approved")
        provider.failure=true;p.enqueue(payment);p.processPending();assertEquals(0,r.finance(f.actor,f.company).availableCents)
        provider.failure=false;p.enqueue(payment);p.processPending();p.enqueue(payment);p.processPending()
        assertEquals(20000,r.finance(f.actor,f.company).availableCents);assertEquals(1,r.finance(f.actor,f.company).entries.count{it.kind=="PAYMENT_CREDIT"})
        assertNull(p.checkout(f.actor,f.company,request,20000).checkoutUrl)
        assertNull(r.paymentOrders(f.actor,f.company).single().checkoutUrl)
        val campaign=r.create(f,20000);r.reserve(f.actor,f.company,id(),campaign,18000)
        provider.current=provider.current.copy(refunded=5000);p.enqueue(payment);p.processPending()
        assertEquals(3000,r.finance(f.actor,f.company).debtCents);assertTrue(r.finance(f.actor,f.company).reconciled)
        provider.current=provider.current.copy(status="charged_back");p.enqueue(payment);p.processPending()
        assertEquals(0,r.finance(f.actor,f.company).availableCents);assertEquals(18000,r.finance(f.actor,f.company).debtCents);assertTrue(r.finance(f.actor,f.company).reconciled)
        assertThrows(IllegalArgumentException::class.java){r.reserve(f.actor,f.company,id(),campaign,100)}
        r.reserve(f.actor,f.company,id(),campaign,18000,true)
        assertEquals(0,r.finance(f.actor,f.company).debtCents);assertTrue(r.finance(f.actor,f.company).reconciled)
        val extra=payment+"1";provider.current=provider.current.copy(id=extra,status="approved");p.enqueue(extra);p.processPending()
        assertEquals(0,r.finance(f.actor,f.company).availableCents)
        db.withConnection{c ->c.prepareStatement("SELECT reason FROM csr_payment_incidents WHERE payment_id=?").use{q ->q.setString(1,extra);q.executeQuery().use{assertTrue(it.next());assertEquals("EXTRA_PAYMENT",it.getString(1))}}}
        val other=p.checkout(f.actor,f.company,id(),10000);val bad=payment+"2"
        provider.current=ProviderPayment(bad,other.id,"OTHER",true,"USD",10000,0,"approved");p.enqueue(bad);p.processPending()
        assertEquals(0,r.finance(f.actor,f.company).availableCents);assertEquals(0,r.finance(f.actor,f.company).spentCents)
    }
    @Test fun reconciliationFindsAPaymentWhenItsNotificationWasLost():Unit=database().use{db ->
        val f=fixture(db,0);val r=CsrRepository(db);val provider=FakeProvider();val p=CsrPayments(r,provider)
        val order=p.checkout(f.actor,f.company,id(),10000);val payment=System.nanoTime().toString()
        provider.current=ProviderPayment(payment,order.id,provider.collector,false,"ARS",10000,0,"approved")
        p.discoverPending(100);p.processPending(100)
        assertEquals(10000,r.finance(f.actor,f.company).availableCents);assertTrue(r.finance(f.actor,f.company).reconciled)
    }
    @Test fun replayingMigrationNeverImportsNewBalancesAsOpeningMoney():Unit=database().use{db ->
        val f=fixture(db,15000);val r=CsrRepository(db)
        fun count()=db.withConnection{c ->c.prepareStatement("SELECT count(*) FROM csr_ledger WHERE company_id=?::uuid").use{q ->q.setString(1,f.company);q.executeQuery().use{it.next();it.getInt(1)}}}
        val before=count()
        db.withConnection{c ->c.createStatement().use{it.execute(java.nio.file.Files.readString(java.nio.file.Path.of("../database/migrations/33_csr_operations.sql")))}}
        assertEquals(before,count());assertTrue(r.finance(f.actor,f.company).reconciled)
    }
    @Test fun accountDeletionRevokesOwnedCouponsAndAnonymizesProtectedFinancialAudit():Unit=database().use{db ->
        val f=fixture(db);val r=CsrRepository(db)
        val campaign=r.saveCampaign(f.actor,f.company,id(),null,"Gracias al barrio","Descuento por una meta sin acciones pendientes",0,0).id
        r.transition(f.actor,f.company,id(),campaign,"ACTIVE")
        val coupon=r.issueCoupon(f.actor,f.company,id(),campaign,1000,f.reader,"")
        val source="qa-personal-audit:"+id()
        db.withConnection{c ->r.ledger(c,f.company,null,null,f.reader,"QA_AUDIT",source=source,note="Synthetic audit entry")}
        assertThrows(java.sql.SQLException::class.java){db.withConnection{c ->c.prepareStatement("DELETE FROM csr_ledger WHERE company_id=?::uuid AND source_key=?").use{q ->q.setString(1,f.company);q.setString(2,source);q.executeUpdate()}}}
        com.onlygoodthings.backend.crm.crmModerateUsers(db,f.reader,listOf(f.reader),"DELETE","Solicitud de baja de prueba",selfDelete=true)
        db.withConnection{c ->
            c.prepareStatement("SELECT status::text,issued_to_user FROM promo_codes WHERE id=?::uuid").use{q ->q.setString(1,coupon.id);q.executeQuery().use{it.next();assertEquals("REVOKED",it.getString(1));assertNull(it.getString(2))}}
            c.prepareStatement("SELECT actor_user_id FROM csr_ledger WHERE company_id=?::uuid AND source_key=?").use{q ->q.setString(1,f.company);q.setString(2,source);q.executeQuery().use{assertTrue(it.next());assertNull(it.getString(1))}}
        }
        assertTrue(r.finance(f.actor,f.company).reconciled)
    }
    @Test fun companyApplicationGrantsNothingUntilAnIndependentAuditedReview():Unit=database().use{db ->
        val f=fixture(db);val registrations=CsrRegistrations(db);val r=CsrRepository(db)
        db.withConnection{c ->c.prepareStatement("INSERT INTO crm_platform_staff(user_id,staff_role,enabled,approved_by,approved_at) VALUES(?::uuid,'OWNER',true,?::uuid,now())").use{q ->q.setString(1,f.actor);q.setString(2,f.actor);q.executeUpdate()}}
        val application=id();val tax=id().replace("-","").take(20)
        val first=registrations.submit(f.reader,application,"Empresa vecinal QA","Comunidad QA",tax,"https://example.test","Queremos apoyar acciones verificadas de la comunidad.")
        assertEquals(first,registrations.submit(f.reader,application,"Empresa vecinal QA","Comunidad QA",tax,"https://example.test","Queremos apoyar acciones verificadas de la comunidad."))
        assertEquals("PENDING_REVIEW",first.status)
        assertThrows(IllegalArgumentException::class.java){registrations.submit(f.reader,application,"Otra razón social","",tax,"","Queremos apoyar a la comunidad.")}
        assertThrows(BusinessAccessDenied::class.java){registrations.pending(f.reader)}
        assertThrows(BusinessAccessDenied::class.java){registrations.review(f.outsider,id(),application,true,"La documentación fue contrastada en QA.")}
        assertTrue(registrations.own(f.outsider).isEmpty());assertEquals(application,registrations.own(f.reader).single().id)
        val rejected=id();registrations.review(f.actor,rejected,application,false,"La solicitud de ensayo necesita una corrección.")
        assertEquals("REJECTED",registrations.own(f.reader).first().status)
        val second=id();registrations.submit(f.reader,second,"Empresa vecinal QA","Comunidad QA",tax,"","Queremos apoyar acciones verificadas de la comunidad.")
        val request=id();val gate=CountDownLatch(1)
        val reviews=List(2){CompletableFuture.supplyAsync{gate.await();registrations.review(f.actor,request,second,true,"Documentación y representación verificadas para este ensayo.")}}
        gate.countDown();val company=reviews.first().get(5,TimeUnit.SECONDS).id
        assertEquals(company,reviews.last().get(5,TimeUnit.SECONDS).id)
        assertTrue(r.campaigns(f.reader,company).isEmpty());assertTrue(r.finance(f.reader,company).reconciled)
        assertThrows(BusinessAccessDenied::class.java){r.finance(f.outsider,company)}
        db.withConnection{c ->c.prepareStatement("SELECT count(*) FROM crm_audit_events WHERE action='COMPANY_APPLICATION_APPROVED' AND target_id=?").use{q ->q.setString(1,second);q.executeQuery().use{it.next();assertEquals(1,it.getInt(1))}}}
        val duplicate=id();registrations.submit(f.outsider,duplicate,"Empresa con identificación existente","",tax,"","Solicitamos una revisión de la empresa de prueba.")
        assertThrows(IllegalArgumentException::class.java){registrations.review(f.actor,id(),duplicate,true,"La duplicación no debe asignar acceso a una empresa ajena.")}
        val own=id();registrations.submit(f.actor,own,"Solicitud del revisor","",id().replace("-","").take(20),"","Esta solicitud no puede aprobarla su propio titular.")
        assertThrows(IllegalArgumentException::class.java){registrations.review(f.actor,id(),own,true,"Nadie puede aprobar su propia solicitud empresarial.")}
    }
    @Test fun pendingCompanyRequestsCanBeWithdrawnExportedAndErasedByTheirOwner():Unit=database().use{db ->
        val f=fixture(db);val registrations=CsrRegistrations(db)
        fun submit()=registrations.submit(f.reader,id(),"Empresa que solicita el alta","",id().replace("-","").take(20),"","Nos gustaría colaborar con proyectos de la comunidad.")
        val first=submit();assertEquals("WITHDRAWN",registrations.withdraw(f.reader,first.id).status)
        assertEquals("WITHDRAWN",registrations.withdraw(f.reader,first.id).status)
        assertThrows(IllegalArgumentException::class.java){registrations.withdraw(f.outsider,first.id)}
        submit()
        val exported=com.onlygoodthings.backend.infra.IdentityStore(db).exportAccount(f.reader)!!
        assertEquals(2,exported.companyApplications.size);assertTrue(exported.companyApplications.all{it.applicantUserId==f.reader})
        com.onlygoodthings.backend.crm.crmModerateUsers(db,f.reader,listOf(f.reader),"DELETE","Solicitud personal de baja de prueba",selfDelete=true)
        db.withConnection{c ->c.prepareStatement("SELECT count(*) FROM csr_company_registrations WHERE applicant_user_id=?::uuid").use{q ->q.setString(1,f.reader);q.executeQuery().use{it.next();assertEquals(0,it.getInt(1))}}}
    }
}
