package com.onlygoodthings.app.ui.screens

import io.ktor.http.encodeURLParameter

internal const val sponsorInvitationUrl = "https://onlygoodthings.lat/presentacion#sumar-sponsors"
internal const val sponsorInvitationText = "¿Conocés una empresa que quiera acompañar a la comunidad? Compartile Only Good Things para que conozca el proyecto y se contacte para sumarse como sponsor: $sponsorInvitationUrl"
internal fun whatsappInvitationUri(text: String) = "https://wa.me/?text=${text.encodeURLParameter()}"
internal fun emailInvitationUri(subject: String, text: String) =
    "mailto:?subject=${subject.encodeURLParameter()}&body=${text.encodeURLParameter()}"
