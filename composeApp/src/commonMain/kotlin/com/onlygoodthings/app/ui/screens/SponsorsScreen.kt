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
import com.onlygoodthings.app.ui.components.OgtCard
import com.onlygoodthings.app.ui.components.OgtCaption
import com.onlygoodthings.app.ui.components.OgtPill
import com.onlygoodthings.app.ui.components.OgtPrimaryButton
import com.onlygoodthings.app.ui.components.OgtSectionTitle
import com.onlygoodthings.app.ui.components.OgtTopBar
import com.onlygoodthings.app.ui.components.ScreenColumn

@Composable
fun SponsorsScreen(onBack: () -> Unit = {}) {
    val db = LocalOgtDb.current
    val me = LocalOgtSession.current.me()
    Column(Modifier.fillMaxSize().background(OgtColors.canvas).verticalScroll(rememberScrollState())) {
        OgtTopBar(title = "Sponsors Verificados", onBack = onBack)
        ScreenColumn {
            OgtPill("Triple Impacto")
            OgtSectionTitle("Alianzas de Impacto y Beneficios")
            OgtCaption("Empresas que financian proyectos comunitarios y premian tu huella positiva.")
            Text("Tu Score Verde Actual", fontWeight = FontWeight.SemiBold)
            Text("${me.communityPoints} Puntos OGT · ${me.levelLabel}", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = OgtColors.primary)
            db.sponsors.forEach { offer ->
                OgtCard {
                    offer.badge?.let { OgtPill(it) }
                    Text(offer.company, fontWeight = FontWeight.Bold)
                    offer.title?.let { Text(it, fontWeight = FontWeight.SemiBold, color = OgtColors.primary) }
                    OgtCaption(offer.body)
                    offer.cta?.let { OgtPrimaryButton(it) { } }
                }
            }
            OgtCaption("El 100% de la publicidad financia causas sociales sin venta de datos personales.")
            Spacer(Modifier.height(80.dp))
        }
    }
}
