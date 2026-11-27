package com.onlygoodthings.backend.modules

import com.onlygoodthings.backend.auth.AuthPrincipal
import com.onlygoodthings.backend.http.*
import com.onlygoodthings.backend.infra.Database
import com.onlygoodthings.backend.realtime.RealtimeHub
import com.onlygoodthings.shared.domain.ApiResponse
import com.onlygoodthings.shared.domain.DiscoverySettings
import com.onlygoodthings.shared.protocol.WsCodec
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.*
import io.ktor.server.auth.principal
import io.ktor.server.response.respond
import io.ktor.server.routing.*

fun Route.discoveryRoutes(db:Database) {
    post("/api/v1/users/discovery-place") {
        val user=call.principal<AuthPrincipal>() ?: return@post call.respond(HttpStatusCode.Unauthorized)
        call.respond(ApiResponse.ok(DiscoverySettingsRepository(db).place(user.userId,JsonBody.receiveMap(call))))
    }
}

fun Route.discoveryCrmRoutes(db:Database,hub:RealtimeHub,authorize:suspend (ApplicationCall,Boolean)->String?) {
    post("/api/v1/crm/platform/discovery/settings") {
        authorize(call,false) ?: return@post
        call.respond(ApiResponse.ok(DiscoverySettingsRepository(db).get()))
    }
    post("/api/v1/crm/platform/discovery/settings/save") {
        val actor=authorize(call,true) ?: return@post
        val data=JsonBody.receiveMap(call)
        val saved=DiscoverySettingsRepository(db).save(actor,data.optInt("adoptionRadiusKm",20),(data["version"] as? Number)?.toLong() ?: 0L)
        hub.projectDiscoverySettings(WsCodec.json.encodeToString(DiscoverySettings.serializer(),saved))
        hub.projectStoryChange()
        call.respond(ApiResponse.ok(saved))
    }
}
