package com.onlygoodthings.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.onlygoodthings.app.map.LocalOgtLocation
import com.onlygoodthings.app.map.LocationPermissionState
import com.onlygoodthings.app.theme.OgtColors
import com.onlygoodthings.app.ui.components.GhostLink
import com.onlygoodthings.app.ui.components.OgtCaption
import com.onlygoodthings.app.ui.components.OgtCard
import com.onlygoodthings.app.ui.components.OgtPill
import com.onlygoodthings.app.ui.components.OgtPrimaryButton
import com.onlygoodthings.app.ui.components.OgtSecondaryButton
import com.onlygoodthings.app.ui.components.OgtSectionTitle

@Composable
fun GpsDialogScreen(
    onAllow: () -> Unit,
    onSkip: () -> Unit,
    onContinue: () -> Unit = onSkip,
    awaitingSystemDialog: Boolean = false,
) {
    val gps = LocalOgtLocation.current
    val hasFix = gps.hasLiveFix
    Column(Modifier.fillMaxSize().background(OgtColors.canvas).padding(24.dp)) {
        OgtPill("GPS de precisión")
        Spacer(Modifier.weight(1f))
        OgtCard {
            OgtPill("Requerido para el Asistente de Parking")
            OgtSectionTitle("¿Permitir a OnlyGoodThings usar tu ubicación en tiempo real?")
            OgtCaption("Pedimos el GPS del dispositivo (Fused Location / Core Location), incluido el del emulador. No usamos la ubicación del perfil.")
            OgtCaption("El rastreo corre solo mientras buscás o cedés un lugar. Al confirmar la cesión se apaga. No guardamos el historial de rutas.")
            when {
                hasFix -> {
                    val acc = gps.fix?.accuracyMeters?.toInt()
                    OgtCaption("Fix listo${if (acc != null) " · ±$acc m" else ""}.")
                    OgtPrimaryButton("Continuar con GPS en vivo") { onContinue() }
                }
                gps.permission == LocationPermissionState.DENIED_FOREVER -> {
                    OgtCaption("El permiso quedó bloqueado en el sistema.")
                    OgtPrimaryButton("Abrir ajustes") { gps.openSettings() }
                }
                gps.permission == LocationPermissionState.GRANTED && !gps.servicesEnabled -> {
                    OgtCaption("El permiso está dado, pero el GPS del sistema está apagado.")
                    OgtPrimaryButton("Activar GPS") { gps.ensureServices() }
                }
                gps.acquiring -> {
                    OgtCaption("Buscando satélites y red. Esperá un fix real…")
                    OgtPrimaryButton("Esperando señal GPS…", enabled = false) { }
                    OgtSecondaryButton("Reintentar") { gps.refreshNow() }
                }
                else -> {
                    OgtPrimaryButton(
                        if (awaitingSystemDialog) "Esperando permiso del sistema…" else "Permitir al usar la app",
                        enabled = !awaitingSystemDialog,
                    ) { onAllow() }
                    OgtSecondaryButton("Permitir solo esta vez", enabled = !awaitingSystemDialog) { onAllow() }
                }
            }
            GhostLink("Ahora no") { onSkip() }
        }
        Spacer(Modifier.weight(1f))
    }
}
