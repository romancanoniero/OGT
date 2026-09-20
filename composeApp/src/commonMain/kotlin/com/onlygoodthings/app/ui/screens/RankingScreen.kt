package com.onlygoodthings.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.onlygoodthings.app.data.LocalOgtDb
import com.onlygoodthings.app.data.LocalOgtSession
import com.onlygoodthings.app.theme.OgtColors
import com.onlygoodthings.app.ui.components.OgtCard
import com.onlygoodthings.app.ui.components.OgtCaption
import com.onlygoodthings.app.ui.components.OgtPill
import com.onlygoodthings.app.ui.components.OgtSectionTitle
import com.onlygoodthings.app.ui.components.OgtTopBar
import com.onlygoodthings.app.ui.components.ScreenColumn

@Composable
fun RankingScreen(onBack: () -> Unit = {}) {
    val db = LocalOgtDb.current
    val me = LocalOgtSession.current.me()
    val board = db.ranking().filter { it.barrio.contains("Palermo") || it.id == me.id }
    val podium = board.take(3)
    val rest = board.drop(3).take(7)
    val myRank = db.rankOf(me.id)
    Column(Modifier.fillMaxSize().background(OgtColors.canvas).verticalScroll(rememberScrollState())) {
        OgtTopBar(title = "Puntos Recompensas", onBack = onBack)
        ScreenColumn {
            OgtPill("¡Medalla de Gratitud enviada con éxito!")
            OgtSectionTitle("Cuadro de honor de la comunidad")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OgtPill("Este Mes")
                OgtPill("Histórico")
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OgtPill("Cerca (${me.barrio.substringBefore(" ")})")
                OgtPill("Comunidad")
                OgtPill("Toda la red")
            }
            podium.forEachIndexed { index, neighbor ->
                PodiumCard("${index + 1}º", neighbor.displayName, "${neighbor.communityPoints} pts", neighbor.honorTag)
            }
            OgtCard {
                Text("#$myRank Tu posición actual", fontWeight = FontWeight.Bold)
                Text("${me.communityPoints} pts · ${me.levelLabel}", color = OgtColors.primary, fontWeight = FontWeight.SemiBold)
                OgtCaption("${me.honorTag} · +3 puestos hoy")
            }
            OgtSectionTitle("Quienes más aportan")
            OgtCaption("Top 4–10 · Actualizado hace 12 min")
            rest.forEach { neighbor ->
                NeighborRow(neighbor.displayName, "${neighbor.communityPoints} pts", neighbor.honorTag)
            }
            OgtCaption("El ranking reconoce la generosidad y el impacto colectivo, sin fines de lucro ni competencia desleal. Cada punto representa una mano tendida en la comunidad, cerca o en la red.")
            Spacer(Modifier.height(80.dp))
        }
    }
}

@Composable
private fun PodiumCard(place: String, name: String, pts: String, tag: String) {
    OgtCard {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            OgtPill(place, OgtColors.sunset, OgtColors.sunsetText)
            Text(pts, fontWeight = FontWeight.Bold, color = OgtColors.primary, fontSize = 18.sp)
        }
        Text(name, fontWeight = FontWeight.Bold, fontSize = 20.sp)
        OgtCaption(tag)
    }
}

@Composable
private fun NeighborRow(name: String, pts: String, tag: String) {
    OgtCard {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(name, fontWeight = FontWeight.SemiBold)
            Text(pts, color = OgtColors.primary, fontWeight = FontWeight.Bold)
        }
        OgtCaption(tag)
    }
}
