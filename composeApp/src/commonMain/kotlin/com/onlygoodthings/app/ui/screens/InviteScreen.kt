package com.onlygoodthings.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.onlygoodthings.app.data.LocalOgtDb
import com.onlygoodthings.app.data.LocalOgtSession
import com.onlygoodthings.app.platform.sharePlainText
import com.onlygoodthings.app.theme.OgtColors
import com.onlygoodthings.app.ui.components.OgtCaption
import com.onlygoodthings.app.ui.components.OgtPrimaryButton
import com.onlygoodthings.app.ui.components.OgtTopBar
import com.onlygoodthings.app.ui.components.PrefCategory
import com.onlygoodthings.app.ui.components.PrefDivider
import com.onlygoodthings.app.ui.components.PrefGroup
import com.onlygoodthings.app.ui.components.PrefLine
import com.onlygoodthings.app.ui.components.ScreenColumn

@Composable
fun InviteScreen(onBack: () -> Unit = {}) {
    val db = LocalOgtDb.current
    val me = LocalOgtSession.current.me()
    val link = "https://onlygoodthings.lat/join/${me.firebaseUid.removePrefix("dev-user-")}"
    Column(Modifier.fillMaxSize().background(OgtColors.canvas).verticalScroll(rememberScrollState())) {
        OgtTopBar(title = "Invitar", onBack = onBack)
        ScreenColumn {
            OgtCaption("Invitá a tus amigos a compartir buenas acciones y ayudar a la comunidad.")
            OgtCaption(link.removePrefix("https://"))
            com.onlygoodthings.app.ui.components.InvitationShareButtons(
                link, "Sumate a Only Good Things",
                "Sumate a Only Good Things con ${me.displayName}: $link",
            )
            PrefCategory("Contactos de confianza")
            PrefGroup {
                db.inviteContacts.forEachIndexed { index, contact ->
                    if (index > 0) PrefDivider()
                    PrefLine(title = contact.name, body = contact.detail)
                }
            }
            OgtCaption("Cada invitación es personal. No mandamos correos masivos.")
            Spacer(Modifier.height(24.dp))
        }
    }
}
