package com.onlygoodthings.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.unit.sp
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
fun WalletScreen(onBack: () -> Unit = {}) {
    val db = LocalOgtDb.current
    val me = LocalOgtSession.current.me()
    val nextGoal = 3000
    val missing = (nextGoal - me.communityPoints).coerceAtLeast(0)
    val progress = (me.communityPoints / nextGoal.toFloat()).coerceIn(0f, 1f)
    Column(Modifier.fillMaxSize().background(OgtColors.canvas).verticalScroll(rememberScrollState())) {
        OgtTopBar(title = "Puntos Recompensas", onBack = onBack)
        ScreenColumn {
            OgtPill(me.levelLabel)
            OgtCaption("Karma Vital")
            Text("Puntos de comunidad acumulados", color = OgtColors.muted, fontSize = 13.sp)
            Text("${formatPts(me.communityPoints)} PTS", fontSize = 40.sp, fontWeight = FontWeight.Bold, color = OgtColors.primary)
            Text("Próximo: Héroe de la comunidad · Faltan $missing pts", color = OgtColors.muted, fontSize = 13.sp)
            Box(Modifier.fillMaxWidth().height(8.dp).clip(CircleShape).background(OgtColors.sand)) {
                Box(Modifier.fillMaxWidth(progress).height(8.dp).clip(CircleShape).background(OgtColors.primary))
            }
            Text("2.000 pts · Meta: ${formatPts(nextGoal)} pts", color = OgtColors.muted, fontSize = 12.sp)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OgtPill("Transferir Gratitud")
                OgtPill("Canjear Beneficio")
                OgtPill("Historial Karma")
            }
            OgtSectionTitle("Comercios Aliados")
            OgtCaption("Recompensas por tus buenas acciones en la comunidad")
            db.rewards.forEach { RewardCard(it.badge, "${it.costPoints} pts", it.place, it.detail) }
            OgtSectionTitle("Actividad de Karma")
            OgtCaption("Actualizado al instante")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OgtPill("Todos")
                OgtPill("Ganados")
                OgtPill("Canjeados")
            }
            db.karma.filter { it.userId == me.id }.forEach { entry ->
                val sign = if (entry.delta >= 0) "+" else ""
                val suffix = if (entry.delta >= 0) "ganados" else "donados"
                KarmaRow(entry.title, "${entry.place}", "$sign${entry.delta} pts $suffix")
            }
            Spacer(Modifier.height(80.dp))
        }
    }
}

private fun formatPts(n: Int): String {
    val raw = n.toString()
    return if (raw.length <= 3) raw else raw.dropLast(3) + "." + raw.takeLast(3)
}

@Composable
private fun RewardCard(badge: String, cost: String, place: String, detail: String) {
    OgtCard {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            OgtPill(badge, OgtColors.sunset, OgtColors.sunsetText)
            Text(cost, fontWeight = FontWeight.Bold, color = OgtColors.primary)
        }
        Spacer(Modifier.height(8.dp))
        Text(place, fontWeight = FontWeight.SemiBold)
        OgtCaption(detail)
        Spacer(Modifier.height(8.dp))
        OgtPrimaryButton("Canjear cupón") { }
    }
}

@Composable
private fun KarmaRow(title: String, place: String, delta: String) {
    OgtCard {
        Text(title, fontWeight = FontWeight.SemiBold)
        OgtCaption(place)
        Text(delta, color = OgtColors.primary, fontWeight = FontWeight.Bold, fontSize = 13.sp)
    }
}
