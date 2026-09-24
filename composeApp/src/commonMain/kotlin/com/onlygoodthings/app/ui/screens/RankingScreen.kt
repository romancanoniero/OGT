package com.onlygoodthings.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.onlygoodthings.app.data.LocalOgtDb
import com.onlygoodthings.app.data.LocalOgtSession
import com.onlygoodthings.app.theme.OgtColors
import com.onlygoodthings.app.ui.components.OgtCaption
import com.onlygoodthings.app.ui.components.OgtTopBar
import com.onlygoodthings.app.ui.components.PrefCategory
import com.onlygoodthings.app.ui.components.PrefDivider
import com.onlygoodthings.app.ui.components.PrefGroup
import com.onlygoodthings.app.ui.components.PrefLine
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
        OgtTopBar(title = "Ranking", onBack = onBack)
        ScreenColumn {
            OgtCaption("El ranking reconoce impacto colectivo, no competencia.")
            PrefCategory("Cuadro de honor")
            PrefGroup {
                podium.forEachIndexed { index, neighbor ->
                    if (index > 0) PrefDivider()
                    PrefLine(
                        title = neighbor.displayName,
                        body = neighbor.honorTag,
                        trailing = "${index + 1}º · ${neighbor.communityPoints} pts",
                    )
                }
            }
            PrefCategory("Tu lugar")
            PrefGroup {
                PrefLine(
                    title = "#$myRank ${me.displayName}",
                    body = "${me.levelLabel} · ${me.honorTag}",
                    trailing = "${me.communityPoints} pts",
                )
            }
            PrefCategory("Quienes más aportan", "Top 4–10")
            PrefGroup {
                rest.forEachIndexed { index, neighbor ->
                    if (index > 0) PrefDivider()
                    PrefLine(
                        title = neighbor.displayName,
                        body = neighbor.honorTag,
                        trailing = "${neighbor.communityPoints} pts",
                    )
                }
            }
            Spacer(Modifier.height(80.dp))
        }
    }
}
