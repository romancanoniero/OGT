package com.onlygoodthings.backend.media

import com.onlygoodthings.backend.auth.AuthPrincipal
import com.onlygoodthings.backend.http.JsonBody
import com.onlygoodthings.backend.http.optString
import com.fasterxml.jackson.core.JsonProcessingException
import com.onlygoodthings.shared.domain.ApiResponse
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.auth.principal
import io.ktor.server.response.header
import io.ktor.server.response.respond
import io.ktor.server.response.respondBytes
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import java.nio.file.Files
import java.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

fun Route.publicMediaRoutes(store: MediaStore,available:(String)->Boolean={true}) {
    get("/media/u/{name}") {
        val name = call.parameters["name"].orEmpty()
        val file = store.resolvePublic(name)
            ?: return@get call.respond(HttpStatusCode.NotFound, ApiResponse.fail<Unit>("Archivo inexistente", "NOT_FOUND"))
        val bytes=withContext(Dispatchers.IO) {
            if(!available(name)) null
            else try { Files.readAllBytes(file) } catch (_: java.nio.file.NoSuchFileException) { null }
        }
            ?: return@get call.respond(HttpStatusCode.NotFound)
        call.response.header(HttpHeaders.CacheControl, "public, max-age=300, must-revalidate")
        call.respondBytes(bytes, ContentType.parse(store.contentTypeOf(name)))
    }
}

fun Route.mediaRoutes(store: MediaStore,onUploaded:(String,String)->Unit={_,_->}) {
    post("/api/v1/media/upload") {
        val principal=call.principal<AuthPrincipal>() ?: return@post call.respond(
            HttpStatusCode.Unauthorized,
            ApiResponse.fail<Unit>("Token requerido", "UNAUTHENTICATED"),
        )
        val dataMap = try { JsonBody.receiveMap(call) } catch (_: JsonProcessingException) {
            return@post call.respond(HttpStatusCode.BadRequest, ApiResponse.fail<Unit>("JSON inválido", "BAD_REQUEST"))
        }
        val filename = requireNotNull(dataMap.optString("filename")) { "Campo requerido: filename" }
        val contentType = dataMap.optString("contentType")
        val encoded = requireNotNull(dataMap.optString("bytesBase64")) { "Campo requerido: bytesBase64" }
        require(encoded.length <= ((MediaStore.MAX_BYTES + 2) / 3) * 4 + 4096) { "El archivo supera 8 MB" }
        val raw = encoded.replace("\\s".toRegex(), "")
        val bytes = runCatching { Base64.getDecoder().decode(raw) }.getOrElse {
            return@post call.respond(HttpStatusCode.BadRequest, ApiResponse.fail<Unit>("bytesBase64 inválido", "BAD_REQUEST"))
        }
        val saved = withContext(Dispatchers.IO) {
            val uploaded=store.save(bytes, filename, contentType)
            try { onUploaded(principal.userId,uploaded.filename);uploaded }
            catch(error:Exception) { store.deleteLocal(uploaded.url);throw error }
        }
        call.respond(ApiResponse.ok(saved, "Archivo listo, como una foto subida"))
    }
}

/*
Postman — subir una foto (queda en disco de la API, no en Commons)

POST {{base}}/api/v1/media/upload
Authorization: Bearer {{jwt}}
{
  "filename": "huerta-01.jpg",
  "contentType": "image/jpeg",
  "bytesBase64": "/9j/4AAQSkZJRgABAQAAAQABAAD/2wCEAAkGBxISEhUS…"
}

Respuesta: { "id", "kind": "IMAGE", "url": "http://…/media/u/{uuid}.jpg", "filename" }

Esa `url` es la que va en `POST /api/v1/social/publish` → media[].url.
GET /media/u/{filename} es público: el feed la pide sin token.
*/
