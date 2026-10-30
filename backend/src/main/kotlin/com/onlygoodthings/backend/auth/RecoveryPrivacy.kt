package com.onlygoodthings.backend.auth

import com.onlygoodthings.backend.crm.crmFirebaseTombstoneHash
import com.onlygoodthings.backend.http.JsonBody
import com.onlygoodthings.backend.infra.Database
import com.onlygoodthings.backend.infra.IdentityStore
import java.security.MessageDigest
import java.sql.DriverManager
import kotlin.system.exitProcess

internal fun recoveryDatabaseAllowed(name:String) = name.matches(Regex("ogt_recovery_[a-f0-9]{32}"))
internal data class RecoveryErasureJournal(val capturedAt:Long,val hashes:Set<String>)
internal fun recoveryErasureJournal(payload:ByteArray,expectedSha:String,snapshot:Long,requiredThrough:Long):RecoveryErasureJournal {
    require(payload.size in 1..2*1024*1024 && expectedSha.matches(Regex("[a-f0-9]{64}")))
    val sha=MessageDigest.getInstance("SHA-256").digest(payload).joinToString("") {"%02x".format(it)}
    require(sha==expectedSha) {"Journal fingerprint mismatch"}
    val root=JsonBody.objectMapper.readTree(payload)
    require(root.path("kind").asText()=="ogt-erasure-journal-v1")
    require(root.path("capturedAtEpochSeconds").isIntegralNumber)
    val captured=root.path("capturedAtEpochSeconds").asLong()
    require(snapshot>0 && requiredThrough>=snapshot && captured>=requiredThrough && captured<=System.currentTimeMillis()/1000+120) {"The journal does not cover the last possible source write"}
    val array=root.path("hashes");require(array.isArray && array.size()<=25000)
    val hashes=array.map {require(it.isTextual);it.asText()}
    require(hashes==hashes.distinct().sorted() && hashes.all {it.matches(Regex("[a-f0-9]{64}"))})
    return RecoveryErasureJournal(captured,hashes.toSet())
}

/** Offline CLI only. No HTTP route and no Firebase provider operations. */
object RecoveryPrivacy {
    @JvmStatic fun main(args:Array<String>) {
        try {run(args)} catch(_:Exception) {
            System.err.println("RECOVERY_PRIVACY_FAILED: the candidate is not approved for serving; review the private recovery plan.")
            exitProcess(2)
        }
    }
    private fun run(args:Array<String>) {
        val apply="--apply" in args;val values=mutableMapOf<String,String>();var at=0
        while(at<args.size) {
            val key=args[at++]
            if(key=="--apply")continue
            require(key in setOf("--database","--journal-sha256","--snapshot-epoch","--required-through-epoch") && key !in values && at<args.size)
            values[key]=args[at++]
        }
        val name=values.getValue("--database");require(recoveryDatabaseAllowed(name)) {"Only an unserved recovery database is allowed"}
        val expected=values.getValue("--journal-sha256")
        val bytes=System.`in`.readNBytes(2*1024*1024+1)
        val journal=recoveryErasureJournal(bytes,expected,values.getValue("--snapshot-epoch").toLong(),values.getValue("--required-through-epoch").toLong())
        val base=System.getenv("OGT_JDBC_URL").orEmpty();require(base.startsWith("jdbc:postgresql://") && !base.contains('?') && base.endsWith("/onlygoodthings"))
        val target=base.removeSuffix("onlygoodthings")+name
        val user=System.getenv("OGT_DB_USER") ?: "ogt";val password=System.getenv("OGT_DB_PASSWORD") ?: error("Database credential required")
        val marker="ogt-recovery-privacy:"+name.removePrefix("ogt_recovery_")
        // Keep a separate lock for the entire operation; reject any pre-existing
        // connection so the tool cannot reconcile a database already serving users.
        DriverManager.getConnection(target,user,password).use {guard ->
            guard.createStatement().use {q->q.executeQuery("SELECT pg_try_advisory_lock(26052,12029)").use {r->r.next();require(r.getBoolean(1))}}
            guard.prepareStatement("SELECT coalesce(shobj_description(oid,'pg_database'),'') FROM pg_database WHERE datname=?").use {q->q.setString(1,name);q.executeQuery().use {r->require(r.next());val actual=r.getString(1);require(actual==marker || actual.matches(Regex(Regex.escape(marker)+":reconciled:[a-f0-9]{64}")))}}
            guard.createStatement().use {q->q.executeQuery("SELECT count(*) FROM pg_stat_activity WHERE datname=current_database() AND pid<>pg_backend_pid()").use {r->r.next();require(r.getInt(1)==0) {"Recovery database has active clients"}}}
            Database(target,user,password).use {db ->
                fun matches():List<String> = db.withConnection {c->c.createStatement().use {q->q.executeQuery("SELECT id::text,firebase_uid FROM users ORDER BY id").use {r->buildList {while(r.next())if(crmFirebaseTombstoneHash(r.getString(2)) in journal.hashes)add(r.getString(1))}}}}
                val ids=matches()
                val privileged=if(ids.isEmpty())false else db.withConnection {c->c.prepareStatement("SELECT EXISTS(SELECT 1 FROM crm_platform_staff WHERE user_id=ANY(?::uuid[]) OR approved_by=ANY(?::uuid[])) OR EXISTS(SELECT 1 FROM crm_company_access WHERE user_id=ANY(?::uuid[]) AND member_role='OWNER')").use {q->for(i in 1..3)q.setArray(i,c.createArrayOf("uuid",ids.toTypedArray()));q.executeQuery().use {r->r.next();r.getBoolean(1)}}}
                if(apply) {
                    require(!privileged) {"Ownership must be reconciled before an erased account can be removed"}
                    db.withConnection {c->c.autoCommit=false;try {
                        c.prepareStatement("INSERT INTO crm_erased_accounts(uid_hash,reason) VALUES(?,'Baja previamente confirmada; reconciliación posterior a recuperación') ON CONFLICT(uid_hash) DO NOTHING").use {q->for(hash in journal.hashes){q.setString(1,hash);q.addBatch()};q.executeBatch()};c.commit()
                    }catch(e:Exception){c.rollback();throw e}finally{c.autoCommit=true}}
                    val identity=IdentityStore(db)
                    ids.forEach {identity.eraseAccount(it)}
                    check(matches().isEmpty())
                    guard.createStatement().use {it.execute("COMMENT ON DATABASE \"$name\" IS '$marker:reconciled:$expected'")}
                }
                println(JsonBody.objectMapper.writeValueAsString(mapOf("mode" to if(apply)"apply" else "plan","journalSha256" to expected,
                    "journalCapturedAt" to journal.capturedAt,"journalHashes" to journal.hashes.size,"matchingAccounts" to ids.size,
                    "protectedOwnershipConflict" to privileged,"privacyReconciled" to (apply&&!privileged),
                    "sourceDatabaseModified" to false,"firebaseProviderTouched" to false,"approvedForPublicServing" to false)))
            }
        }
    }
}
