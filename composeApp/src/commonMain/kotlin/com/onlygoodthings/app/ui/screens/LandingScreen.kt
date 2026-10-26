package com.onlygoodthings.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.onlygoodthings.app.theme.OgtColors
import com.onlygoodthings.app.theme.OgtLogo
import com.onlygoodthings.app.ui.components.GhostLink
import com.onlygoodthings.app.ui.components.OgtCaption
import com.onlygoodthings.app.ui.components.OgtPill
import com.onlygoodthings.app.ui.components.OgtPrimaryButton

/** Presentación para visitantes sin sesión; no es la pantalla de arranque. */
@Composable
fun LandingScreen(onContinue: () -> Unit, onLogin: () -> Unit) {
    Column(
        Modifier.fillMaxSize().background(OgtColors.canvas)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 22.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(15.dp),
    ) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            OgtPill("Tu comunidad en vivo")
            OgtPill("Buenas noticias", OgtColors.sunset, OgtColors.sunsetText)
        }
        Spacer(Modifier.height(18.dp))
        OgtLogo(size = 132.dp, animated = false)
        Text(
            "Only Good Things",
            fontSize = 32.sp,
            lineHeight = 37.sp,
            fontWeight = FontWeight.ExtraBold,
            color = OgtColors.ink,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            "RED SOLIDARIA DE COMUNIDAD",
            color = OgtColors.primary,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.2.sp,
            textAlign = TextAlign.Center,
        )
        OgtCaption("Las buenas noticias, la ayuda mutua y los actos solidarios de tu comunidad.")
        AuthFeatureReel()
        OgtPrimaryButton("Descubrí Only Good Things") { onContinue() }
        GhostLink("Ya tengo una cuenta · Iniciar sesión") { onLogin() }
        Text(
            "Una comunidad donde las buenas acciones nos acercan.",
            color = OgtColors.muted,
            fontSize = 12.sp,
            textAlign = TextAlign.Center,
        )
    }
}
