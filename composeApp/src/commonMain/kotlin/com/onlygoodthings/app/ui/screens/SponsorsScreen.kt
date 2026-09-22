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
import com.onlygoodthings.app.theme.OgtColors
import com.onlygoodthings.app.ui.components.OgtCaption
import com.onlygoodthings.app.ui.components.OgtTopBar
import com.onlygoodthings.app.ui.components.PrefCategory
import com.onlygoodthings.app.ui.components.PrefDivider
import com.onlygoodthings.app.ui.components.PrefGroup
import com.onlygoodthings.app.ui.components.PrefLine
import com.onlygoodthings.app.ui.components.ScreenColumn

@Composable
fun SponsorsScreen(onBack: () -> Unit = {}) {
    val db = LocalOgtDb.current
    val me = LocalOgtSession.current.me()
    Column(Modifier.fillMaxSize().background(OgtColors.canvas)) {
        OgtTopBar(title = "Sponsors", onBack = onBack)
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        ScreenColumn {
            OgtCaption("Empresas que financian proyectos comunitarios y premian tu huella.")
            Text("${me.communityPoints} pts", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = OgtColors.primary)
            OgtCaption("${me.levelLabel} · Score verde")
            PrefCategory("Alianzas")
            PrefGroup {
                db.sponsors.forEachIndexed { index, offer ->
                    if (index > 0) PrefDivider()
                    PrefLine(
                        title = offer.company,
                        body = listOfNotNull(offer.title, offer.body).joinToString(" · "),
                        trailing = offer.badge,
                    )
                }
            }
            OgtCaption("El 100% de la publicidad financia causas. No se venden datos.")
            Spacer(Modifier.height(80.dp))
        }
        }
    }
}
