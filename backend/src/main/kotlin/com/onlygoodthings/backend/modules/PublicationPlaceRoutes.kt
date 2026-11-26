package com.onlygoodthings.backend.modules

import com.onlygoodthings.backend.http.JsonBody
import com.onlygoodthings.backend.http.reqDouble
import com.onlygoodthings.backend.http.reqString
import com.onlygoodthings.shared.domain.ApiResponse
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import kotlinx.coroutines.CancellationException

internal fun Route.publicationPlaceRoutes(places:PublicationGeocoder=PublicationGeocoder()) {
    post("/api/v1/geo/places/search") {
        requireUser(call) ?: return@post
        val data = JsonBody.receiveMap(call)
        val query = data.reqString("query").trim()
        require(query.length <= 200) { "El lugar admite hasta 200 caracteres" }
        val hits = try { places.search(query, data["lang"] as? String ?: "es") }
        catch (cancel: CancellationException) { throw cancel }
        catch (_: Exception) {
            return@post call.respond(HttpStatusCode.BadGateway, ApiResponse.fail<Unit>("No pudimos buscar lugares. Podés escribirlo libremente.", "GEO_UPSTREAM"))
        }
        call.respond(ApiResponse.ok(hits))
    }

    post("/api/v1/geo/places/reverse") {
        requireUser(call) ?: return@post
        val data = JsonBody.receiveMap(call)
        val lat = data.reqDouble("latitude"); val lng = data.reqDouble("longitude")
        require(lat.isFinite() && lat in -90.0..90.0 && lng.isFinite() && lng in -180.0..180.0) { "Ubicación inválida" }
        val hit = try { places.reverse(lat, lng, data["lang"] as? String ?: "es") }
        catch (cancel: CancellationException) { throw cancel }
        catch (_: Exception) {
            return@post call.respond(HttpStatusCode.BadGateway, ApiResponse.fail<Unit>("No pudimos identificar el lugar. Podés escribirlo libremente.", "GEO_UPSTREAM"))
        }
        if (hit == null) return@post call.respond(HttpStatusCode.NotFound, ApiResponse.fail<Unit>("Sin lugar cercano", "NOT_FOUND"))
        call.respond(ApiResponse.ok(hit))
    }

}
