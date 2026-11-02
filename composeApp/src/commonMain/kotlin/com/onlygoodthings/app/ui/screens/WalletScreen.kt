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
fun WalletScreen(onBack: () -> Unit = {}) {
    val auth=LocalAuth.current
    val session=LocalOgtSession.current
    val me=session.me()
    val state=rememberAccountData(me.id) { auth.account.wallet() }
    LaunchedEffect(me.id,state.value) { state.value?.let { session.applyCommunityPoints(it.points) } }
    Column(Modifier.fillMaxSize().background(OgtColors.canvas).verticalScroll(rememberScrollState())) {
        OgtTopBar(title="Billetera",onBack=onBack)
        ScreenColumn {
            AccountDataStatus(state)
            state.value?.let { wallet ->
                OgtCaption(wallet.levelLabel)
                Text("${wallet.points} pts",fontSize=40.sp,fontWeight=FontWeight.Bold,color=OgtColors.primary)
                val next=nextCommunityGoal(wallet.points)
                if(next==null)OgtCaption("Alcanzaste el nivel Héroe de la comunidad.")
                else {
                    OgtCaption("Próximo: ${communityLevelLabel(next)} · Faltan ${wallet.missing} pts")
                    androidx.compose.material3.LinearProgressIndicator(progress={ (wallet.points/next.toFloat()).coerceIn(0f,1f) },modifier=Modifier.fillMaxWidth())
                    OgtCaption("Meta $next pts")
                }
                PrefCategory("Actividad confirmada")
                if(wallet.activity.isEmpty())OgtCaption("Todavía no hay acciones confirmadas con puntos.")
                else PrefGroup {
                    wallet.activity.forEachIndexed { index,entry ->
                        if(index>0)PrefDivider()
                        PrefLine(entry.title,body=relativeTimeLabel(entry.createdAtEpochMs,com.onlygoodthings.shared.realtime.currentEpochMs()),trailing="${if(entry.delta>=0)"+" else ""}${entry.delta} pts")
                    }
                }
            }
            CouponWallet()
            Spacer(Modifier.height(80.dp))
        }
    }
}
