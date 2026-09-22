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
import com.onlygoodthings.app.ui.components.OgtPrimaryButton
import com.onlygoodthings.app.ui.components.OgtSecondaryButton
import com.onlygoodthings.app.ui.components.OgtTopBar
import com.onlygoodthings.app.ui.components.PrefGroup
import com.onlygoodthings.app.ui.components.PrefLine

@Composable
fun GpsDialogScreen(
    onAllow: () -> Unit,
    onSkip: () -> Unit,
    onContinue: () -> Unit = onSkip,
    awaitingSystemDialog: Boolean = false,
    onBack: (() -> Unit)? = null,
) {
    val gps = LocalOgtLocation.current
    val hasFix = gps.hasLiveFix
    Column(Modifier.fillMaxSize().background(OgtColors.canvas)) {
        OgtTopBar(title = "GPS", onBack = onBack, hideOnScroll = false)
        Column(Modifier.padding(horizontal = 20.dp).weight(1f)) {
        OgtCaption("Pedimos el GPS del dispositivo para parking y alertas. No usamos la ubicación del perfil ni guardamos rutas.")
        Spacer(Modifier.weight(1f))
        PrefGroup {
            when {
                hasFix -> {
                    val acc = gps.fix?.accuracyMeters?.toInt()
                    PrefLine(
                        title = "Señal lista",
                        body = if (acc != null) "±$acc m" else "Fix en vivo",
                    )
                }
                gps.permission == LocationPermissionState.DENIED_FOREVER -> {
                    PrefLine(title = "Permiso bloqueado", body = "Hay que habilitarlo en el sistema.")
                }
                gps.permission == LocationPermissionState.GRANTED && !gps.servicesEnabled -> {
                    PrefLine(title = "GPS apagado", body = "El permiso está dado, falta el chip del sistema.")
                }
                gps.acquiring -> {
                    PrefLine(title = "Buscando señal", body = "Esperá un fix real del dispositivo.")
                }
                else -> {
                    PrefLine(title = "Sin permiso", body = "Se pide al continuar.")
                }
            }
        }
        Spacer(Modifier.weight(1f))
        when {
            hasFix -> OgtPrimaryButton("Continuar con GPS en vivo") { onContinue() }
            gps.permission == LocationPermissionState.DENIED_FOREVER ->
                OgtPrimaryButton("Abrir ajustes") { gps.openSettings() }
            gps.permission == LocationPermissionState.GRANTED && !gps.servicesEnabled ->
                OgtPrimaryButton("Activar GPS") { gps.ensureServices() }
            gps.acquiring -> {
                OgtPrimaryButton("Esperando señal…", enabled = false) { }
                OgtSecondaryButton("Reintentar") { gps.refreshNow() }
            }
            else -> {
                OgtPrimaryButton(
                    if (awaitingSystemDialog) "Esperando permiso…" else "Permitir al usar la app",
                    enabled = !awaitingSystemDialog,
                ) { onAllow() }
            }
        }
        GhostLink("Ahora no") { onSkip() }
        }
    }
}
