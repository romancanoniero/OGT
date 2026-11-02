package com.onlygoodthings.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.onlygoodthings.app.data.LocalAuth
import com.onlygoodthings.app.data.LocalOgtSession
import com.onlygoodthings.app.theme.OgtColors
import com.onlygoodthings.app.ui.components.*
import com.onlygoodthings.shared.domain.*

@Composable
fun RankingScreen(onBack: () -> Unit = {}) {
    val auth=LocalAuth.current
    val me=LocalOgtSession.current.me()
    val session=LocalOgtSession.current
    val state=rememberAccountData(me.id) { auth.account.wallet() }
    LaunchedEffect(me.id,state.value) { state.value?.let { session.applyCommunityPoints(it.points) } }
    Column(Modifier.fillMaxSize().background(OgtColors.canvas).verticalScroll(rememberScrollState())) {
        OgtTopBar(title="Ranking",onBack=onBack)
        ScreenColumn {
            OgtCaption("El ranking reconoce impacto colectivo. Muestra los perfiles disponibles para vos, ordenados por sus puntos confirmados.")
            AccountDataStatus(state)
            state.value?.let { summary ->
                PrefCategory("Tu lugar")
                PrefGroup { PrefLine(summary.myRank?.let { "#$it ${me.displayName}" } ?: me.displayName,body=summary.levelLabel,trailing="${summary.points} pts") }
                PrefCategory("Cuadro de honor")
                if(summary.ranking.isEmpty())OgtCaption("Todavía no hay perfiles en el ranking.")
                else PrefGroup {
                    summary.ranking.forEachIndexed { index,neighbor ->
                        if(index>0)PrefDivider()
                        PrefLine("${index+1}º · ${neighbor.displayName}",body=neighbor.levelLabel,trailing="${neighbor.communityPoints} pts")
                    }
                }
            }
            Spacer(Modifier.height(80.dp))
        }
    }
}
