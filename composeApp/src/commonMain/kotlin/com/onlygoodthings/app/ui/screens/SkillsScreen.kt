package com.onlygoodthings.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
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
import com.onlygoodthings.app.i18n.LocalOgtCopy
import com.onlygoodthings.app.theme.OgtColors
import com.onlygoodthings.app.ui.components.OgtCard
import com.onlygoodthings.app.ui.components.OgtCaption
import com.onlygoodthings.app.ui.components.OgtPill
import com.onlygoodthings.app.ui.components.OgtPrimaryButton
import com.onlygoodthings.app.ui.components.OgtSectionTitle
import com.onlygoodthings.app.ui.components.OgtTopBar
import com.onlygoodthings.app.ui.components.ScreenColumn
import com.onlygoodthings.shared.data.local.LocalHelpExchange

@Composable
fun SkillsScreen(onPropose: () -> Unit, onChat: (String) -> Unit) {
    val db = LocalOgtDb.current
    val me = LocalOgtSession.current.me()
    val copy = LocalOgtCopy.current
    val mine = db.helpExchangesOf(me.id)
    val nearby = db.helpExchanges.filter { it.authorUserId != me.id }
    val matches = db.matches.filter { it.requesterId == me.id || it.providerId == me.id }
    val chatByAuthor = matches.associate { match ->
        val peer = if (match.providerId == me.id) match.requesterId else match.providerId
        peer to match.id
    }
    Column(Modifier.fillMaxSize().background(OgtColors.canvas).verticalScroll(rememberScrollState())) {
        OgtTopBar(title = copy.tabSkills)
        ScreenColumn {
            OgtCaption("Pedí una tarea o un saber y ofrecé lo tuyo: una clase, un arreglo, un trámite. El día y la hora se hablan en privado.")
            OgtPrimaryButton("Publicar un intercambio") { onPropose() }
            OgtSectionTitle("Tu aviso")
            OgtCaption("Necesito")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                db.skillsOf(me.id, offered = false).forEach { OgtPill(it.label) }
            }
            OgtCaption("Doy a cambio")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                db.skillsOf(me.id, offered = true).forEach { OgtPill(it.label) }
            }
            mine.forEach { listing ->
                HelpListingCard(listing, authorName = "Vos", onChat = null)
            }
            OgtSectionTitle("Cerca tuyo")
            nearby.forEach { listing ->
                val author = db.user(listing.authorUserId)
                HelpListingCard(
                    listing = listing,
                    authorName = "${author.displayName} · ${author.barrio}",
                    onChat = chatByAuthor[listing.authorUserId]?.let { id -> { onChat(id) } },
                )
            }
            val listedAuthors = nearby.map { it.authorUserId }.toSet()
            matches.forEach { match ->
                val peerId = if (match.providerId == me.id) match.requesterId else match.providerId
                if (peerId in listedAuthors) return@forEach
                val peer = db.user(peerId)
                OgtCard {
                    Text("${peer.displayName} · ${match.distanceLabel}", fontWeight = FontWeight.Bold)
                    OgtCaption(peer.honorTag)
                    Text("Necesita: ${match.requestedLabel}", fontWeight = FontWeight.SemiBold)
                    OgtCaption("Da: ${match.offeredLabel}")
                    match.quote?.let { OgtCaption(it) }
                    OgtPrimaryButton("Escribirle a ${peer.displayName.substringBefore(" ")}") { onChat(match.id) }
                }
            }
            Spacer(Modifier.height(80.dp))
        }
    }
}

@Composable
private fun HelpListingCard(
    listing: LocalHelpExchange,
    authorName: String,
    onChat: (() -> Unit)?,
) {
    OgtCard {
        Text(authorName, fontWeight = FontWeight.Bold)
        Text("Necesita: ${listing.need}", fontWeight = FontWeight.SemiBold)
        OgtCaption("Da: ${listing.give}")
        listing.note?.let { OgtCaption(it) }
        if (onChat != null) {
            OgtPrimaryButton("Escribirle") { onChat() }
        }
    }
}
