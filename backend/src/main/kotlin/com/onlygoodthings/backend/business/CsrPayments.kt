package com.onlygoodthings.backend.business

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*
import java.sql.Connection
import java.util.UUID

@Serializable
data class CsrPaymentOrder(val id:String,val amountCents:Long,val status:String,val creditedCents:Long,
    val liveMode:Boolean,val checkoutUrl:String?,val createdAtEpochMs:Long)

class CsrPayments internal constructor(private val repository:CsrRepository,private val provider:PaymentProvider) {
    fun checkout(actor:String,company:String,request:String,amount:Long):CsrActionResult {
        require(amount in 100_00..1_000_000_00L){"El monto debe ser entre $100 y $1.000.000 ARS"}
        val order=repository.operation(actor,company,request,"PAYMENT_ORDER",buildJsonObject{put("amount",amount);put("live",provider.live)},financeRoles){c,owner ->
            require(owner.verified){"La empresa debe estar verificada antes de cargar saldo"}
            val id=UUID.randomUUID().toString()
            c.prepareStatement("INSERT INTO csr_payment_orders(id,company_id,actor_user_id,amount_cents,live_mode) VALUES(?::uuid,?::uuid,?::uuid,?,?)").use{q ->
                q.setString(1,id);q.setString(2,company);q.setString(3,actor);q.setLong(4,amount);q.setBoolean(5,provider.live);q.executeUpdate()
            };CsrActionResult(id,"PENDING",amountCents=amount)
        }
        // The order survives provider outages. Retrying uses that order, never another deposit.
        return repository.db.withConnection{c ->
            c.autoCommit=false
            try {
                val prior=c.prepareStatement("SELECT checkout_url,provider_status FROM csr_payment_orders WHERE id=?::uuid FOR UPDATE").use{q ->q.setString(1,order.id);q.executeQuery().use{r ->check(r.next());r.getString(1) to r.getString(2)}}
                if(prior.second=="approved"){
                    c.commit();return@withConnection order.copy(status=prior.second,liveMode=provider.live)
                }
                val url=prior.first ?:provider.preference(order.id,amount).let{preference ->
                    c.prepareStatement("UPDATE csr_payment_orders SET preference_id=?,checkout_url=?,updated_at=now() WHERE id=?::uuid").use{q ->q.setString(1,preference.id);q.setString(2,preference.url);q.setString(3,order.id);q.executeUpdate()};preference.url
                }
                c.commit();order.copy(status=prior.second,checkoutUrl=url,liveMode=provider.live)
            }catch(error:Exception){c.rollback();throw error}finally{c.autoCommit=true}
        }
    }
    fun orders(actor:String,company:String):List<CsrPaymentOrder> = repository.paymentOrders(actor,company)
    fun enqueue(id:String) {
        require(id.matches(Regex("[0-9]{1,30}")))
        repository.db.withConnection{c ->c.prepareStatement("INSERT INTO csr_payment_jobs(payment_id) VALUES(?) ON CONFLICT(payment_id) DO UPDATE SET done=false,next_attempt_at=now(),updated_at=now()").use{q ->q.setString(1,id);q.executeUpdate()}}
    }
    /** Recover even if a notification never reached the server. Lookup IDs still
     * pass the full authoritative payment checks before any balance is changed. */
    fun discoverPending(limit:Int=2) {
        repeat(limit) {
            val found=repository.db.withConnection{c ->
                c.autoCommit=false
                try {
                    val order=c.prepareStatement("SELECT id::text FROM csr_payment_orders WHERE preference_id IS NOT NULL AND next_lookup_at<=now() ORDER BY next_lookup_at FOR UPDATE SKIP LOCKED LIMIT 1").use{q ->q.executeQuery().use{r ->if(r.next())r.getString(1) else null}}
                    if(order==null){c.commit();null} else {
                        val ids=runCatching{provider.paymentIds(order)}.getOrDefault(emptyList())
                        c.prepareStatement("UPDATE csr_payment_orders SET lookup_attempts=lookup_attempts+1,next_lookup_at=now()+make_interval(secs=>least(86400,60*(lookup_attempts+1))) WHERE id=?::uuid").use{q ->q.setString(1,order);q.executeUpdate()}
                        c.commit();ids
                    }
                }catch(error:Exception){c.rollback();throw error}finally{c.autoCommit=true}
            } ?:return
            // Release the order lock before acquiring queue locks: payment workers
            // take these locks in the opposite order. A crash is recovered by lookup.
            found.forEach{enqueue(it)}
        }
    }
    fun processPending(limit:Int=5) {
        repeat(limit) {
            val processed=repository.db.withConnection{c ->
                c.autoCommit=false
                try {
                    val id=c.prepareStatement("SELECT payment_id FROM csr_payment_jobs WHERE NOT done AND next_attempt_at<=now() ORDER BY next_attempt_at FOR UPDATE SKIP LOCKED LIMIT 1").use{q ->q.executeQuery().use{r ->if(r.next())r.getString(1) else null}}
                    if(id==null){c.commit();false} else {
                        // Serialize fetch + apply per payment; older notifications cannot overwrite a newer fetch.
                        val savepoint=c.setSavepoint()
                        try {
                            val payment=provider.payment(id)
                            apply(c,id,payment)
                            val terminal=payment.status in setOf("rejected","cancelled","refunded","charged_back")
                            c.prepareStatement("UPDATE csr_payment_jobs SET done=?,attempts=attempts+1,last_error_code=null,next_attempt_at=now()+interval '6 hours',updated_at=now() WHERE payment_id=?").use{q ->q.setBoolean(1,terminal);q.setString(2,id);q.executeUpdate()}
                        }catch(_:Exception){
                            c.rollback(savepoint)
                            c.prepareStatement("UPDATE csr_payment_jobs SET attempts=attempts+1,last_error_code='RETRY_REQUIRED',next_attempt_at=now()+make_interval(secs=>least(3600,30*(attempts+1))),updated_at=now() WHERE payment_id=?").use{q ->q.setString(1,id);q.executeUpdate()}
                        }
                        c.commit();true
                    }
                }catch(error:Exception){c.rollback();throw error}finally{c.autoCommit=true}
            }
            if(!processed)return
        }
    }
    private fun incident(c:Connection,id:String,order:String?,reason:String) {
        c.prepareStatement("INSERT INTO csr_payment_incidents(payment_id,order_id,reason) VALUES(?,?::uuid,?) ON CONFLICT(payment_id,reason) DO NOTHING").use{q ->q.setString(1,id);q.setString(2,order);q.setString(3,reason);q.executeUpdate()}
    }
    private fun apply(c:Connection,id:String,p:ProviderPayment) {
        if(p.id!=id || p.collector!=provider.collector || !provider.matchesEnvironment(p) || p.currency!="ARS" || runCatching{UUID.fromString(p.orderId)}.isFailure || p.refunded !in 0..p.amount){
            incident(c,id,null,"PROVIDER_MISMATCH");return
        }
        data class Order(val company:String,val amount:Long,val live:Boolean,val payment:String?,val credited:Long)
        val order=c.prepareStatement("SELECT company_id::text,amount_cents,live_mode,payment_id,credited_net_cents FROM csr_payment_orders WHERE id=?::uuid FOR UPDATE").use{q ->q.setString(1,p.orderId);q.executeQuery().use{r ->if(r.next())Order(r.getString(1),r.getLong(2),r.getBoolean(3),r.getString(4),r.getLong(5)) else null}}
        if(order==null){incident(c,id,null,"UNKNOWN_ORDER");return}
        if(order.amount!=p.amount || order.live!=provider.live){incident(c,id,p.orderId,"ORDER_MISMATCH");return}
        val net=if(p.status=="approved")p.amount-p.refunded else 0L
        if(order.payment!=null && order.payment!=id){
            if(net>0)incident(c,id,p.orderId,"EXTRA_PAYMENT");return
        }
        // Rejected attempts do not consume an order; the buyer can retry from the same checkout.
        if(net==0L && order.payment==null){
            c.prepareStatement("UPDATE csr_payment_orders SET provider_status=?,updated_at=now() WHERE id=?::uuid").use{q ->q.setString(1,p.status);q.setString(2,p.orderId);q.executeUpdate()};return
        }
        if(!provider.live && p.live && order.payment==null){
            // Legacy Checkout Pro can report live_mode=true for a transfer
            // between generated test accounts. The provider has independently
            // verified its collector's test_user tag; the payer must also be
            // explicitly allowed from the operator's test-account inventory.
            // Keep the raw provider flag in append-only audit, never normalize
            // it silently or enable this exception for production collectors.
            c.prepareStatement("""INSERT INTO crm_audit_events(company_id,action,target_type,target_id,reason,outcome,metadata)
                VALUES(?::uuid,'PAYMENT_SANDBOX_CONFIRMED','payment',?,'Cuenta receptora test_user y comprador de prueba configurado; se conserva live_mode del proveedor','SUCCESS',?::jsonb)""").use{q ->
                q.setString(1,order.company);q.setString(2,id);q.setString(3,buildJsonObject{put("providerLiveMode",p.live);put("collectorId",p.collector);put("configuredTestBuyerId",p.payer)}.toString());q.executeUpdate()
            }
        }
        val balance=c.prepareStatement("SELECT campaign_balance_cents,payment_debt_cents FROM companies WHERE id=?::uuid FOR UPDATE").use{q ->q.setString(1,order.company);q.executeQuery().use{r ->check(r.next());r.getLong(1) to r.getLong(2)}}
        val delta=net-order.credited
        if(delta!=0L) {
            val debtDelta:Long;val availableDelta:Long
            if(delta>0){val settle=minOf(balance.second,delta);debtDelta=-settle;availableDelta=delta-settle}
            else{val deduct=minOf(balance.first,-delta);availableDelta=-deduct;debtDelta=-delta-deduct}
            c.prepareStatement("UPDATE companies SET campaign_balance_cents=campaign_balance_cents+?,payment_debt_cents=payment_debt_cents+? WHERE id=?::uuid").use{q ->q.setLong(1,availableDelta);q.setLong(2,debtDelta);q.setString(3,order.company);q.executeUpdate()}
            repository.ledger(c,order.company,null,null,null,if(delta>0)"PAYMENT_CREDIT" else "PAYMENT_REVERSAL",available=availableDelta,debt=debtDelta,
                source="payment:$id:${UUID.randomUUID()}",note=if(delta>0)"Pago confirmado por Mercado Pago" else "Devolución o contracargo confirmado por Mercado Pago")
        }
        c.prepareStatement("UPDATE csr_payment_orders SET payment_id=?,provider_status=?,credited_net_cents=?,updated_at=now() WHERE id=?::uuid").use{q ->q.setString(1,id);q.setString(2,p.status);q.setLong(3,net);q.setString(4,p.orderId);q.executeUpdate()}
        // A previously rejected environment signal is resolved only after the
        // same authoritative payment passed every identity/order/amount check.
        c.prepareStatement("UPDATE csr_payment_incidents SET order_id=?::uuid,resolved_at=now() WHERE payment_id=? AND reason='PROVIDER_MISMATCH' AND resolved_at IS NULL").use{q ->q.setString(1,p.orderId);q.setString(2,id);q.executeUpdate()}
    }
}


fun CsrRepository.paymentOrders(actor:String,company:String):List<CsrPaymentOrder> = db.withConnection{c ->
        access(c,actor,company,financeRoles+setOf("ANALYST"))
        val canPay=try {access(c,actor,company,financeRoles);true} catch(_:BusinessAccessDenied){false}
        c.prepareStatement("SELECT id::text,amount_cents,provider_status,credited_net_cents,live_mode,checkout_url,(extract(epoch FROM created_at)*1000)::bigint FROM csr_payment_orders WHERE company_id=?::uuid ORDER BY created_at DESC LIMIT 100").use{q ->
            q.setString(1,company);q.executeQuery().use{r ->buildList{while(r.next())add(CsrPaymentOrder(r.getString(1),r.getLong(2),r.getString(3),r.getLong(4),r.getBoolean(5),r.getString(6).takeIf {canPay && r.getString(3) in setOf("PENDING","pending","in_process","authorized","rejected")},r.getLong(7)))}}
        }
    }
