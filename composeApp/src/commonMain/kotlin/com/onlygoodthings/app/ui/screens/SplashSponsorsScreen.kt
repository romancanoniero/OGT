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
import com.onlygoodthings.app.ui.components.OgtPrimaryButton
import com.onlygoodthings.app.ui.components.OgtTopBar
import com.onlygoodthings.app.ui.components.PrefCategory
import com.onlygoodthings.app.ui.components.PrefDivider
import com.onlygoodthings.app.ui.components.PrefGroup
import com.onlygoodthings.app.ui.components.PrefLine

@Composable
fun SplashSponsorsScreen(onContinue: () -> Unit, onBack: (() -> Unit)? = null) {
    Column(Modifier.fillMaxSize().background(OgtColors.canvas)) {
        OgtTopBar(title = "Sponsors", onBack = onBack, hideOnScroll = false)
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
        OgtLogo(size = 96.dp)
        Spacer(Modifier.height(12.dp))
        Text("OnlyGoodThings", fontWeight = FontWeight.Bold, fontSize = 22.sp, color = OgtColors.ink)
        OgtCaption("La red donde hacer el bien transforma tu comunidad.")
        PrefCategory("Sponsors regionales")
        PrefGroup {
            PrefLine(
                title = "Banco Santander",
                body = "Puntos de reciclaje urbano y microcréditos para cooperativas.",
            )
            PrefDivider()
            PrefLine(
                title = "Cablevisión Flow",
                body = "Wi-Fi en plazas recuperadas y capacitaciones comunitarias.",
            )
        }
        OgtCaption("34 municipios adheridos.")
        Spacer(Modifier.height(12.dp))
        OgtPrimaryButton("Explorar iniciativas cercanas") { onContinue() }
        Spacer(Modifier.height(16.dp))
        }
    }
}
