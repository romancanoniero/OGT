package com.onlygoodthings.backend.business

import com.onlygoodthings.backend.infra.Database
import kotlinx.serialization.json.*
import java.sql.Connection
import java.util.UUID
import java.security.MessageDigest

internal val campaignRoles=setOf("OWNER","ADMIN","CAMPAIGN_MANAGER")
internal val financeRoles=setOf("OWNER","ADMIN","FINANCE")
internal val businessReadRoles=campaignRoles+financeRoles+setOf("ANALYST","VIEWER")
class BusinessAccessDenied:RuntimeException("No tenés permiso para operar esta empresa")

class CsrRepository(internal val db:Database) {
    internal data class Company(val id:String,val verified:Boolean,val available:Long,val debt:Long)
    internal fun access(c:Connection,actor:String,company:String,roles:Set<String>,lock:Boolean=false):Company {
        UUID.fromString(company)
        // Lock first, then read permissions in a fresh statement. A waiting writer
        // must not keep the grant snapshot taken before a concurrent revocation.
        if(lock)c.prepareStatement("SELECT id FROM companies WHERE id=?::uuid FOR UPDATE").use { q ->
            q.setString(1,company);q.executeQuery().use {if(!it.next())throw BusinessAccessDenied()}
        }
        return c.prepareStatement("""SELECT c.id::text,c.verification_status::text,c.campaign_balance_cents,c.payment_debt_cents
            FROM companies c JOIN crm_company_access a ON a.company_id=c.id
            JOIN users u ON u.id=a.user_id
            WHERE c.id=?::uuid AND a.user_id=?::uuid AND u.status='ACTIVE' AND a.member_role=ANY(?)""").use { q ->
            q.setString(1,company);q.setString(2,actor);q.setArray(3,c.createArrayOf("text",roles.toTypedArray()))
            q.executeQuery().use { r ->if(!r.next())throw BusinessAccessDenied()
                Company(r.getString(1),r.getString(2)=="VERIFIED",r.getLong(3),r.getLong(4)) }
        }
    }

