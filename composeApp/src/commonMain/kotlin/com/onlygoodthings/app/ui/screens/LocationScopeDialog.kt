package com.onlygoodthings.app.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.onlygoodthings.app.resources.Res
import com.onlygoodthings.app.resources.gps_scope_allegory
import com.onlygoodthings.app.theme.OgtColors
import com.onlygoodthings.app.ui.components.GhostLink
import com.onlygoodthings.app.ui.components.OgtCaption
import com.onlygoodthings.app.ui.components.OgtPill
import com.onlygoodthings.app.ui.components.OgtPrimaryButton
import com.onlygoodthings.app.ui.components.OgtSectionTitle
import com.onlygoodthings.shared.domain.LocationScope
import org.jetbrains.compose.resources.painterResource

/**
 * Diálogo Quiet Studio: mientras uses la app vs siempre.
 * Ilustración Stitch `gps_scope_allegory`.
 */
@Composable
fun LocationScopeDialog(
    onConfirm: (LocationScope) -> Unit,
    onSkip: () -> Unit,
) {
    var scope by remember { mutableStateOf(LocationScope.WHILE_USING) }
    Column(
        Modifier
            .fillMaxSize()
            .background(OgtColors.canvas)
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        OgtPill("Tu ubicación, solo para el bien")
        Image(
            painter = painterResource(Res.drawable.gps_scope_allegory),
            contentDescription = "Manos cuidando un pin que se vuelve auto y comunidad",
            modifier = Modifier
                .fillMaxWidth()
                .height(220.dp)
                .clip(RoundedCornerShape(28.dp)),
            contentScale = ContentScale.Crop,
        )
        OgtSectionTitle("¿Cómo usamos tu GPS?")
        OgtCaption("Pedimos el GPS real del teléfono para el radar de parking y para que encuentres tu auto. No vendemos rutas.")
        ScopeCard(
            selected = scope == LocationScope.WHILE_USING,
            title = "Mientras uses la app",
            body = "Sincroniza cesiones y distancias solo con Parking o el mapa abiertos. Al salir, el rastreo se pausa.",
            tag = "Recomendado",
        ) { scope = LocationScope.WHILE_USING }
        ScopeCard(
            selected = scope == LocationScope.ALWAYS,
            title = "Siempre",
            body = "Detecta si estacionaste y te recuerda ceder el lugar aunque cambies de app. Sigue siendo efímero: no guardamos el historial.",
            tag = "Para no olvidar el auto",
            sunset = true,
        ) { scope = LocationScope.ALWAYS }
        OgtPrimaryButton("Continuar") { onConfirm(scope) }
        GhostLink("Ahora no") { onSkip() }
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun ScopeCard(
    selected: Boolean,
    title: String,
    body: String,
    tag: String,
    sunset: Boolean = false,
    onClick: () -> Unit,
) {
    val border = if (selected) OgtColors.primary else OgtColors.stone
    val fill = if (selected) OgtColors.mint else OgtColors.surface
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(fill)
            .border(1.5.dp, border, RoundedCornerShape(24.dp))
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(
                Modifier
                    .size(18.dp)
                    .clip(RoundedCornerShape(99.dp))
                    .background(if (selected) OgtColors.primary else OgtColors.stone),
            )
            Text(title, fontWeight = FontWeight.Bold, fontSize = 17.sp, color = OgtColors.ink)
        }
        OgtPill(
            tag,
            if (sunset) OgtColors.sunset else OgtColors.mint,
            if (sunset) OgtColors.sunsetText else OgtColors.mintText,
        )
        OgtCaption(body)
    }
}
