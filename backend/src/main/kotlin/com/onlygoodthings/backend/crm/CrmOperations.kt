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
    val complete:Boolean?=null,val files:Long?=null,val mediaBytes:Long?=null,
    val allocatedBytes:Long?=null,
    val uploadImageFiles:Long?=null,val uploadImageBytes:Long?=null,
    val uploadVideoFiles:Long?=null,val uploadVideoBytes:Long?=null,
    val storyFiles:Long?=null,val storyBytes:Long?=null,
    val posterFiles:Long?=null,val posterBytes:Long?=null,
    val otherFiles:Long?=null,val otherBytes:Long?=null,
    val diskTotalBytes:Long?=null,val diskFreeBytes:Long?=null,
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
        require(parsed.checks.keys.all {it in setOf("containers","application","authorization","database","redis","release","tls","capacity","backups","media")})
        require(parsed.alerts.size<=30 && parsed.alerts.all {it.code.matches(Regex("[A-Z_]{3,80}")) && it.severity in setOf("warning","critical")})
        require(parsed.checks.values.all {check ->
            (check.sourceSha==null || check.sourceSha.matches(Regex("[a-f0-9]{40}"))) &&
            (check.paymentsMode==null || check.paymentsMode in setOf("sandbox","live","disabled","unavailable","unknown")) &&
            check.services.keys.all {it in setOf("ogt-api","ogt-web","ogt-postgres","ogt-redis")}
        })
        require(parsed.checks.values.all { c -> listOf(c.files,c.mediaBytes,c.allocatedBytes,c.uploadImageFiles,c.uploadImageBytes,c.uploadVideoFiles,c.uploadVideoBytes,c.storyFiles,c.storyBytes,c.posterFiles,c.posterBytes,c.otherFiles,c.otherBytes,c.diskTotalBytes,c.diskFreeBytes).all { it==null || it>=0 } })
        parsed.checks["media"]?.takeIf { it.complete==true }?.let { m ->
            require(listOf(m.files,m.mediaBytes,m.allocatedBytes,m.uploadImageFiles,m.uploadImageBytes,m.uploadVideoFiles,m.uploadVideoBytes,m.storyFiles,m.storyBytes,m.posterFiles,m.posterBytes,m.otherFiles,m.otherBytes,m.diskTotalBytes,m.diskFreeBytes).all { it!=null })
            require(m.diskTotalBytes!!>0 && m.diskFreeBytes!!<=m.diskTotalBytes)
            require(m.diskUsedPercent!=null && m.diskUsedPercent.isFinite() && m.diskUsedPercent in 0.0..100.0)
            require(kotlin.math.abs(m.diskUsedPercent-100.0*(1.0-m.diskFreeBytes.toDouble()/m.diskTotalBytes))<=0.11)
            require(m.files==listOf(m.uploadImageFiles!!,m.uploadVideoFiles!!,m.storyFiles!!,m.posterFiles!!,m.otherFiles!!).fold(0L,Math::addExact))
            require(m.mediaBytes==listOf(m.uploadImageBytes!!,m.uploadVideoBytes!!,m.storyBytes!!,m.posterBytes!!,m.otherBytes!!).fold(0L,Math::addExact))
        }
        val stale=parsed.checkedAtEpochSeconds !in (now-180)..(now+60)
        parsed.copy(stale=stale,status=if(stale)"stale" else parsed.status)
    }.getOrElse {CrmOperationsSnapshot()}
}