    internal fun operation(actor:String,company:String,requestId:String,kind:String,parameters:JsonObject,roles:Set<String>,body:(Connection,Company)->CsrActionResult):CsrActionResult {
        UUID.fromString(requestId)
        val digest=MessageDigest.getInstance("SHA-256").digest((kind+Json.encodeToString(JsonObject.serializer(),parameters)).toByteArray()).joinToString(""){"%02x".format(it)}
        return db.withConnection { c ->
            c.autoCommit=false
            try {
                c.prepareStatement("SELECT pg_advisory_xact_lock(hashtextextended(?,0))").use { q ->q.setString(1,"csr:$requestId");q.execute() }
                val owner=access(c,actor,company,roles,true)
                val prior=c.prepareStatement("SELECT company_id::text,actor_user_id::text,kind,payload_hash,result_json::text FROM csr_operations WHERE id=?::uuid").use { q ->
                    q.setString(1,requestId);q.executeQuery().use { r ->if(r.next()) {
                        require(r.getString(1)==UUID.fromString(company).toString() && r.getString(2)==UUID.fromString(actor).toString() && r.getString(3)==kind && r.getString(4)==digest) { "Este identificador de operación ya fue usado con otros datos" }
                        Json.decodeFromString(CsrActionResult.serializer(),r.getString(5))
                    } else null }
                }
                if(prior!=null){c.commit();prior} else {
                    c.prepareStatement("INSERT INTO csr_operations(id,company_id,actor_user_id,kind,payload_hash) VALUES(?::uuid,?::uuid,?::uuid,?,?)").use { q ->
                        q.setString(1,requestId);q.setString(2,company);q.setString(3,actor);q.setString(4,kind);q.setString(5,digest);q.executeUpdate()
                    }
                    val result=body(c,owner)
                    c.prepareStatement("UPDATE csr_operations SET result_json=?::jsonb WHERE id=?::uuid").use { q ->q.setString(1,Json.encodeToString(CsrActionResult.serializer(),result));q.setString(2,requestId);q.executeUpdate() }
                    c.prepareStatement("INSERT INTO crm_audit_events(actor_user_id,company_id,action,target_type,target_id,reason,request_id,outcome) VALUES(?::uuid,?::uuid,?,'company',?,?,?,'SUCCESS')").use { q ->
                        q.setString(1,actor);q.setString(2,company);q.setString(3,"CSR_$kind");q.setString(4,company);q.setString(5,"Operación empresarial confirmada: $kind");q.setString(6,requestId);q.executeUpdate()
                    }
                    c.commit();result
                }
            } catch(error:Exception){c.rollback();throw error} finally {c.autoCommit=true}
        }
    }
    internal fun ledger(c:Connection,company:String,campaign:String?,operation:String?,actor:String?,kind:String,available:Long=0,reserved:Long=0,spent:Long=0,debt:Long=0,source:String,note:String) {
        c.prepareStatement("""INSERT INTO csr_ledger(company_id,campaign_id,operation_id,actor_user_id,kind,
            available_delta_cents,reserved_delta_cents,spent_delta_cents,debt_delta_cents,source_key,note)
            VALUES(?::uuid,?::uuid,?::uuid,?::uuid,?,?,?,?,?,?,?)""").use { q ->
            q.setString(1,company);q.setString(2,campaign);q.setString(3,operation);q.setString(4,actor);q.setString(5,kind)
            q.setLong(6,available);q.setLong(7,reserved);q.setLong(8,spent);q.setLong(9,debt);q.setString(10,source);q.setString(11,note);q.executeUpdate()
        }
    }
    private val fields="id::text,company_id::text,title,description,status::text,social_goal,social_progress,budget_cents,reserved_cents,spent_cents"
    private fun java.sql.ResultSet.campaign()=CsrCampaign(getString(1),getString(2),getString(3),getString(4),getString(5),getInt(6),getInt(7),getLong(8),getLong(9),getLong(10))
    internal fun campaign(c:Connection,company:String,id:String):CsrCampaign = c.prepareStatement("SELECT $fields FROM campaigns WHERE company_id=?::uuid AND id=?::uuid FOR UPDATE").use { q ->
        UUID.fromString(id);q.setString(1,company);q.setString(2,id);q.executeQuery().use { r ->require(r.next()){ "Campaña no disponible para esta empresa" };r.campaign() }
    }
    fun campaigns(actor:String,company:String):List<CsrCampaign> = db.withConnection { c ->
        access(c,actor,company,businessReadRoles)
        c.prepareStatement("SELECT $fields FROM campaigns WHERE company_id=?::uuid ORDER BY created_at DESC LIMIT 200").use { q ->q.setString(1,company);q.executeQuery().use { r ->buildList { while(r.next())add(r.campaign()) } } }
    }
    fun saveCampaign(actor:String,company:String,request:String,id:String?,title:String,description:String,goal:Int,budget:Long):CsrActionResult {
        val name=title.trim();val text=description.trim()
        require(name.length in 3..120 && text.length in 10..4000 && goal in 0..1_000_000 && budget in 0..100_000_000_00L) { "Revisá el título, descripción, meta y presupuesto" }
        val parameters=buildJsonObject {put("id",id);put("title",name);put("description",text);put("goal",goal);put("budget",budget)}
        return operation(actor,company,request,if(id==null)"CAMPAIGN_CREATE" else "CAMPAIGN_UPDATE",parameters,campaignRoles) { c,_ ->
            val key=id ?: UUID.randomUUID().toString()
            if(id==null)c.prepareStatement("INSERT INTO campaigns(id,company_id,title,description,social_goal,budget_cents) VALUES(?::uuid,?::uuid,?,?,?,?)").use { q ->
                q.setString(1,key);q.setString(2,company);q.setString(3,name);q.setString(4,text);q.setInt(5,goal);q.setLong(6,budget);q.executeUpdate()
            } else {
                val old=campaign(c,company,id);require(old.status=="DRAFT"){ "Solo se edita una campaña en borrador" }
                require(budget>=old.reservedCents+old.spentCents){ "El presupuesto no puede ser menor que lo comprometido" }
                c.prepareStatement("UPDATE campaigns SET title=?,description=?,social_goal=?,budget_cents=?,updated_at=now() WHERE id=?::uuid").use { q ->
                    q.setString(1,name);q.setString(2,text);q.setInt(3,goal);q.setLong(4,budget);q.setString(5,id);q.executeUpdate()
                }
            }
            CsrActionResult(key,"DRAFT")
        }
    }
    fun transition(actor:String,company:String,request:String,id:String,state:String):CsrActionResult = operation(actor,company,request,"CAMPAIGN_STATE",buildJsonObject {put("id",id);put("state",state)},campaignRoles) { c,owner ->
        val old=campaign(c,company,id);validateCampaignTransition(old.status,state)
        if(state=="ACTIVE")require(owner.verified && owner.debt==0L){ "La empresa debe estar verificada y sin saldo pendiente" }
        if(state=="COMPLETED")require(old.socialProgress>=old.socialGoal){ "La meta de la campaña todavía no está verificada" }
        val closing=state in setOf("COMPLETED","CANCELLED")
        if(closing && old.reservedCents>0) {
            val settle=minOf(owner.debt,old.reservedCents)
            c.prepareStatement("UPDATE companies SET campaign_balance_cents=campaign_balance_cents+?,payment_debt_cents=payment_debt_cents-? WHERE id=?::uuid").use { q ->q.setLong(1,old.reservedCents-settle);q.setLong(2,settle);q.setString(3,company);q.executeUpdate() }
            ledger(c,company,id,request,actor,"RELEASE",available=old.reservedCents-settle,reserved=-old.reservedCents,debt=-settle,source="state-release:$request",note="Devolución del presupuesto no utilizado al cerrar campaña")
        }
        c.prepareStatement("UPDATE campaigns SET status=?::campaign_status,reserved_cents=CASE WHEN ? THEN 0 ELSE reserved_cents END,updated_at=now() WHERE id=?::uuid").use { q ->q.setString(1,state);q.setBoolean(2,closing);q.setString(3,id);q.executeUpdate() }
        if(state=="CANCELLED")c.prepareStatement("UPDATE promo_codes SET status='REVOKED' WHERE campaign_id=?::uuid AND status='ISSUED'").use { q ->q.setString(1,id);q.executeUpdate() }
        CsrActionResult(id,state)
    }
    fun reserve(actor:String,company:String,request:String,id:String,amount:Long,release:Boolean=false):CsrActionResult {
        require(amount in 1..100_000_000_00L){ "Monto fuera de rango" }
        return operation(actor,company,request,if(release)"RELEASE" else "RESERVE",buildJsonObject {put("id",id);put("amount",amount)},financeRoles) { c,owner ->
            val row=campaign(c,company,id)
            require(row.status in setOf("DRAFT","ACTIVE","PAUSED")){ "La campaña está cerrada" }
            if(release)require(row.reservedCents>=amount){ "No hay presupuesto reservado suficiente" }
            else {
                require(owner.verified && owner.debt==0L){ "La empresa debe estar verificada y sin saldo pendiente" }
                require(owner.available>=amount){ "No hay saldo disponible suficiente" }
                require(row.spentCents<=row.budgetCents && row.reservedCents<=row.budgetCents-row.spentCents && amount<=row.budgetCents-row.spentCents-row.reservedCents){ "Se supera el presupuesto de la campaña" }
            }
            val settle=if(release)minOf(owner.debt,amount) else 0L
            val available=if(release)amount-settle else -amount
            val reserved=if(release)-amount else amount
            c.prepareStatement("UPDATE companies SET campaign_balance_cents=campaign_balance_cents+?,payment_debt_cents=payment_debt_cents-? WHERE id=?::uuid").use { q ->q.setLong(1,available);q.setLong(2,settle);q.setString(3,company);q.executeUpdate() }
            c.prepareStatement("UPDATE campaigns SET reserved_cents=reserved_cents+?,updated_at=now() WHERE id=?::uuid").use { q ->q.setLong(1,reserved);q.setString(2,id);q.executeUpdate() }
            ledger(c,company,id,request,actor,if(release)"RELEASE" else "RESERVE",available=available,reserved=reserved,debt=-settle,source="reserve:$request",note=if(release)"Devolución de presupuesto reservado" else "Presupuesto reservado; todavía no es un gasto")
            CsrActionResult(id,amountCents=amount)
        }
    }
    fun fundOutcome(actor:String,company:String,request:String,id:String,outcome:String,amount:Long):CsrActionResult {
        require(amount in 1..100_000_000_00L);UUID.fromString(outcome)
        return operation(actor,company,request,"FUND_OUTCOME",buildJsonObject {put("id",id);put("outcome",outcome);put("amount",amount)},financeRoles) { c,owner ->
            require(owner.verified && owner.debt==0L){ "La empresa no puede financiar nuevas acciones" }
            val row=campaign(c,company,id);require(row.status=="ACTIVE" && row.reservedCents>=amount){ "Campaña inactiva o presupuesto reservado insuficiente" }
            c.prepareStatement("SELECT verified FROM action_outcomes WHERE id=?::uuid AND company_id=?::uuid FOR UPDATE").use { q ->
                q.setString(1,outcome);q.setString(2,company);q.executeQuery().use { r ->require(r.next() && r.getBoolean(1)){ "La acción debe estar verificada y pertenecer a esta empresa" } }
            }
            val exists=c.prepareStatement("SELECT 1 FROM csr_action_funding WHERE outcome_id=?::uuid").use { q ->q.setString(1,outcome);q.executeQuery().use { it.next() } }
            require(!exists){ "La acción ya fue financiada" }
            c.prepareStatement("INSERT INTO csr_action_funding(outcome_id,company_id,campaign_id,operation_id,amount_cents) VALUES(?::uuid,?::uuid,?::uuid,?::uuid,?)").use { q ->
                q.setString(1,outcome);q.setString(2,company);q.setString(3,id);q.setString(4,request);q.setLong(5,amount);q.executeUpdate()
            }
            c.prepareStatement("UPDATE campaigns SET reserved_cents=reserved_cents-?,spent_cents=spent_cents+?,social_progress=social_progress+1,updated_at=now() WHERE id=?::uuid").use { q ->q.setLong(1,amount);q.setLong(2,amount);q.setString(3,id);q.executeUpdate() }
            ledger(c,company,id,request,actor,"SPEND",reserved=-amount,spent=amount,source="outcome:$outcome",note="Financiación de una acción previamente verificada")
            CsrActionResult(outcome,"FUNDED",amountCents=amount)
        }
    }
}
