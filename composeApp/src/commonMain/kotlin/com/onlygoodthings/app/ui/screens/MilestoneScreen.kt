package com.onlygoodthings.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import com.onlygoodthings.app.data.LocalOgtSession
import com.onlygoodthings.app.theme.OgtColors
import com.onlygoodthings.app.ui.components.OgtCaption
import com.onlygoodthings.app.ui.components.OgtCard
import com.onlygoodthings.app.ui.components.OgtPill
import com.onlygoodthings.app.ui.components.OgtPrimaryButton
import com.onlygoodthings.app.ui.components.OgtSecondaryButton
import com.onlygoodthings.app.ui.components.OgtSectionTitle

@Composable
fun MilestoneScreen(onDone: () -> Unit) {
    val me = LocalOgtSession.current.me()
    Column(
        Modifier.fillMaxSize().background(OgtColors.canvas).verticalScroll(rememberScrollState()).padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        OgtPill("¡Nuevo hito de comunidad desbloqueado!")
        Text("NIVEL 4 · GUARDIÁN", color = OgtColors.secondary, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
        OgtSectionTitle("¡Felicitaciones, ${me.displayName.substringBefore(" ")}!")
        OgtCaption("Tu dedicación y generosidad están ayudando a la comunidad — cerca de ${me.barrio} y en la red — a ser más solidaria, verde y conectada.")
        OgtCard {
            Text("Impacto Comunitario", fontWeight = FontWeight.Bold)
            Text("${me.communityPoints} Puntos Karma", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = OgtColors.primary)
            OgtCaption("Top 3% de la comunidad")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OgtPill("42 acciones")
                OgtPill("15 parkings")
                OgtPill("−18 kg CO₂")
            }
            OgtCaption(me.honorTag)
        }
        OgtSectionTitle("Privilegios Nivel 4")
        Privilege("15% Descuento Exclusivo", "Válido en ferias agroecológicas y huertas comunitarias asociadas.")
        Privilege("Voto Doble Comunitario", "Tu voz cuenta el doble en la asignación de fondos para mejoras urbanas.")
        Privilege("Insignia Dorada en Avatar", "Distintivo público de Guardián en las publicaciones de toda la comunidad.")
        OgtPrimaryButton("Compartir Hito en Stories / WhatsApp") { onDone() }
        OgtSecondaryButton("Ver mi Billetera y Recompensas") { onDone() }
        OgtSecondaryButton("Continuar al feed") { onDone() }
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun Privilege(title: String, body: String) {
    OgtCard {
        Text(title, fontWeight = FontWeight.Bold)
        OgtCaption(body)
    }
}
