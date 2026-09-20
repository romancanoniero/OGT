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
import com.onlygoodthings.app.ui.components.OgtCard
import com.onlygoodthings.app.ui.components.OgtPill
import com.onlygoodthings.app.ui.components.OgtPrimaryButton
import com.onlygoodthings.app.ui.components.OgtSectionTitle
import com.onlygoodthings.app.ui.components.OgtTopBar
import com.onlygoodthings.app.ui.components.ScreenColumn

@Composable
fun InviteScreen(onBack: () -> Unit = {}) {
    val db = LocalOgtDb.current
    val me = LocalOgtSession.current.me()
    Column(Modifier.fillMaxSize().background(OgtColors.canvas).verticalScroll(rememberScrollState())) {
        OgtTopBar(title = "Impacto compartido", onBack = onBack)
        ScreenColumn {
            OgtSectionTitle("Invita a tu comunidad")
            OgtCaption("Una red comunitaria crece con personas de buen corazón. Por cada persona que se sume, ambos reciben +100 puntos y una donación automática de un árbol.")
            OgtCard {
                Text("Tu bosque comunitario", fontWeight = FontWeight.Bold)
                Text("7 árboles donados · +700 pts", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = OgtColors.primary)
            }
            OgtCaption("Comunidad ${me.displayName}")
            OgtPill(me.inviteCode)
            Text("onlygoodthings.lat/join/${me.firebaseUid.removePrefix("dev-user-")}", color = OgtColors.muted, fontSize = 13.sp)
            OgtPrimaryButton("Copiar Enlace") {
                sharePlainText("Sumate a Only Good Things con ${me.displayName}: https://onlygoodthings.lat/join/${me.firebaseUid.removePrefix("dev-user-")}")
            }
            OgtSectionTitle("Compartir Rápido")
            OgtPrimaryButton("Compartir por WhatsApp") {
                sharePlainText("Sumate a Only Good Things con ${me.displayName}: https://onlygoodthings.lat/join/${me.firebaseUid.removePrefix("dev-user-")}")
            }
            OgtCaption("Telegram · SMS tradicional · Código QR de la comunidad")
            OgtSectionTitle("Contactos de Confianza")
            db.inviteContacts.forEach { contact ->
                OgtCard {
                    Text(contact.name, fontWeight = FontWeight.SemiBold)
                    OgtCaption(contact.detail)
                }
            }
            OgtCaption("No enviamos correos masivos ni spam. Cada invitación es un acto personal entre personas de la comunidad.")
            Spacer(Modifier.height(24.dp))
        }
    }
}
