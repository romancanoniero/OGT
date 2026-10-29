package com.onlygoodthings.backend.stories

import com.onlygoodthings.backend.auth.AuthPrincipal
import com.onlygoodthings.backend.http.*
import com.onlygoodthings.backend.realtime.RealtimeHub
import com.onlygoodthings.shared.domain.*
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.file.Files
import java.util.Base64

fun Route.storyRoutes(repository: StorySqlRepository, hub: RealtimeHub) {
    post("/api/v1/stories/list") {
        val viewer=call.principal<AuthPrincipal>() ?: return@post call.respond(HttpStatusCode.Unauthorized)
        val body=JsonBody.receiveMap(call)
        call.respond(ApiResponse.ok(repository.list(viewer.userId,body.optString("cursor"))))
    }
    post("/api/v1/stories/publish") {
        val viewer=call.principal<AuthPrincipal>() ?: return@post call.respond(HttpStatusCode.Unauthorized)
        if(!repository.publishingAllowed(viewer.userId)) return@post call.respond(HttpStatusCode.Forbidden,ApiResponse.fail<Unit>("Esta cuenta puede leer, pero no publicar","PUBLISHING_BLOCKED"))
        val body=JsonBody.receiveMap(call)
        fun decode(key: String): ByteArray {
            val value=body.reqString(key)
            require(value.length <= ((StoryRules.MAX_BYTES+2)/3)*4+4) { "El archivo supera 8 MB" }
            return Base64.getDecoder().decode(value)
        }
        val kind=body.reqString("kind")
        require(kind in setOf("IMAGE","VIDEO")) { "Tipo de historia inválido" }
        val framing = body["frame"]?.let { raw ->
            require(raw is Map<*, *>) { "Encuadre inválido" }
            fun number(key: String, default: Double): Double = raw[key]?.let { require(it is Number) { "Encuadre inválido" };it.toDouble() } ?: default
            StoryFrame(StoryFrameMode.valueOf(raw["mode"] as? String ?: "FILL"),number("zoom",1.0),number("x",0.0),number("y",0.0))
        }
        val saved=withContext(Dispatchers.IO) { repository.publish(viewer.userId,body.reqString("requestId"),
            body.optString("caption").orEmpty().trim(),StoryAudience.valueOf(body.reqString("audience")),
            decode("bytesBase64"),kind=="VIDEO",
            body.optString("posterBase64")?.let { require(it.length<=2_000_000) { "La vista previa es demasiado grande" }; Base64.getDecoder().decode(it) },framing,ContentAttribution.normalize(body.optString("attributionUrl"))) }
        hub.projectStoryChange()
        call.respond(ApiResponse.ok(saved,"Historia publicada durante 24 horas"))
    }
    post("/api/v1/stories/seen") {
        val viewer=call.principal<AuthPrincipal>() ?: return@post call.respond(HttpStatusCode.Unauthorized)
        repository.markSeen(JsonBody.receiveMap(call).reqString("storyId"),viewer.userId)
        call.respond(ApiResponse.ok("ok"))
    }
    post("/api/v1/stories/report") {
        val viewer=call.principal<AuthPrincipal>() ?: return@post call.respond(HttpStatusCode.Unauthorized)
        val body=JsonBody.receiveMap(call)
        withContext(Dispatchers.IO) { repository.report(body.reqString("storyId"),viewer.userId,body.reqString("reason"),body.optString("details")?.trim()?.takeIf { it.isNotEmpty() }) }
        call.respond(ApiResponse.ok("ok","Denuncia recibida. El equipo de moderación la revisará."))
    }
    post("/api/v1/stories/remove") {
        val viewer=call.principal<AuthPrincipal>() ?: return@post call.respond(HttpStatusCode.Unauthorized)
        repository.remove(JsonBody.receiveMap(call).reqString("storyId"),viewer.userId)
        hub.projectStoryChange()
        call.respond(ApiResponse.ok("ok"))
    }
}

fun Route.storyMediaRoutes(repository: StorySqlRepository) {
    get("/media/stories/{id}/{variant}") {
        call.response.header(HttpHeaders.CacheControl,"private, no-store, max-age=0")
        call.response.header("X-Content-Type-Options","nosniff")
        val id=call.parameters["id"].orEmpty()
        val variant=call.parameters["variant"].orEmpty()
        val viewer=call.request.queryParameters["viewer"].orEmpty()
        val until=call.request.queryParameters["until"]?.toLongOrNull() ?: 0L
        val signature=call.request.queryParameters["signature"].orEmpty()
        val authorized=runCatching { repository.media.signed(id,viewer,until,variant,signature,System.currentTimeMillis()) &&
            repository.canRead(id,viewer,variant.startsWith("admin-")) }.getOrDefault(false)
        if(!authorized) return@get call.respond(HttpStatusCode.NotFound)
        val poster=variant.endsWith("poster")
        val file=repository.media.path(id,poster)
        if(!Files.isRegularFile(file)) return@get call.respond(HttpStatusCode.NotFound)
        val bytes=withContext(Dispatchers.IO) { Files.readAllBytes(file) }
        val type=ContentType.parse(if(poster) {
            if(bytes.size>2 && bytes[0]==(-1).toByte()) "image/jpeg" else if(bytes.size>8 && bytes[0]==(-119).toByte()) "image/png" else "image/webp"
        } else repository.mediaType(id))
        call.response.header(HttpHeaders.AcceptRanges,"bytes")
        val range=call.request.header(HttpHeaders.Range)
        if(range==null) return@get call.respondBytes(bytes,type)
        val match=Regex("bytes=(\\d*)-(\\d*)").matchEntire(range)
        val first=match?.groupValues?.get(1).orEmpty()
        val last=match?.groupValues?.get(2).orEmpty()
        val start=if(first.isNotEmpty()) first.toLongOrNull() else last.toLongOrNull()?.let { (bytes.size-it).coerceAtLeast(0) }
        val end=if(first.isEmpty() || last.isEmpty()) bytes.lastIndex.toLong() else last.toLongOrNull()?.coerceAtMost(bytes.lastIndex.toLong())
        if(start==null || end==null || start !in 0..bytes.lastIndex || end<start) {
            call.response.header(HttpHeaders.ContentRange,"bytes */${bytes.size}")
            return@get call.respond(HttpStatusCode.RequestedRangeNotSatisfiable)
        }
        call.response.header(HttpHeaders.ContentRange,"bytes $start-$end/${bytes.size}")
        call.respondBytes(bytes.copyOfRange(start.toInt(),end.toInt()+1),type,HttpStatusCode.PartialContent)
    }
}
