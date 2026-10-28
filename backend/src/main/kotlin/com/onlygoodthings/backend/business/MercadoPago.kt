package com.onlygoodthings.backend.business

import com.onlygoodthings.backend.http.JsonBody
import com.fasterxml.jackson.databind.JsonNode
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import java.time.Duration
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

internal data class PaymentPreference(val id:String,val url:String)
internal data class ProviderPayment(val id:String,val orderId:String,val collector:String,val live:Boolean,
    val currency:String,val amount:Long,val refunded:Long,val status:String,val payer:String="")
internal interface PaymentProvider {
    val live:Boolean
    val collector:String
    val sandboxBuyerIds:Set<String> get()=emptySet()
    fun preference(order:String,amount:Long):PaymentPreference
    fun payment(id:String):ProviderPayment
    fun paymentIds(order:String):List<String> = emptyList()
}
internal class PaymentProviderFailure:RuntimeException("No se pudo confirmar la respuesta de Mercado Pago. Podés reintentar la misma operación.")
internal fun sandboxBuyerAllowlist(raw:String,live:Boolean):Set<String> {
    if(raw.isBlank())return emptySet()
    require(!live){"Los compradores de prueba solo se configuran en sandbox"}
    return raw.split(',').map{it.trim()}.also{ids ->require(ids.all{it.matches(Regex("[0-9]{1,30}"))}){"Identificador de comprador de prueba inválido"}}.toSet()
}
internal fun PaymentProvider.matchesEnvironment(payment:ProviderPayment):Boolean =
    payment.live==live || (!live && payment.payer.isNotBlank() && payment.payer in sandboxBuyerIds)
internal fun cents(value:String):Long = try {java.math.BigDecimal(value).movePointRight(2).longValueExact()}
    catch(_:Exception){throw PaymentProviderFailure()}

/** No credentials, card data, or provider response bodies enter logs or browser responses. */
internal class MercadoPago(private val token:String,override val collector:String,override val live:Boolean,
    private val publicBase:String,override val sandboxBuyerIds:Set<String> = emptySet()):PaymentProvider {
    init {require(!live || sandboxBuyerIds.isEmpty());require(sandboxBuyerIds.all{it.matches(Regex("[0-9]{1,30}"))})}
    private val client=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8)).followRedirects(HttpClient.Redirect.NEVER).build()
    @Volatile private var verifiedUntil=0L
    val ready:Boolean get()=System.currentTimeMillis()<verifiedUntil
    private fun request(path:String,payload:Any?=null,requestId:String?=null):JsonNode {
        val builder=HttpRequest.newBuilder(URI("https://api.mercadopago.com$path"))
            .timeout(Duration.ofSeconds(15)).header("Authorization","Bearer $token")
        if(payload==null)builder.GET() else builder.header("Content-Type","application/json")
            .header("X-Idempotency-Key",requireNotNull(requestId))
            .POST(HttpRequest.BodyPublishers.ofString(JsonBody.objectMapper.writeValueAsString(payload)))
        return try {
            val response=client.send(builder.build(),HttpResponse.BodyHandlers.ofString())
            if(response.statusCode() !in 200..299 || response.body().length>2_000_000)throw PaymentProviderFailure()
            JsonBody.objectMapper.readTree(response.body())
        }catch(_:Exception){throw PaymentProviderFailure()}
    }
    @Synchronized fun verifyAccount(){
        if(ready)return
        val account=request("/users/me")
        if(account.path("id").asText()!=collector)throw PaymentProviderFailure()
        val test=account.path("tags").any{it.asText()=="test_user"}
        if(test==live)throw PaymentProviderFailure()
        verifiedUntil=System.currentTimeMillis()+300_000
    }
    override fun preference(order:String,amount:Long):PaymentPreference {
        verifyAccount()
        val result=request("/checkout/preferences",mapOf(
            "items" to listOf(mapOf("id" to order,"title" to "Saldo de campañas de Only Good Things","quantity" to 1,
                "currency_id" to "ARS","unit_price" to java.math.BigDecimal.valueOf(amount,2))),
            "external_reference" to order,
            "notification_url" to "$publicBase/api/v1/payments/mercadopago/webhook",
            "back_urls" to mapOf("success" to "$publicBase/crm/empresas/?payment=return","pending" to "$publicBase/crm/empresas/?payment=pending","failure" to "$publicBase/crm/empresas/?payment=failure"),
            "auto_return" to "approved","expires" to true,
            "expiration_date_to" to java.time.OffsetDateTime.now(java.time.ZoneOffset.UTC).plusHours(24).toString()
        ),order)
        // Test credentials still require a test buyer/card. Reconciliation also
        // verifies the collector and the explicitly configured sandbox buyers.
        val url=result.path(if(live)"init_point" else "sandbox_init_point").asText()
        val uri=runCatching{URI(url)}.getOrNull() ?:throw PaymentProviderFailure()
        if(uri.scheme!="https" || uri.host !in setOf("www.mercadopago.com.ar","sandbox.mercadopago.com.ar") || uri.userInfo!=null || uri.port !in setOf(-1,443))throw PaymentProviderFailure()
        val id=result.path("id").asText();if(id.isBlank())throw PaymentProviderFailure()
        return PaymentPreference(id,url)
    }
    override fun payment(id:String):ProviderPayment {
        verifyAccount()
        require(id.matches(Regex("[0-9]{1,30}")))
        val r=request("/v1/payments/$id")
        return ProviderPayment(r.path("id").asText(),r.path("external_reference").asText(),r.path("collector_id").asText(),
            r.path("live_mode").takeUnless{it.isMissingNode}?.asBoolean() ?:throw PaymentProviderFailure(),
            r.path("currency_id").asText(),cents(r.path("transaction_amount").asText()),
            cents(r.path("transaction_amount_refunded").asText("0")),r.path("status").asText(),r.path("payer").path("id").asText(""))
    }
    override fun paymentIds(order:String):List<String> {
        verifyAccount()
        val key=java.util.UUID.fromString(order).toString()
        return request("/v1/payments/search?external_reference=$key&sort=date_created&criteria=desc&limit=50")
            .path("results").map{it.path("id").asText()}.filter{it.matches(Regex("[0-9]{1,30}"))}.distinct()
    }
}

