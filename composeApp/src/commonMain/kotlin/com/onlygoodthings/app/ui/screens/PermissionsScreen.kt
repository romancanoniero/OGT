package com.onlygoodthings.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.onlygoodthings.app.theme.OgtColors
import com.onlygoodthings.app.ui.components.GhostLink
import com.onlygoodthings.app.ui.components.OgtCaption
import com.onlygoodthings.app.ui.components.OgtPrimaryButton
import com.onlygoodthings.app.ui.components.OgtTopBar
import com.onlygoodthings.app.ui.components.PrefCategory
import com.onlygoodthings.app.ui.components.PrefDivider
import com.onlygoodthings.app.ui.components.PrefGroup
import com.onlygoodthings.app.ui.components.PrefLine
import com.onlygoodthings.app.ui.components.ScreenColumn

@Composable
fun PermissionsScreen(
    onContinue: () -> Unit,
    onRequestNotify: () -> Unit = {},
    onBack: (() -> Unit)? = null,
) {
    LaunchedEffect(Unit) { onRequestNotify() }
    Column(Modifier.fillMaxSize().background(OgtColors.canvas).verticalScroll(rememberScrollState())) {
        OgtTopBar(title = "Privacidad", onBack = onBack)
        ScreenColumn {
            OgtCaption("Para ceder un lugar, avisar de una mascota o invitar a alguien, el teléfono pide unos accesos. Los podés apagar cuando quieras.")
            PrefCategory("Accesos")
            PrefGroup {
                PrefLine(
                    title = "Ubicación",
                    body = "Distancias de parking y alertas de mascotas. Solo con el GPS encendido. No vendemos rutas.",
                    trailing = "Necesario",
                )
                PrefDivider()
                PrefLine(
                    title = "Notificaciones",
                    body = "Plazas, matches y menciones. De 22 a 7 se silencia solo.",
                    trailing = "Tiempo real",
                )
                PrefDivider()
                PrefLine(
                    title = "Contactos",
                    body = "Para elegir a quién invitar. No subimos tu agenda.",
                    trailing = "Opcional",
                )
                PrefDivider()
                PrefLine(
                    title = "Cámara y fotos",
                    body = "Para publicar un recuerdo o un reporte. Se limpian los EXIF sensibles.",
                    trailing = "Para aportar",
                )
            }
            OgtCaption("Cero venta de datos. Cifrado de extremo a extremo.")
            OgtPrimaryButton("Aceptar y continuar") { onContinue() }
            GhostLink("Personalizar más tarde") { onContinue() }
            Spacer(Modifier.height(24.dp))
        }
    }
}
