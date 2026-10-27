package com.onlygoodthings.backend.auth

import com.google.firebase.auth.AuthErrorCode
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthException
import com.onlygoodthings.backend.infra.Database
import java.util.concurrent.TimeUnit

internal class IdentityDeletionFailure(val safeCode:String):Exception()
internal fun deletionRetryDelaySeconds(attempt:Int):Long = (30L shl attempt.coerceIn(0,7)).coerceAtMost(3600)
internal fun safeDeletionError(error:Exception):String =
    (error as? IdentityDeletionFailure)?.safeCode?.takeIf { it.matches(Regex("[A-Z0-9_]{1,80}")) } ?: "UNAVAILABLE"

private fun deleteFirebaseUser(uid:String) {
    val future=FirebaseAuth.getInstance().deleteUserAsync(uid)
    try { future.get(20,TimeUnit.SECONDS) }
    catch(error:Exception) {
        future.cancel(true)
        val cause=error.cause ?: error
        if(cause is FirebaseAuthException && cause.authErrorCode==AuthErrorCode.USER_NOT_FOUND)return
        val code=if(cause is FirebaseAuthException)cause.authErrorCode?.name ?: "FIREBASE_ERROR"
            else if(error is java.util.concurrent.TimeoutException) "TIMEOUT" else "UNAVAILABLE"
        throw IdentityDeletionFailure(code)
    }
}

/** Retryable outbox, independent of a phone staying open or a request being retried. */
class AccountErasureService(private val db:Database,private val deleteIdentity:(String)->Unit=::deleteFirebaseUser) {
    fun processPending() {
        repeat(10) {
            val processed=db.withConnection { c ->
                c.autoCommit=false
                try {
                    val job=c.createStatement().use { q -> q.executeQuery("""SELECT uid_hash,firebase_uid,attempts
                        FROM account_erasure_jobs WHERE completed_at IS NULL AND next_attempt_at<=now()
                        ORDER BY requested_at FOR UPDATE SKIP LOCKED LIMIT 1""").use { r ->
                        if(r.next())Triple(r.getString(1),r.getString(2),r.getInt(3)) else null
                    } }
                    if(job==null) { c.commit();false }
                    else {
                        val failure=try { deleteIdentity(job.second);null } catch(error:Exception) { safeDeletionError(error) }
                        if(failure==null)c.prepareStatement("""UPDATE account_erasure_jobs SET firebase_uid=NULL,
                            completed_at=now(),attempts=attempts+1,last_error_code=NULL WHERE uid_hash=?""").use { q -> q.setString(1,job.first);q.executeUpdate() }
                        else c.prepareStatement("""UPDATE account_erasure_jobs SET attempts=attempts+1,last_error_code=?,
                            next_attempt_at=now()+(? * interval '1 second') WHERE uid_hash=?""").use { q ->
                            q.setString(1,failure);q.setLong(2,deletionRetryDelaySeconds(job.third));q.setString(3,job.first);q.executeUpdate()
                        }
                        c.commit();true
                    }
                } catch(error:Exception) { c.rollback();throw error } finally { c.autoCommit=true }
            }
            if(!processed)return
        }
    }
}
