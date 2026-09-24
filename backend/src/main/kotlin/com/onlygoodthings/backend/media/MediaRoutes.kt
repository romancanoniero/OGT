package com.onlygoodthings.backend.media

import com.onlygoodthings.backend.auth.AuthPrincipal
import com.onlygoodthings.backend.http.JsonBody
import com.onlygoodthings.backend.http.optString
import com.onlygoodthings.backend.http.reqString
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

fun Route.publicMediaRoutes(store: MediaStore) {
    get("/media/u/{name}") {
        val name = call.parameters["name"].orEmpty()
        val file = store.resolvePublic(name)
            ?: return@get call.respond(HttpStatusCode.NotFound, ApiResponse.fail<Unit>("Archivo inexistente", "NOT_FOUND"))
        call.response.header(HttpHeaders.CacheControl, "public, max-age=31536000, immutable")
        call.respondBytes(Files.readAllBytes(file), ContentType.parse(store.contentTypeOf(name)))
    }
}

fun Route.mediaRoutes(store: MediaStore) {
    post("/api/v1/media/upload") {
        call.principal<AuthPrincipal>() ?: return@post call.respond(
            HttpStatusCode.Unauthorized,
            ApiResponse.fail<Unit>("Token requerido", "UNAUTHENTICATED"),
        )
        val dataMap = JsonBody.receiveMap(call)
        val filename = dataMap.reqString("filename")
        val contentType = dataMap.optString("contentType")
        val raw = dataMap.reqString("bytesBase64").replace("\\s".toRegex(), "")
        val bytes = runCatching { Base64.getDecoder().decode(raw) }.getOrElse {
            return@post call.respond(HttpStatusCode.BadRequest, ApiResponse.fail<Unit>("bytesBase64 inválido", "BAD_REQUEST"))
        }
        val saved = store.save(bytes, filename, contentType)
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
