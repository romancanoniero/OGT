package com.onlygoodthings.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import com.onlygoodthings.app.data.LocalOgtDb
import com.onlygoodthings.app.data.LocalOgtSession
import com.onlygoodthings.app.theme.OgtColors
import com.onlygoodthings.app.ui.components.OgtCard
import com.onlygoodthings.app.ui.components.OgtCaption
import com.onlygoodthings.app.ui.components.OgtPill
import com.onlygoodthings.app.ui.components.OgtPrimaryButton
import com.onlygoodthings.app.ui.components.OgtSectionTitle
import com.onlygoodthings.app.ui.components.OgtTopBar
import com.onlygoodthings.app.ui.components.ScreenColumn

@Composable
fun NotificationsScreen(
    onBack: () -> Unit = {},
    onOpenPost: (String) -> Unit = {},
) {
    val db = LocalOgtDb.current
    val me = LocalOgtSession.current.me()
    val inbox = db.inboxOf(me.id)
    val unread = inbox.count { !it.read }
    Column(Modifier.fillMaxSize().background(OgtColors.canvas).verticalScroll(rememberScrollState())) {
        OgtTopBar(title = "Notificaciones", onBack = onBack)
        ScreenColumn {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OgtPill("$unread nuevas", OgtColors.sunset, OgtColors.sunsetText)
                Box(Modifier.clickable { db.markInboxRead(me.id) }) {
                    OgtPill("Marcar leídas")
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OgtPill("Todas")
                OgtPill("Alertas Urgentes")
                OgtPill("Puntos y Karma")
                OgtPill("Mensajes y Trueque")
            }
            inbox.forEach { item ->
                val postId = item.postId
                OgtCard(
                    modifier = if (postId.isNullOrBlank()) Modifier
                    else Modifier.clickable {
                        db.markNoticeRead(item.id)
                        onOpenPost(postId)
                    },
                ) {
                    when {
                        item.kind == "MENTION" -> OgtPill("Te mencionaron", OgtColors.sand, OgtColors.ink)
                        item.urgent -> OgtPill("Alerta Extravío a 400 m", OgtColors.sunset, OgtColors.sunsetText)
                    }
                    Text(item.title, fontWeight = FontWeight.Bold, color = OgtColors.ink)
                    OgtCaption(item.body)
                    OgtCaption(item.timeLabel)
                    if (item.kind == "ALERT") {
                        OgtPrimaryButton("Vi a esta mascota") { }
                    }
                    if (item.kind == "MATCH") {
                        OgtPrimaryButton("Aceptar trueque") { }
                    }
                    if (item.kind == "MENTION" && !postId.isNullOrBlank()) {
                        OgtPrimaryButton("Ver la acción") {
                            db.markNoticeRead(item.id)
                            onOpenPost(postId)
                        }
                    }
                }
            }
            OgtSectionTitle("Geolocalización Comunitaria Activa")
            OgtCaption("Tus notificaciones de emergencia y trueque se basan en tu radio de comunidad configurado en 2 km alrededor de ${me.barrio}.")
            Spacer(Modifier.height(80.dp))
        }
    }
}
