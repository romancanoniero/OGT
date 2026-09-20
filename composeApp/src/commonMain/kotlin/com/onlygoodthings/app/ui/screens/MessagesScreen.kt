package com.onlygoodthings.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.onlygoodthings.app.data.LocalOgtDb
import com.onlygoodthings.app.data.LocalOgtSession
import com.onlygoodthings.app.resources.Res
import com.onlygoodthings.app.resources.qs_form
import com.onlygoodthings.app.resources.qs_gps
import com.onlygoodthings.app.theme.OgtColors
import com.onlygoodthings.app.ui.components.OgtCard
import com.onlygoodthings.app.ui.components.OgtCaption
import com.onlygoodthings.app.ui.components.OgtPill
import com.onlygoodthings.app.ui.components.OgtPrimaryButton
import com.onlygoodthings.app.ui.components.OgtSectionTitle
import com.onlygoodthings.app.ui.components.OgtStitchIcon
import com.onlygoodthings.app.ui.components.OgtTopBar
import com.onlygoodthings.app.ui.components.ScreenColumn
import com.onlygoodthings.shared.data.local.OgtIds

@Composable
fun MessagesScreen(onOpenChat: (String) -> Unit, onBack: () -> Unit = {}) {
    val db = LocalOgtDb.current
    val me = LocalOgtSession.current.me()
    val highlight = db.inbox.firstOrNull { it.matchId == OgtIds.MatchCamila }
    val threads = db.inbox.filter { !it.group }
    val groups = db.inbox.filter { it.group }
    Column(Modifier.fillMaxSize().background(OgtColors.canvas).verticalScroll(rememberScrollState())) {
        OgtTopBar(title = "Mensajes", onBack = onBack)
        ScreenColumn {
            OgtSectionTitle("Mensajes y Grupos")
            OgtCaption("Comunidad activa · cerca de ${me.barrio}")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OgtPill("Privados")
                OgtPill("Grupos de comunidad")
            }
            if (highlight != null) {
                OgtCard {
                    OgtPill("Encuentro Hoy · 17:30 hs", OgtColors.sunset, OgtColors.sunsetText)
                    Text("${highlight.title} en Plaza Armenia", fontWeight = FontWeight.Bold)
                    OgtCaption("Punto de encuentro seguro junto a la pérgola central de aromáticas.")
                    OgtPrimaryButton("Ver detalles") { onOpenChat(highlight.matchId ?: OgtIds.MatchCamila) }
                }
            }
            OgtSectionTitle("Hilos recientes")
            threads.forEach { thread ->
                val adopt = thread.matchId?.let { db.adoptRequestOfMatch(it) }
                val sight = thread.matchId?.let { db.sightingOfMatch(it) }
                ThreadRow(
                    name = thread.title,
                    whenText = thread.timeLabel,
                    tag = thread.tag,
                    preview = thread.preview,
                    form = adopt != null,
                    sighting = sight != null,
                    onClick = thread.matchId?.let { id -> { onOpenChat(id) } },
                )
            }
            OgtSectionTitle("Canales de comunidad destacados")
            groups.forEach { channel ->
                OgtCard {
                    Text(channel.title, fontWeight = FontWeight.Bold)
                    OgtCaption(channel.tag)
                    Text(channel.preview, fontWeight = FontWeight.SemiBold)
                    OgtCaption(channel.timeLabel)
                }
            }
            OgtPrimaryButton("Abrir chat de trueque") { onOpenChat(OgtIds.MatchCamila) }
            Spacer(Modifier.height(80.dp))
        }
    }
}

@Composable
private fun ThreadRow(
    name: String,
    whenText: String,
    tag: String,
    preview: String,
    form: Boolean,
    sighting: Boolean = false,
    onClick: (() -> Unit)?,
) {
    OgtCard(Modifier.fillMaxWidth().then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (form || sighting) {
                Box(
                    Modifier.size(36.dp).clip(CircleShape).background(OgtColors.sand).border(1.dp, OgtColors.hairline, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    OgtStitchIcon(
                        if (sighting) Res.drawable.qs_gps else Res.drawable.qs_form,
                        if (sighting) "Avistaje" else "Formulario",
                        tint = OgtColors.secondary,
                    )
                }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(name, fontWeight = FontWeight.Bold, color = OgtColors.ink)
                    Text(whenText, color = OgtColors.muted)
                }
                OgtCaption(tag)
                Text(preview)
            }
        }
    }
}
