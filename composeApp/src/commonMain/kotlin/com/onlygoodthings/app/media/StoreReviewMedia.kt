package com.onlygoodthings.app.media

import io.ktor.http.Url
import kotlinx.coroutines.flow.MutableStateFlow

/** Bearers are attached only to first-party review media, never map/CDN/credit URLs. */
internal object StoreReviewMedia {
    private class Context(val token: suspend () -> String?)
    private val context=MutableStateFlow<Context?>(null)
    fun configure(enabled: Boolean, token: suspend () -> String?) { context.value=if(enabled)Context(token) else null }
    fun clear() { context.value=null }

    suspend fun request(original: String): Pair<String,String?> {
        val snapshot=context.value ?: return original to null
        val uri=runCatching { Url(original) }.getOrNull() ?: return original to null
        if(uri.protocol.name!="https" || uri.host!="onlygoodthings.lat" || uri.port!=443 || uri.user!=null || uri.password!=null || !uri.encodedPath.startsWith("/media/"))return original to null
        val bearer=snapshot.token() ?: error("Iniciá sesión para ver este contenido")
        check(context.value===snapshot) { "La sesión cambió" }
        return "https://onlygoodthings.lat/store-review${uri.encodedPath}${uri.encodedQuery.takeIf(String::isNotBlank)?.let { "?$it" }.orEmpty()}" to bearer
    }
}
