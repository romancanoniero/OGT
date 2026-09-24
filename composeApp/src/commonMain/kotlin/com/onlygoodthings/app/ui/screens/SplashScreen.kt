package com.onlygoodthings.app.ui.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.onlygoodthings.app.theme.OgtColors
import com.onlygoodthings.app.theme.OgtLogo
import com.onlygoodthings.app.ui.components.OgtCaption
import com.onlygoodthings.app.ui.components.OgtPill
import com.onlygoodthings.app.ui.components.OgtPrimaryButton
import kotlinx.coroutines.delay

@Composable
fun SplashScreen(onContinue: () -> Unit) {
    val progress = remember { Animatable(0.12f) }
    var status by remember { mutableStateOf("Conectando tu comunidad…") }
    var ready by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        status = "Conectando tu comunidad…"
        progress.animateTo(0.45f, tween(900, easing = FastOutSlowInEasing))
        status = "Sincronizando el mapa de la comunidad…"
        progress.animateTo(0.82f, tween(1000, easing = FastOutSlowInEasing))
        status = "¡Todo listo para sumar tu impacto!"
        progress.animateTo(1f, tween(700, easing = FastOutSlowInEasing))
        ready = true
        delay(450)
        onContinue()
    }

    Column(
        Modifier.fillMaxSize().background(OgtColors.canvas).padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            OgtPill("Tu comunidad en vivo")
            OgtPill("Buenas noticias", OgtColors.sunset, OgtColors.sunsetText)
        }
        Spacer(Modifier.weight(1f))
        OgtLogo(size = 168.dp, animated = true)
        Spacer(Modifier.height(20.dp))
        Text("RED SOLIDARIA DE COMUNIDAD", color = OgtColors.primary, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp)
        Text("OnlyGoodThings", fontSize = 32.sp, fontWeight = FontWeight.Bold, color = OgtColors.ink)
        OgtCaption("Las buenas noticias, la ayuda mutua y los actos solidarios de tu comunidad.")
        Spacer(Modifier.height(16.dp))
        AuthFeatureReel()
        Spacer(Modifier.weight(1f))
        Text(status, color = OgtColors.muted, fontSize = 13.sp)
        Box(
            Modifier.fillMaxWidth().padding(vertical = 12.dp).height(6.dp).clip(CircleShape).background(OgtColors.sand),
        ) {
            Box(
                Modifier.fillMaxWidth(progress.value.coerceIn(0.04f, 1f)).height(6.dp).clip(CircleShape)
                    .background(OgtColors.primary),
            )
        }
        OgtPrimaryButton(
            if (ready) "Entrar a la comunidad" else "Saltar y descubrir buenas acciones",
        ) { onContinue() }
        Spacer(Modifier.height(8.dp))
        Text("Red comunitaria positiva · Sin algoritmo invasivo", color = OgtColors.muted, fontSize = 12.sp)
    }
}
