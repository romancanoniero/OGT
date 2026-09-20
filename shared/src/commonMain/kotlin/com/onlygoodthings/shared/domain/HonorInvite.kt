package com.onlygoodthings.shared.domain

import kotlinx.serialization.Serializable

/** Canal por el que se envía la mención de honor. */
@Serializable
enum class HonorChannel {
    WHATSAPP,
    SMS,
    EMAIL,
}

@Serializable
enum class HonorStatus {
    PENDING,
    CLAIMED,
    DECLINED,
}

/** Dominio contratado para invitaciones y Universal Links. */
const val OgtPublicWebHost = "onlygoodthings.lat"

fun honorClaimLink(token: String): String = "https://$OgtPublicWebHost/h/$token"

fun honorAppLink(token: String): String = "ogt://h/$token"

fun honorShareText(issuerName: String, givenName: String, token: String): String =
    "$issuerName contó algo bueno que hiciste, $givenName. " +
        "Si eras vos, entrá y reivindicá el crédito:\n${honorClaimLink(token)}"

@Serializable
data class HonorMentionView(
    val id: String,
    val givenName: String,
    val issuerName: String? = null,
    val status: HonorStatus,
    val claimToken: String,
    val claimedUserId: String? = null,
    val postId: String? = null,
) {
    val pending: Boolean get() = status == HonorStatus.PENDING
}

fun normalizeHonorContact(channel: HonorChannel, raw: String): String {
    val trimmed = raw.trim()
    return when (channel) {
        HonorChannel.EMAIL -> trimmed.lowercase()
        HonorChannel.SMS -> trimmed.filter { it.isDigit() }
        HonorChannel.WHATSAPP -> {
            val digits = trimmed.filter { it.isDigit() }
            if (digits.length >= 8) digits else HonorWhatsAppPicker
        }
    }
}

fun honorContactOk(channel: HonorChannel, raw: String): Boolean {
    val contact = normalizeHonorContact(channel, raw)
    return when (channel) {
        HonorChannel.EMAIL -> contact.contains('@') && contact.contains('.') && contact.length >= 6
        HonorChannel.SMS -> contact.length >= 8
        HonorChannel.WHATSAPP -> contact == HonorWhatsAppPicker || contact.length >= 8
    }
}

/** Destinatario elegido en la lista de chats de WhatsApp; el crédito se reivindica por el link. */
const val HonorWhatsAppPicker = "whatsapp"

/** Extrae el token de `onlygoodthings.app/h/{token}` o acepta el token pelado. */
fun parseHonorToken(uri: String): String? {
    val src = uri.trim()
    if (src.isEmpty()) return null
    val marker = "/h/"
    val at = src.indexOf(marker, ignoreCase = true)
    val raw = if (at >= 0) src.substring(at + marker.length) else src
    val token = raw.takeWhile { it.isLetterOrDigit() || it == '.' || it == '-' }
    return token.takeIf { it.length >= 6 && '/' !in it }
}

fun honorChannelLabel(channel: HonorChannel): String = when (channel) {
    HonorChannel.WHATSAPP -> "WhatsApp"
    HonorChannel.SMS -> "SMS"
    HonorChannel.EMAIL -> "Mail"
}
