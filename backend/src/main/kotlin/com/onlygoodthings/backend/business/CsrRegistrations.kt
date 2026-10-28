package com.onlygoodthings.backend.business

import com.onlygoodthings.backend.infra.Database
import kotlinx.serialization.Serializable
import java.net.URI
import java.security.MessageDigest
import java.sql.Connection
import java.util.UUID

@Serializable
data class CsrRegistration(
    val id:String,val applicantUserId:String,val applicantName:String,
    val legalName:String,val tradeName:String,val taxId:String,val websiteUrl:String,
    val note:String,val status:String,val companyId:String?,val reviewReason:String?,val createdAtEpochMs:Long,
    val canReview:Boolean=false,
)

/** Personal requests and staff review deliberately have separate authorization. */
class CsrRegistrations(private val db:Database) {
    private val fields="""r.id::text,r.applicant_user_id::text,u.display_name,r.legal_name,r.trade_name,r.tax_id,
        r.website_url,r.applicant_note,r.status,r.company_id::text,r.review_reason,
        (extract(epoch FROM r.created_at)*1000)::bigint"""
    private fun java.sql.ResultSet.row()=CsrRegistration(getString(1),getString(2),getString(3),getString(4),getString(5),getString(6),getString(7),getString(8),getString(9),getString(10),getString(11),getLong(12))
    private fun active(c:Connection,user:String,lock:Boolean=false) {
        c.prepareStatement("SELECT 1 FROM users WHERE id=?::uuid AND status='ACTIVE' ${if(lock)"FOR UPDATE" else ""}").use { q ->
            q.setString(1,user);q.executeQuery().use {if(!it.next())throw BusinessAccessDenied()}
        }
    }
    private fun reviewer(c:Connection,actor:String) {
        active(c,actor)
        c.prepareStatement("""SELECT 1 FROM crm_platform_staff WHERE user_id=?::uuid AND enabled
            AND approved_by IS NOT NULL AND approved_at IS NOT NULL AND staff_role IN ('OWNER','OPERATIONS')""").use { q ->
            q.setString(1,actor);q.executeQuery().use {if(!it.next())throw BusinessAccessDenied()}
        }
    }
    private fun audit(c:Connection,actor:String,action:String,target:String?,reason:String,company:String?=null,request:String?=null) {
        c.prepareStatement("""INSERT INTO crm_audit_events(actor_user_id,company_id,action,target_type,target_id,reason,request_id,outcome)
            VALUES(?::uuid,?::uuid,?,'company_registration',?,?,?,'SUCCESS')""").use { q ->
            q.setString(1,actor);q.setString(2,company);q.setString(3,action);q.setString(4,target);q.setString(5,reason);q.setString(6,request);q.executeUpdate()
        }
    }
    internal fun own(c:Connection,actor:String,forUi:Boolean=false):List<CsrRegistration> {
        active(c,actor)
        return c.prepareStatement("SELECT $fields FROM csr_company_registrations r JOIN users u ON u.id=r.applicant_user_id WHERE r.applicant_user_id=?::uuid ORDER BY r.created_at DESC ${if(forUi)"LIMIT 50" else ""}").use { q ->
            q.setString(1,actor);q.executeQuery().use { r ->buildList {while(r.next())add(r.row())} }
        }
    }
    fun own(actor:String)=db.withConnection {own(it,actor,forUi=true)}
    fun pending(actor:String):List<CsrRegistration> = db.withConnection { c ->
        reviewer(c,actor)
        val result=c.prepareStatement("SELECT $fields FROM csr_company_registrations r JOIN users u ON u.id=r.applicant_user_id WHERE r.status='PENDING_REVIEW' ORDER BY r.created_at LIMIT 200").use { q ->q.executeQuery().use { r ->buildList {while(r.next()){val row=r.row();add(row.copy(canReview=row.applicantUserId!=actor))}} } }
        audit(c,actor,"COMPANY_APPLICATION_LIST",null,"Revisión de solicitudes empresariales pendientes")
        result
    }
    private fun <T> transaction(body:(Connection)->T):T = db.withConnection { c ->
        c.autoCommit=false
        try {val value=body(c);c.commit();value} catch(error:Exception){c.rollback();throw error} finally{c.autoCommit=true}
    }
    fun submit(actor:String,request:String,legal:String,trade:String,tax:String,website:String,note:String):CsrActionResult {
        UUID.fromString(request)
        val name=legal.trim();val brand=trade.trim();val fiscal=tax.uppercase().replace(Regex("[ .-]"),"")
        val url=website.trim();val description=note.trim()
        require(name.length in 2..160 && brand.length<=160 && fiscal.matches(Regex("[A-Z0-9]{5,30}")) && description.length in 10..2000 && url.length<=500){"Revisá la razón social, identificación fiscal y descripción de la solicitud"}
        require(url.isEmpty() || runCatching {val parsed=URI(url);parsed.scheme=="https" && !parsed.host.isNullOrBlank() && parsed.userInfo==null}.getOrDefault(false)){"El sitio de la empresa debe ser una dirección HTTPS"}
        val fingerprint=MessageDigest.getInstance("SHA-256").digest(listOf(name,brand,fiscal,url,description).joinToString("\u0000").toByteArray()).joinToString(""){"%02x".format(it)}
        return transaction { c ->
            c.prepareStatement("SELECT pg_advisory_xact_lock(hashtextextended(?,0))").use { q ->q.setString(1,"company-registration:$request");q.execute() }
            // Same user lock order as account erasure, so approval cannot race a deletion.
            active(c,actor,true)
            val prior=c.prepareStatement("SELECT applicant_user_id::text,payload_hash,status,company_id::text FROM csr_company_registrations WHERE id=?::uuid").use { q ->
                q.setString(1,request);q.executeQuery().use { r ->if(r.next())listOf(r.getString(1),r.getString(2),r.getString(3),r.getString(4)) else null }
            }
            if(prior!=null){require(prior[0]==actor && prior[1]==fingerprint){"Ese identificador ya corresponde a otra solicitud"};return@transaction CsrActionResult(prior[3]?:request,prior[2])}
            c.prepareStatement("SELECT count(*),count(*) FILTER(WHERE status='PENDING_REVIEW') FROM csr_company_registrations WHERE applicant_user_id=?::uuid AND (created_at>now()-interval '1 day' OR status='PENDING_REVIEW')").use { q ->
                q.setString(1,actor);q.executeQuery().use { r ->r.next();require(r.getLong(2)==0L){"Ya tenés una solicitud pendiente. Podés retirarla antes de corregirla."};require(r.getLong(1)<5){"Alcanzaste el límite diario de solicitudes. Volvé a intentar mañana."} }
            }
            c.prepareStatement("INSERT INTO csr_company_registrations(id,applicant_user_id,payload_hash,legal_name,trade_name,tax_id,website_url,applicant_note) VALUES(?::uuid,?::uuid,?,?,?,?,?,?)").use { q ->
                q.setString(1,request);q.setString(2,actor);q.setString(3,fingerprint);q.setString(4,name);q.setString(5,brand);q.setString(6,fiscal);q.setString(7,url);q.setString(8,description);q.executeUpdate()
            }
            audit(c,actor,"COMPANY_APPLICATION_SUBMIT",request,"Solicitud de incorporación enviada por su titular",request=request)
            CsrActionResult(request,"PENDING_REVIEW")
        }
    }
    fun withdraw(actor:String,id:String):CsrActionResult = transaction { c ->
        active(c,actor,true)
        val status=c.prepareStatement("SELECT status FROM csr_company_registrations WHERE id=?::uuid AND applicant_user_id=?::uuid FOR UPDATE").use { q ->
            q.setString(1,id);q.setString(2,actor);q.executeQuery().use { r ->require(r.next()){ "Solicitud no disponible" };r.getString(1) }
        }
        if(status=="WITHDRAWN")return@transaction CsrActionResult(id,status)
        require(status=="PENDING_REVIEW"){"La solicitud ya fue revisada; no se puede retirar"}
        c.prepareStatement("UPDATE csr_company_registrations SET status='WITHDRAWN',updated_at=now() WHERE id=?::uuid").use { q ->q.setString(1,id);q.executeUpdate() }
        audit(c,actor,"COMPANY_APPLICATION_WITHDRAW",id,"Solicitud retirada por su titular")
        CsrActionResult(id,"WITHDRAWN")
    }
    fun review(actor:String,request:String,id:String,approve:Boolean,reason:String):CsrActionResult = transaction { c ->
        UUID.fromString(request);require(reason.trim().length in 10..500){"Explicá la revisión en 10 a 500 caracteres"};reviewer(c,actor)
        c.prepareStatement("SELECT pg_advisory_xact_lock(hashtextextended(?,0))").use { q ->q.setString(1,"company-review:$request");q.execute() }
        c.prepareStatement("SELECT id::text FROM csr_company_registrations WHERE review_request_id=?::uuid").use { q ->q.setString(1,request);q.executeQuery().use { r ->require(!r.next() || r.getString(1)==id){"Ese identificador corresponde a otra revisión"} } }
        val applicant=c.prepareStatement("SELECT applicant_user_id::text FROM csr_company_registrations WHERE id=?::uuid").use { q ->q.setString(1,id);q.executeQuery().use { r ->require(r.next()){ "Solicitud no disponible" };r.getString(1) } }
        require(applicant!=actor){"La solicitud debe revisarla otra persona autorizada"}
        active(c,applicant,true)
        val current=c.prepareStatement("SELECT $fields,r.review_request_id::text,r.reviewer_user_id::text FROM csr_company_registrations r JOIN users u ON u.id=r.applicant_user_id WHERE r.id=?::uuid FOR UPDATE OF r").use { q ->
            q.setString(1,id);q.executeQuery().use { r ->require(r.next()){ "Solicitud no disponible" };Triple(r.row(),r.getString(13),r.getString(14)) }
        }
        val state=if(approve)"APPROVED" else "REJECTED";val row=current.first
        if(row.status!="PENDING_REVIEW"){
            require(current.second==request && current.third==actor && row.status==state && row.reviewReason==reason.trim()){ "La solicitud ya fue resuelta o retirada" }
            return@transaction CsrActionResult(row.companyId?:id,state)
        }
        val company=if(approve)UUID.randomUUID().toString() else null
        if(company!=null){
            c.prepareStatement("SELECT pg_advisory_xact_lock(hashtextextended(?,0))").use { q ->q.setString(1,"company-tax:${row.taxId}");q.execute() }
            val duplicate=c.prepareStatement("SELECT EXISTS(SELECT 1 FROM companies WHERE upper(regexp_replace(coalesce(tax_id,''),'[ .-]','','g'))=?)").use { q ->q.setString(1,row.taxId);q.executeQuery().use { r ->r.next();r.getBoolean(1) } }
            require(!duplicate){"Ya existe una empresa con esa identificación fiscal. Revisá la titularidad; no se asignaron permisos."}
            c.prepareStatement("INSERT INTO companies(id,legal_name,trade_name,tax_id,verification_status,verified_at,website_url) VALUES(?::uuid,?,?,?,'VERIFIED',now(),?)").use { q ->q.setString(1,company);q.setString(2,row.legalName);q.setString(3,row.tradeName.takeIf {it.isNotBlank()});q.setString(4,row.taxId);q.setString(5,row.websiteUrl.takeIf {it.isNotBlank()});q.executeUpdate() }
            c.prepareStatement("INSERT INTO crm_company_members(company_id,user_id,member_role,status,accepted_at,invited_by) VALUES(?::uuid,?::uuid,'OWNER','ACTIVE',now(),?::uuid)").use { q ->q.setString(1,company);q.setString(2,applicant);q.setString(3,actor);q.executeUpdate() }
            c.prepareStatement("INSERT INTO company_admins(company_id,user_id,is_primary) VALUES(?::uuid,?::uuid,true)").use { q ->q.setString(1,company);q.setString(2,applicant);q.executeUpdate() }
        }
        c.prepareStatement("UPDATE csr_company_registrations SET status=?,company_id=?::uuid,review_request_id=?::uuid,reviewer_user_id=?::uuid,review_reason=?,updated_at=now() WHERE id=?::uuid").use { q ->q.setString(1,state);q.setString(2,company);q.setString(3,request);q.setString(4,actor);q.setString(5,reason.trim());q.setString(6,id);q.executeUpdate() }
        audit(c,actor,"COMPANY_APPLICATION_"+state,id,reason.trim(),company,request)
        CsrActionResult(company?:id,state)
    }
}
