package com.onlygoodthings.backend.business

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*
import java.sql.Connection
import java.security.MessageDigest
import java.util.UUID

@Serializable data class CsrTeamMember(val userId:String,val name:String,val role:String,val status:String)
@Serializable data class CsrTeamOffer(val id:String,val companyId:String,val companyName:String,val recipientId:String?,val recipientName:String,
    val proposedBy:String?,val proposerName:String,val role:String,val status:String,val expiresAtEpochMs:Long,val ownership:Boolean=false)
@Serializable data class CsrTeamDesk(val companyId:String,val actorUserId:String,val role:String,val members:List<CsrTeamMember>,val invitations:List<CsrTeamOffer>,val transfers:List<CsrTeamOffer>)

@Serializable data class CsrPersonalTeamRecord(val id:String,val companyId:String,val kind:String,val role:String,val status:String)

private val delegatedRoles=setOf("ADMIN","CAMPAIGN_MANAGER","FINANCE","ANALYST","VIEWER")
/** Identity-bound invitations appear inside Empresas; no bearer links or unsolicited mail. */
class CsrTeam(private val repository:CsrRepository) {
    private val db get()=repository.db
    private fun role(c:Connection,user:String?,company:String):String? = c.prepareStatement("""SELECT a.member_role FROM crm_company_access a
        JOIN users u ON u.id=a.user_id WHERE a.company_id=?::uuid AND a.user_id=?::uuid AND u.status='ACTIVE'""").use { q ->
        q.setString(1,company);q.setString(2,user);q.executeQuery().use {if(it.next())it.getString(1) else null}
    }
    private fun manager(c:Connection,actor:String,company:String,targetRole:String?=null):String {
        val own=role(c,actor,company)
        if(own !in setOf("OWNER","ADMIN") || (own=="ADMIN" && targetRole in setOf("OWNER","ADMIN")))throw BusinessAccessDenied()
        return own!!
    }
    private fun offer(c:Connection,id:String,ownership:Boolean):CsrTeamOffer {
        val table=if(ownership)"csr_ownership_transfers" else "csr_team_invitations"
        val sender=if(ownership)"proposed_by" else "invited_by"
        val memberRole=if(ownership)"'OWNER'" else "o.member_role"
        return c.prepareStatement("""SELECT o.id::text,o.company_id::text,coalesce(nullif(co.trade_name,''),co.legal_name),o.recipient_id::text,
            coalesce(u.display_name,'Cuenta eliminada'),o.$sender::text,coalesce(s.display_name,'Cuenta eliminada'),$memberRole,
            CASE WHEN o.status='PENDING' AND o.expires_at<=now() THEN 'EXPIRED' ELSE o.status END,
            (extract(epoch FROM o.expires_at)*1000)::bigint
            FROM $table o JOIN companies co ON co.id=o.company_id LEFT JOIN users u ON u.id=o.recipient_id
            LEFT JOIN users s ON s.id=o.$sender WHERE o.id=?::uuid""").use {q ->
            q.setString(1,id);q.executeQuery().use {r ->require(r.next()){"Invitación o traspaso no disponible"}
                CsrTeamOffer(r.getString(1),r.getString(2),r.getString(3),r.getString(4),r.getString(5),r.getString(6),r.getString(7),r.getString(8),r.getString(9),r.getLong(10),ownership)}
        }
    }
    private fun offers(c:Connection,actor:String?,company:String?,ownership:Boolean):List<CsrTeamOffer> {
        val table=if(ownership)"csr_ownership_transfers" else "csr_team_invitations"
        val ids=c.prepareStatement("""SELECT id::text FROM $table WHERE ${if(company!=null)"company_id" else "recipient_id"}=?::uuid
            AND status='PENDING' AND expires_at>now() ${if(company!=null && actor!=null)"AND recipient_id=?::uuid" else ""} ORDER BY created_at LIMIT 100""").use {q ->
            q.setString(1,company?:actor);if(company!=null && actor!=null)q.setString(2,actor);q.executeQuery().use {r ->buildList {while(r.next())add(r.getString(1))}}
        }
        return ids.map{offer(c,it,ownership)}
    }
    internal fun export(c:Connection,actor:String):List<CsrPersonalTeamRecord> = c.prepareStatement("""
        SELECT company_id::text,company_id::text,'MEMBERSHIP',member_role,status FROM crm_company_members WHERE user_id=?::uuid
        UNION ALL SELECT company_id::text,company_id::text,'MEMBERSHIP',member_role,'ACTIVE' FROM crm_company_access a
          WHERE user_id=?::uuid AND NOT EXISTS(SELECT 1 FROM crm_company_members m WHERE m.company_id=a.company_id AND m.user_id=a.user_id)
        UNION ALL SELECT id::text,company_id::text,'INVITATION',member_role,
          CASE WHEN status='PENDING' AND expires_at<=now() THEN 'EXPIRED' ELSE status END FROM csr_team_invitations WHERE recipient_id=?::uuid OR invited_by=?::uuid
        UNION ALL SELECT id::text,company_id::text,'TRANSFER','OWNER',
          CASE WHEN status='PENDING' AND expires_at<=now() THEN 'EXPIRED' ELSE status END FROM csr_ownership_transfers WHERE recipient_id=?::uuid OR proposed_by=?::uuid
        """).use {q ->
        repeat(6){q.setString(it+1,actor)};q.executeQuery().use {r ->buildList {while(r.next())add(CsrPersonalTeamRecord(r.getString(1),r.getString(2),r.getString(3),r.getString(4),r.getString(5)))}}
    }
    fun inbox(actor:String):List<CsrTeamOffer> = db.withConnection {c ->
        active(c,actor);offers(c,actor,null,false)+offers(c,actor,null,true)
    }
    fun desk(actor:String,company:String):CsrTeamDesk = db.withConnection {c ->
        repository.access(c,actor,company,businessReadRoles)
        val own=role(c,actor,company)?:throw BusinessAccessDenied()
        val members=c.prepareStatement("""SELECT a.user_id::text,u.display_name,a.member_role FROM crm_company_access a
            JOIN users u ON u.id=a.user_id WHERE a.company_id=?::uuid AND u.status='ACTIVE' ORDER BY a.member_role,u.display_name,a.user_id LIMIT 100""").use {q ->
            q.setString(1,company);q.executeQuery().use {r ->buildList {while(r.next())add(CsrTeamMember(r.getString(1),r.getString(2),r.getString(3),"ACTIVE"))}}
        }
        CsrTeamDesk(company,actor,own,members,if(own in setOf("OWNER","ADMIN"))offers(c,null,company,false) else emptyList(),
            if(own=="OWNER")offers(c,null,company,true) else offers(c,actor,company,true))
    }
    private fun active(c:Connection,actor:String) {
        c.prepareStatement("SELECT 1 FROM users WHERE id=?::uuid AND status='ACTIVE'").use {q ->q.setString(1,actor);q.executeQuery().use {if(!it.next())throw BusinessAccessDenied()}}
    }
    private fun <T> transaction(users:List<String>,body:(Connection)->T):T = db.withConnection {c ->
        c.autoCommit=false
        try {
            // Same order as account erasure: users (sorted) before companies.
            c.prepareStatement("SELECT id FROM users WHERE id=ANY(?::uuid[]) ORDER BY id FOR NO KEY UPDATE").use {q ->
                q.setArray(1,c.createArrayOf("uuid",users.distinct().sorted().toTypedArray()));q.executeQuery().use {while(it.next()){}}
            }
            val result=body(c);c.commit();result
        }catch(e:Exception){c.rollback();throw e}finally{c.autoCommit=true}
    }
    private fun operation(c:Connection,actor:String,company:String,request:String,kind:String,parameters:JsonObject,body:()->CsrActionResult):CsrActionResult {
        UUID.fromString(request);active(c,actor)
        c.prepareStatement("SELECT id FROM companies WHERE id=?::uuid FOR UPDATE").use {q ->q.setString(1,company);q.executeQuery().use {if(!it.next())throw BusinessAccessDenied()}}
        val digest=MessageDigest.getInstance("SHA-256").digest((kind+parameters.toString()).toByteArray()).joinToString(""){"%02x".format(it)}
        // All company writes serialize; a conflicting global request ID still cannot be reused.
        c.prepareStatement("SELECT pg_advisory_xact_lock(hashtextextended(?,0))").use {q ->q.setString(1,"team:$request");q.execute()}
        val prior=c.prepareStatement("SELECT company_id::text,actor_user_id::text,kind,payload_hash,result_json::text FROM csr_operations WHERE id=?::uuid").use {q ->
            q.setString(1,request);q.executeQuery().use {r ->if(r.next()){
                require(r.getString(1)==company && r.getString(2)==actor && r.getString(3)==kind && r.getString(4)==digest){"Ese identificador corresponde a otra operación"}
                Json.decodeFromString(CsrActionResult.serializer(),r.getString(5))
            }else null}
        }
        if(prior!=null)return prior
        val result=body()
        c.prepareStatement("INSERT INTO csr_operations(id,company_id,actor_user_id,kind,payload_hash,result_json) VALUES(?::uuid,?::uuid,?::uuid,?,?,?::jsonb)").use {q ->
            q.setString(1,request);q.setString(2,company);q.setString(3,actor);q.setString(4,kind);q.setString(5,digest);q.setString(6,Json.encodeToString(CsrActionResult.serializer(),result));q.executeUpdate()
        }
        c.prepareStatement("""INSERT INTO crm_audit_events(actor_user_id,company_id,action,target_type,target_id,reason,request_id,outcome,metadata)
            VALUES(?::uuid,?::uuid,?,'company_team',?,'Gestión del equipo empresarial',?,'SUCCESS',?::jsonb)""").use {q ->
            q.setString(1,actor);q.setString(2,company);q.setString(3,kind);q.setString(4,result.id);q.setString(5,request);q.setString(6,parameters.toString());q.executeUpdate()
        }
        return result
    }
    private fun expire(c:Connection,company:String) {
        for(table in listOf("csr_team_invitations","csr_ownership_transfers"))c.prepareStatement("UPDATE $table SET status='EXPIRED',decided_at=now() WHERE company_id=?::uuid AND status='PENDING' AND expires_at<=now()").use {q ->q.setString(1,company);q.executeUpdate()}
    }
    private fun setMember(c:Connection,company:String,user:String,role:String,status:String="ACTIVE",inviter:String?=null) {
        c.prepareStatement("""INSERT INTO crm_company_members(company_id,user_id,member_role,status,accepted_at,invited_by)
            VALUES(?::uuid,?::uuid,?,?,now(),?::uuid) ON CONFLICT(company_id,user_id) DO UPDATE
            SET member_role=excluded.member_role,status=excluded.status,accepted_at=CASE WHEN crm_company_members.status<>'ACTIVE' THEN now() ELSE coalesce(crm_company_members.accepted_at,now()) END,invited_by=coalesce(excluded.invited_by,crm_company_members.invited_by),updated_at=now()""").use {q ->
            q.setString(1,company);q.setString(2,user);q.setString(3,role);q.setString(4,status);q.setString(5,inviter);q.executeUpdate()
        }
        // Keep old clients/data compatible. The view remains authoritative even if
        // a stale legacy row is reintroduced by an older maintenance process.
        c.prepareStatement("DELETE FROM company_admins WHERE company_id=?::uuid AND user_id=?::uuid").use {q ->q.setString(1,company);q.setString(2,user);q.executeUpdate()}
        if(status=="ACTIVE" && role in setOf("OWNER","ADMIN"))c.prepareStatement("INSERT INTO company_admins(company_id,user_id,is_primary) VALUES(?::uuid,?::uuid,?)").use {q ->q.setString(1,company);q.setString(2,user);q.setBoolean(3,role=="OWNER");q.executeUpdate()}
    }
    fun invite(actor:String,company:String,request:String,email:String,role:String):CsrActionResult {
        require(role in delegatedRoles){"Rol no permitido; la titularidad requiere un traspaso"}
        val address=email.trim().lowercase();require(address.length<=254 && address.matches(Regex("[^\\s@]+@[^\\s@]+\\.[^\\s@]+"))){"Revisá el correo de la cuenta OGT"}
        // Resolve only an existing account. An invitation never registers a user or
        // silently assigns access based on a later email/identity change.
        val target=db.withConnection {c ->manager(c,actor,company,role)
            c.prepareStatement("SELECT id::text FROM users WHERE lower(email)=? AND status='ACTIVE' LIMIT 2").use {q ->q.setString(1,address);q.executeQuery().use {r ->buildList {while(r.next())add(r.getString(1))}}}.singleOrNull()
        } ?:throw IllegalArgumentException("La persona debe tener una cuenta OGT activa con ese correo")
        require(target!=actor){"Ya formás parte de esta empresa"}
        return transaction(listOf(actor,target)){c ->operation(c,actor,company,request,"TEAM_INVITE",buildJsonObject{put("recipient",target);put("role",role)}){
            manager(c,actor,company,role);active(c,target);expire(c,company)
            require(role(c,target,company)==null){"La persona ya integra el equipo; usá Cambiar rol"}
            c.prepareStatement("SELECT count(*) FROM csr_team_invitations WHERE company_id=?::uuid AND created_at>now()-interval '1 day'").use {q ->q.setString(1,company);q.executeQuery().use {it.next();require(it.getInt(1)<50){"Alcanzaste el límite diario de invitaciones"}}}
            c.prepareStatement("SELECT (SELECT count(*) FROM crm_company_access WHERE company_id=?::uuid)+(SELECT count(*) FROM csr_team_invitations WHERE company_id=?::uuid AND status='PENDING')").use {q ->q.setString(1,company);q.setString(2,company);q.executeQuery().use {it.next();require(it.getInt(1)<100){"El equipo alcanzó el límite de integrantes e invitaciones"}}}
            c.prepareStatement("SELECT 1 FROM csr_team_invitations WHERE company_id=?::uuid AND recipient_id=?::uuid AND status='PENDING'").use {q ->q.setString(1,company);q.setString(2,target);q.executeQuery().use {require(!it.next()){"Ya existe una invitación pendiente para esa persona"}}}
            c.prepareStatement("INSERT INTO csr_team_invitations(id,company_id,recipient_id,invited_by,member_role) VALUES(?::uuid,?::uuid,?::uuid,?::uuid,?)").use {q ->q.setString(1,request);q.setString(2,company);q.setString(3,target);q.setString(4,actor);q.setString(5,role);q.executeUpdate()}
            CsrActionResult(request,"PENDING")
        }}
    }
    fun member(actor:String,company:String,request:String,user:String,newRole:String?,leave:Boolean=false):CsrActionResult {
        require(newRole==null || newRole in delegatedRoles){"Rol no permitido"}
        return transaction(listOf(actor,user)){c ->operation(c,actor,company,request,if(leave)"TEAM_LEAVE" else if(newRole==null)"TEAM_REMOVE" else "TEAM_ROLE",buildJsonObject{put("user",user);put("role",newRole)}){
            val previous=role(c,user,company)?:throw IllegalArgumentException("La persona ya no integra este equipo")
            require(previous!="OWNER"){"Transferí la titularidad antes de salir o modificar al titular"}
            if(leave)require(actor==user && newRole==null) else {
                require(actor!=user){"Usá Salir del equipo para retirar tu propio acceso"}
                manager(c,actor,company,previous);if(newRole!=null)manager(c,actor,company,newRole)
            }
            setMember(c,company,user,newRole?:previous,if(newRole==null)"REMOVED" else "ACTIVE")
            // A removed/demoted manager's pending offers cannot grant old powers.
            c.prepareStatement("UPDATE csr_team_invitations SET status='REVOKED',decided_at=now() WHERE company_id=?::uuid AND invited_by=?::uuid AND status='PENDING'").use {q ->q.setString(1,company);q.setString(2,user);q.executeUpdate()}
            c.prepareStatement("UPDATE csr_ownership_transfers SET status='CANCELLED',decided_at=now() WHERE company_id=?::uuid AND (recipient_id=?::uuid OR proposed_by=?::uuid) AND status='PENDING'").use {q ->q.setString(1,company);q.setString(2,user);q.setString(3,user);q.executeUpdate()}
            CsrActionResult(user,newRole?:"REMOVED")
        }}
    }
    fun propose(actor:String,company:String,request:String,user:String):CsrActionResult = transaction(listOf(actor,user)){c ->
        operation(c,actor,company,request,"TEAM_TRANSFER_PROPOSE",buildJsonObject{put("recipient",user)}){
            if(role(c,actor,company)!="OWNER")throw BusinessAccessDenied()
            require(actor!=user && role(c,user,company) in delegatedRoles){"El nuevo titular debe ser un integrante activo que ya aceptó su invitación"}
            expire(c,company)
            c.prepareStatement("SELECT 1 FROM csr_ownership_transfers WHERE company_id=?::uuid AND status='PENDING'").use {q ->q.setString(1,company);q.executeQuery().use {require(!it.next()){"Ya hay un traspaso pendiente; cancelalo antes de proponer otro"}}}
            c.prepareStatement("INSERT INTO csr_ownership_transfers(id,company_id,proposed_by,recipient_id) VALUES(?::uuid,?::uuid,?::uuid,?::uuid)").use {q ->q.setString(1,request);q.setString(2,company);q.setString(3,actor);q.setString(4,user);q.executeUpdate()}
            CsrActionResult(request,"PENDING")
        }
    }
    fun decide(actor:String,request:String,id:String,ownership:Boolean,decision:String):CsrActionResult {
        require(decision in setOf("ACCEPT","DECLINE","CANCEL")){"Decisión inválida"}
        val initial=db.withConnection{offer(it,id,ownership)}
        if(decision!="CANCEL" && initial.recipientId!=actor)throw BusinessAccessDenied()
        return transaction(listOfNotNull(actor,initial.recipientId,initial.proposedBy)){c ->
            operation(c,actor,initial.companyId,request,"TEAM_${if(ownership)"TRANSFER" else "INVITATION"}_$decision",buildJsonObject{put("id",id)}){
                val row=offer(c,id,ownership);val company=row.companyId
                require(row.status=="PENDING"){"La invitación o el traspaso ya finalizó o venció"}
                if(decision=="CANCEL"){
                    if(ownership){if(role(c,actor,company)!="OWNER")throw BusinessAccessDenied()}
                    else manager(c,actor,company,row.role)
                }else {
                    if(row.recipientId!=actor)throw BusinessAccessDenied()
                    if(decision=="ACCEPT"){
                        if(ownership){
                            require(role(c,row.proposedBy,company)=="OWNER" && role(c,actor,company) in delegatedRoles){"Los integrantes o sus permisos cambiaron; solicitá un nuevo traspaso"}
                            setMember(c,company,actor,"OWNER");setMember(c,company,row.proposedBy!!,"ADMIN")
                            c.prepareStatement("UPDATE csr_team_invitations SET status='REVOKED',decided_at=now() WHERE company_id=?::uuid AND invited_by=?::uuid AND status='PENDING'").use {q ->q.setString(1,company);q.setString(2,row.proposedBy);q.executeUpdate()}
                        }else {
                            manager(c,row.proposedBy?:throw BusinessAccessDenied(),company,row.role)
                            require(role(c,actor,company)==null){"Ya integrás el equipo"}
                            setMember(c,company,actor,row.role,inviter=row.proposedBy)
                        }
                    }
                }
                val state=when(decision){"ACCEPT"->"ACCEPTED";"DECLINE"->"DECLINED";else->if(ownership)"CANCELLED" else "REVOKED"}
                val table=if(ownership)"csr_ownership_transfers" else "csr_team_invitations"
                c.prepareStatement("UPDATE $table SET status=?,decided_at=now() WHERE id=?::uuid").use {q ->q.setString(1,state);q.setString(2,id);q.executeUpdate()}
                CsrActionResult(id,state)
            }
        }
    }
}
