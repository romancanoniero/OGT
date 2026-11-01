package com.onlygoodthings.app.ui.components

import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.AnnotatedString
import com.onlygoodthings.app.platform.sharePlainText
import com.onlygoodthings.app.ui.screens.emailInvitationUri
import com.onlygoodthings.app.ui.screens.whatsappInvitationUri

@Composable
internal fun InvitationShareButtons(link: String, subject: String, text: String) {
    val uri = LocalUriHandler.current
    val clipboard = LocalClipboardManager.current
    var copied by remember(link) { mutableStateOf(false) }
    fun open(destination: String) {
        try { uri.openUri(destination) } catch (_: Exception) { sharePlainText(text) }
    }
    OgtPrimaryButton("Compartir por WhatsApp") { open(whatsappInvitationUri(text)) }
    OgtSecondaryButton("Enviar por correo") { open(emailInvitationUri(subject, text)) }
    OgtSecondaryButton(if (copied) "Enlace copiado" else "Copiar enlace") {
        clipboard.setText(AnnotatedString(link))
        copied = true
    }
}
