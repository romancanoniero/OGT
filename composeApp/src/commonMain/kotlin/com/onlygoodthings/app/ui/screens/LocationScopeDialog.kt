package com.onlygoodthings.app.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import com.onlygoodthings.app.data.LocalOgtSession
import com.onlygoodthings.app.resources.Res
import com.onlygoodthings.app.resources.gps_scope_allegory
import com.onlygoodthings.app.theme.OgtColors
import com.onlygoodthings.app.ui.components.GhostLink
import com.onlygoodthings.app.ui.components.OgtCaption
import com.onlygoodthings.app.ui.components.OgtPrimaryButton
import com.onlygoodthings.app.ui.components.OgtTopBar
import com.onlygoodthings.app.ui.components.PrefCategory
import com.onlygoodthings.app.ui.components.PrefDivider
import com.onlygoodthings.app.ui.components.PrefGroup
import com.onlygoodthings.app.ui.components.PrefRadio
import com.onlygoodthings.shared.domain.LocationScope
import org.jetbrains.compose.resources.painterResource

/**
 * Alcance del GPS: misma lista que Ajustes, no cards de opción.
 */
@Composable
fun LocationScopeDialog(
    onConfirm: (LocationScope) -> Unit,
    onSkip: () -> Unit,
    onBack: (() -> Unit)? = null,
) {
    val session = LocalOgtSession.current
    var scope by remember { mutableStateOf(session.locationScope) }
    Column(
        Modifier
            .fillMaxSize()
            .background(OgtColors.canvas)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        OgtTopBar(title = "GPS", onBack = onBack, hideOnScroll = false)
        Column(
            Modifier.padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Image(
                painter = painterResource(Res.drawable.gps_scope_allegory),
                contentDescription = "Manos cuidando un pin que se vuelve auto y comunidad",
                modifier = Modifier
                    .fillMaxWidth()
                    .height(220.dp)
                    .clip(RoundedCornerShape(28.dp)),
                contentScale = ContentScale.Crop,
            )
            OgtCaption("Pedimos el GPS real para parking y para que encuentres tu auto. No vendemos rutas.")
            PrefCategory("Alcance")
            PrefGroup {
                PrefRadio(
                    title = "Mientras uses la app",
                    body = "Sincroniza cesiones y distancias con Parking o el mapa abiertos.",
                    selected = scope == LocationScope.WHILE_USING,
                ) { scope = LocationScope.WHILE_USING }
                PrefDivider()
                PrefRadio(
                    title = "Siempre",
                    body = "Sigue midiendo aunque cambies de app. No guardamos el historial.",
                    selected = scope == LocationScope.ALWAYS,
                ) { scope = LocationScope.ALWAYS }
            }
            OgtPrimaryButton("Continuar") { onConfirm(scope) }
            GhostLink("Ahora no") { onSkip() }
            Spacer(Modifier.height(16.dp))
        }
    }
}
