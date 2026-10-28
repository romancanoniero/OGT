package com.onlygoodthings.backend.crm

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.time.Instant

@Serializable data class CrmOperationalAlert(val code:String,val severity:String)
@Serializable data class CrmOperationalCheck(
    val ok:Boolean,val securityViolation:Boolean?=null,
    val services:Map<String,Boolean> = emptyMap(),
    val paymentsMode:String?=null,val sourceSha:String?=null,val daysRemaining:Int?=null,
    val diskUsedPercent:Double?=null,val diskFreeGiB:Double?=null,val memoryAvailablePercent:Double?=null,
    val ageHours:Int?=null,val paymentsOverdue:Int?=null,val paymentIncidents:Int?=null,
    val erasureOverdue:Int?=null,val mediaOverdue:Int?=null,
)
@Serializable data class CrmOperationsSnapshot(
    val schemaVersion:Int=1,val checkedAtEpochSeconds:Long=0,val status:String="unavailable",
    val checks:Map<String,CrmOperationalCheck> = emptyMap(),val alerts:List<CrmOperationalAlert> = emptyList(),
    val stale:Boolean=true,
)

/** Fixed private mount; never expose command output, environment or filesystem paths. */
internal class CrmOperations(private val path:Path=Path.of("/run/ogt-operations/status.json")) {
    private val json=Json {ignoreUnknownKeys=true}
    fun read(now:Long=Instant.now().epochSecond):CrmOperationsSnapshot = runCatching {
        require(Files.isRegularFile(path,LinkOption.NOFOLLOW_LINKS) && Files.size(path) in 1..65536)
        val parsed=json.decodeFromString<CrmOperationsSnapshot>(Files.readString(path))
        require(parsed.schemaVersion==1 && parsed.status in setOf("healthy","warning","critical"))
        require(parsed.checks.keys.all {it in setOf("containers","application","authorization","database","redis","release","tls","capacity","backups")})
        require(parsed.alerts.size<=30 && parsed.alerts.all {it.code.matches(Regex("[A-Z_]{3,80}")) && it.severity in setOf("warning","critical")})
        require(parsed.checks.values.all {check ->
            (check.sourceSha==null || check.sourceSha.matches(Regex("[a-f0-9]{40}"))) &&
            (check.paymentsMode==null || check.paymentsMode in setOf("sandbox","live","disabled","unavailable","unknown")) &&
            check.services.keys.all {it in setOf("ogt-api","ogt-web","ogt-postgres","ogt-redis")}
        })
        val stale=parsed.checkedAtEpochSeconds !in (now-180)..(now+60)
        parsed.copy(stale=stale,status=if(stale)"stale" else parsed.status)
    }.getOrElse {CrmOperationsSnapshot()}
}
