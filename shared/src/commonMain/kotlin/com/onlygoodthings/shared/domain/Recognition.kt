package com.onlygoodthings.shared.domain

import kotlinx.serialization.Serializable

@Serializable
data class RecognitionCategory(val id: String, val label: String, val icon: String = "", val active: Boolean = true, val sortOrder: Int = 0, val version: Int = 1)

@Serializable
data class RecognitionPostDraft(
    val body:String, val topic:String, val categoryId:String, val requestId:String,
    val media:List<PostMediaItem>, val honoreeName:String,
    val honoreeFullName:String=honoreeName,
    val protagonistUserId:String?=null, val participantUserIds:List<String> = emptyList(),
    val invitations:List<PostInvitationDraft> = emptyList(), val attributionUrl:String?=null,
    val publicationKind:String="ACTION",
)

/** Used by previews and older servers; an explicit empty server catalog stays empty. */
val legacyRecognitionCategories = listOf(
    RecognitionCategory("plantar", "Reforestación", "🌳", sortOrder = 10),
    RecognitionCategory("limpieza", "Limpieza", "🧹", sortOrder = 20),
    RecognitionCategory("reciclaje", "Reciclaje", "♻", sortOrder = 30),
    RecognitionCategory("vecinal", "Ayuda comunitaria", "🤝", sortOrder = 40),
    RecognitionCategory("animal", "Rescate Animal", "🐾", sortOrder = 50),
    RecognitionCategory("movilidad", "Movilidad Eco", "🚲", sortOrder = 60),
)

@Serializable
data class PostInvitationDraft(
    val recipientName: String,
    val purpose: String = "PARTICIPATION",
    val channel: String,
    val contact: String = "",
    val recipientUserId: String? = null,
)

@Serializable
data class PostInvitationView(
    val id: String, val postId: String, val recipientName: String, val issuerName: String,
    val purpose: String, val status: String, val expiresAtEpochMs: Long,
    val deliveryStatus: String = "PENDING", val channel: String = "", val maskedContact: String = "",
    val token: String? = null,
    val contact: String? = null,
)

fun invitationEmailUri(contact:String,text:String):String {
    fun encode(value:String)=value.encodeToByteArray().joinToString("") { b ->
        val n=b.toInt() and 255;val c=n.toChar()
        if(c in 'a'..'z'||c in 'A'..'Z'||c in '0'..'9'||c in "-._~")c.toString() else "%"+n.toString(16).uppercase().padStart(2,'0')
    }
    return "mailto:${encode(contact)}?subject=${encode("Te invitaron a Only Good Things")}&body=${encode(text)}"
}

fun normalizeInvitationContact(channel: String, raw: String): String = when (channel) {
    "EMAIL" -> raw.trim().lowercase().also {
        require(it.length <= 254 && Regex("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$").matches(it)) { "Ingresá un correo válido." }
    }
    "SMS" -> raw.trim().replace(Regex("[ ()-]"), "").also {
        require(Regex("^\\+[1-9][0-9]{7,14}$").matches(it)) { "Ingresá el teléfono con código de país, por ejemplo +541155551234." }
    }
    "IN_APP", "SHARE" -> ""
    else -> throw IllegalArgumentException("Canal de invitación inválido")
}

fun validateInvitationDraft(draft: PostInvitationDraft) {
    require(draft.recipientName.trim().length in 2..100) { "Ingresá el nombre completo de la persona invitada." }
    require(draft.purpose in setOf("RECOGNITION", "PARTICIPATION")) { "Motivo de invitación inválido" }
    normalizeInvitationContact(draft.channel, draft.contact)
    require(draft.channel != "IN_APP" || !draft.recipientUserId.isNullOrBlank()) { "Seleccioná una cuenta de OGT." }
}

fun normalizeNickname(raw: String): String = raw.trim().removePrefix("@").lowercase().also {
    require(Regex("^[a-z0-9][a-z0-9._]{2,29}$").matches(it)) { "El @nick debe tener entre 3 y 30 letras, números, puntos o guiones bajos." }
}

fun invitationLink(token: String): String = "https://$OgtPublicWebHost/i/$token"
fun parseInvitationToken(uri: String): String? {
    val path = uri.trim().substringBefore('?').substringBefore('#')
    val marker = when {
        path.startsWith("ogt://i/") -> "ogt://i/"
        path.startsWith("https://$OgtPublicWebHost/i/") -> "https://$OgtPublicWebHost/i/"
        path.startsWith("https://www.$OgtPublicWebHost/i/") -> "https://www.$OgtPublicWebHost/i/"
        path.startsWith("https://ogt-app-links.web.app/i/") -> "https://ogt-app-links.web.app/i/"
        else -> return null
    }
    return path.removePrefix(marker).takeIf { Regex("^[A-Za-z0-9_-]{32,64}$").matches(it) }
}

fun parseInvitationReferrer(raw:String):String? = raw.split('&').firstOrNull { it.startsWith("invitation=") }?.substringAfter('=')?.takeIf { Regex("^[A-Za-z0-9_-]{32,64}$").matches(it) }
