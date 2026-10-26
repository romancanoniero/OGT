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
import com.onlygoodthings.app.ui.components.OgtCaption
import com.onlygoodthings.app.ui.components.OgtPill
import com.onlygoodthings.app.ui.components.OgtPrimaryButton
import com.onlygoodthings.app.ui.components.GhostLink

/** Landing solo para invitados, separada de la splash y del onboarding. */
@Composable
fun LandingScreen(onContinue: () -> Unit, onLogin: () -> Unit) {
    Column(
        Modifier.fillMaxSize().background(OgtColors.canvas).padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            OgtPill("Tu comunidad en vivo")
            OgtPill("Buenas noticias", OgtColors.sunset, OgtColors.sunsetText)
        }
        Spacer(Modifier.weight(1f))
        OgtLogo(size = 160.dp, animated = false)
        Spacer(Modifier.height(22.dp))
        Text(
            "Only Good Things",
            fontSize = 32.sp,
            fontWeight = FontWeight.ExtraBold,
            color = OgtColors.ink,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "RED SOLIDARIA DE COMUNIDAD",
            color = OgtColors.primary,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.2.sp,
        )
        Spacer(Modifier.height(8.dp))
        OgtCaption("Las buenas noticias, la ayuda mutua y los actos solidarios de tu comunidad.")
        Spacer(Modifier.height(20.dp))
        AuthFeatureReel()
        Spacer(Modifier.weight(1f))
        OgtPrimaryButton("Conocer la comunidad", onContinue)
        Spacer(Modifier.height(6.dp))
        GhostLink("Ya tengo cuenta · Iniciar sesión", onLogin)
    }
}
