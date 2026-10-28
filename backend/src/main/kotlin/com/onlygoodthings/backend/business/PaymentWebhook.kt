package com.onlygoodthings.backend.business

import com.onlygoodthings.backend.http.JsonBody
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.respond
import io.ktor.server.routing.*
import java.time.Instant
import io.ktor.utils.io.readRemaining
import io.ktor.utils.io.core.readText

fun Route.paymentWebhook(payments:CsrPayments?,secret:String?) {
    post("/api/v1/payments/mercadopago/webhook") {
        if(payments==null || secret.isNullOrBlank())return@post call.respond(HttpStatusCode.ServiceUnavailable)
        if((call.request.header("Content-Length")?.toLongOrNull() ?:0)>65536)return@post call.respond(HttpStatusCode.PayloadTooLarge)
        val id=call.request.queryParameters["data.id"].orEmpty()
        if(!validPaymentSignature(id,call.request.header("x-request-id"),call.request.header("x-signature"),secret,Instant.now().epochSecond))return@post call.respond(HttpStatusCode.Unauthorized)
        val text=call.receiveChannel().readRemaining(65537).readText()
        if(text.length>65536)return@post call.respond(HttpStatusCode.PayloadTooLarge)
        val body=runCatching{JsonBody.objectMapper.readTree(text)}.getOrNull() ?:return@post call.respond(HttpStatusCode.BadRequest)
        if(body.path("data").path("id").asText()!=id || body.path("type").asText()!="payment")return@post call.respond(HttpStatusCode.BadRequest)
        payments.enqueue(id)
        call.respond(HttpStatusCode.OK)
    }
}