/** Signed notifications only enqueue an ID. Authoritative payment data is fetched server to server. */
internal fun validPaymentSignature(id:String,requestId:String?,signature:String?,secret:String,now:Long):Boolean {
    if(!id.matches(Regex("[0-9]{1,30}")) || requestId.isNullOrBlank() || !requestId.matches(Regex("[A-Za-z0-9-]{1,120}")) || secret.isBlank())return false
    val fields=signature.orEmpty().split(',').map{it.trim().split('=',limit=2)}
    if(fields.any{it.size!=2} || fields.groupBy{it[0]}.any{it.value.size>1})return false
    val values=fields.associate{it[0] to it[1]};val ts=values["ts"] ?:return false
    val stamp=ts.toLongOrNull() ?:return false
    val seconds=if(stamp>100_000_000_000L)stamp/1000 else stamp
    if(seconds<now-300 || seconds>now+60)return false
    val actual=values["v1"]?.takeIf{it.matches(Regex("[a-fA-F0-9]{64}"))} ?:return false
    val mac=Mac.getInstance("HmacSHA256").apply{init(SecretKeySpec(secret.toByteArray(),"HmacSHA256"))}
    val expected=mac.doFinal("id:$id;request-id:$requestId;ts:$ts;".toByteArray())
    val received=actual.chunked(2).map{it.toInt(16).toByte()}.toByteArray()
    return MessageDigest.isEqual(expected,received)
}

internal data class MercadoPagoConfiguration(val provider:MercadoPago,val webhookSecret:String) {
    companion object {
        fun fromEnv():MercadoPagoConfiguration? {
            val mode=System.getenv("OGT_MP_MODE").orEmpty()
            if(mode.isBlank() || mode=="disabled")return null
            require(mode in setOf("sandbox","live")){"Modo de Mercado Pago inválido"}
            require(mode!="live" || System.getenv("OGT_MP_LIVE_PAYMENTS")=="true"){"Los cobros reales requieren habilitación explícita"}
            fun secret(name:String):String {
                val path=Path.of(System.getenv(name) ?:error("Falta el archivo privado $name"))
                require(Files.isRegularFile(path) && Files.getPosixFilePermissions(path).none{it.name.startsWith("GROUP_") || it.name.startsWith("OTHERS_")}){"Archivo privado con permisos demasiado amplios"}
                return Files.readString(path).trim().also{require(it.isNotBlank() && !it.contains('\n')){"Credencial privada inválida"}}
            }
            val collector=System.getenv("OGT_MP_COLLECTOR_ID").orEmpty().also{require(it.matches(Regex("[0-9]{1,30}"))){"Falta la cuenta receptora de Mercado Pago"}}
            val base=(System.getenv("OGT_MP_PUBLIC_BASE")?.takeIf{it.isNotBlank()}?:System.getenv("OGT_PUBLIC_BASE")).orEmpty().trimEnd('/')
            val uri=URI(base);require(uri.scheme=="https" && uri.host!=null && uri.userInfo==null && uri.query==null && uri.fragment==null && uri.path.isNullOrEmpty()){"Mercado Pago necesita una dirección pública HTTPS sin ruta"}
            val buyers=sandboxBuyerAllowlist(System.getenv("OGT_MP_SANDBOX_BUYER_IDS").orEmpty(),mode=="live")
            val provider=MercadoPago(secret("OGT_MP_ACCESS_TOKEN_FILE"),collector,mode=="live",base,buyers)
            return MercadoPagoConfiguration(provider,secret("OGT_MP_WEBHOOK_SECRET_FILE"))
        }
    }
}
