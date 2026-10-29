package com.onlygoodthings.shared.domain

import io.ktor.http.Url

/** Credits do not grant a licence. Kept separate from sourceUrl, which drives feed classification. */
object ContentAttribution {
    const val MAX_URL_LENGTH = 2048
    const val NOTICE = "Al publicar declarás que el contenido es tuyo o que tenés los permisos necesarios. Citar la fuente no reemplaza la autorización."

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
