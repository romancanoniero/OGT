package com.onlygoodthings.shared.domain

import io.ktor.http.Url

/** Credits do not grant a licence. Kept separate from sourceUrl, which drives feed classification. */
object ContentAttribution {
    const val MAX_URL_LENGTH = 2048
    const val NOTICE = "Al publicar declarás que el contenido es tuyo o que tenés los permisos necesarios. Citar la fuente no reemplaza la autorización."

    /** Editorial illustration anchors disclose synthetic images beside the media. */
    fun label(value: String?): String {
        val url=runCatching { normalize(value)?.let {Url(it)} }.getOrNull()
        val generated=url!=null && url.protocol.name=="https" && url.host=="onlygoodthings.lat" &&
            url.port==443 && url.encodedPath=="/terms" &&
            url.fragment.matches(Regex("ilustracion-[a-f0-9]{8}(?:-[a-f0-9]{4}){3}-[a-f0-9]{12}"))
        return if(generated) "Ilustración generada con IA · OGT" else "Fuente y créditos"
    }

    fun normalize(value: String?): String? {
        val text = value?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        require(text.length <= MAX_URL_LENGTH && text.none { it.isWhitespace() || it.isISOControl() || it == '\\' }) {
            "La fuente debe ser un enlace web de hasta 2048 caracteres"
        }
        val authority = text.substringAfter("://", "").substringBefore('/').substringBefore('?').substringBefore('#')
        val url = runCatching { Url(text) }.getOrNull()
        require(authority.isNotBlank() && url != null && text.substringBefore("://").lowercase() in setOf("http", "https") &&
            url.host.isNotBlank() && url.user == null && url.password == null) {
            "La fuente debe comenzar con https:// o http:// y no incluir credenciales"
        }
        return text
    }
}
