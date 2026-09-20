package com.onlygoodthings.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.onlygoodthings.app.theme.OgtColors
import com.onlygoodthings.app.theme.OgtLogo
import com.onlygoodthings.app.ui.components.OgtCaption
import com.onlygoodthings.app.ui.components.OgtCard
import com.onlygoodthings.app.ui.components.OgtPill
import com.onlygoodthings.app.ui.components.OgtPrimaryButton
import com.onlygoodthings.app.ui.components.OgtSectionTitle

@Composable
fun SplashSponsorsScreen(onContinue: () -> Unit) {
    Column(
        Modifier.fillMaxSize().background(OgtColors.canvas).verticalScroll(rememberScrollState()).padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        OgtLogo(size = 96.dp)
        Spacer(Modifier.height(12.dp))
        OgtSectionTitle("OnlyGoodThings")
        Text("La red comunitaria donde hacer el bien transforma tu comunidad, cerca o en la red.", fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
        Spacer(Modifier.height(8.dp))
        OgtPill("Geolocalización activa · CABA y Gran Buenos Aires")
        OgtCaption("210 acciones vecinales coordinándose hoy en tu zona")
        OgtCaption("Cargando mapa de impacto de la comunidad…")
        OgtSectionTitle("Impulsado por Sponsors Regionales")
        OgtCaption("Alianzas 2025")
        OgtCard {
            Text("Banco Santander", fontWeight = FontWeight.Bold)
            OgtCaption("Compromiso Verde y Finanzas Sustentables. Financiando puntos de reciclaje urbano y microcréditos para cooperativas de la comunidad en Buenos Aires.")
        }
        OgtCard {
            Text("Cablevisión Flow", fontWeight = FontWeight.Bold)
            OgtCaption("Conectividad e Inclusión Digital Vecinal. Puntos Wi-Fi libres en plazas recuperadas y capacitaciones comunitarias.")
        }
        OgtCaption("Red federal de 34 municipios adheridos a OnlyGoodThings")
        Spacer(Modifier.height(12.dp))
        OgtPrimaryButton("Explorar iniciativas cercanas") { onContinue() }
        Spacer(Modifier.height(16.dp))
    }
}
