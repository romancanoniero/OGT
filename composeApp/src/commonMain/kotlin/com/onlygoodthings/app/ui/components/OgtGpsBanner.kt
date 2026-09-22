package com.onlygoodthings.app.ui.components

import androidx.compose.runtime.Composable
import com.onlygoodthings.app.i18n.LocalOgtCopy
import com.onlygoodthings.app.map.LocalOgtLocation
import com.onlygoodthings.app.map.LocationPermissionState
import com.onlygoodthings.app.map.covers
import com.onlygoodthings.app.map.isGranted
/** CTA cuando todavía no hay fix GPS real. */
@Composable
fun OgtGpsBanner() {
    val gps = LocalOgtLocation.current
    val session = com.onlygoodthings.app.data.LocalOgtSession.current
    val (title, body, action) = when {
        gps.permission == LocationPermissionState.DENIED_FOREVER ->
            Triple("GPS bloqueado", "Activalo en Ajustes del sistema para ceder o reservar un lugar.", "Abrir ajustes")
        !gps.permission.isGranted() ->
            Triple("GPS apagado", "El radar usa tu posición real. No usamos la del perfil.", "Permitir ubicación")
        !gps.servicesEnabled ->
            Triple("Ubicación del sistema desactivada", "Encendé el GPS del teléfono para continuar.", "Activar GPS")
        gps.acquiring ->
            Triple("Buscando señal GPS", "Esperando el fix del dispositivo o del GPS del emulador. No usamos la del perfil.", null)
        else ->
            Triple("Sin señal GPS", "En el emulador fijá un punto en Extended controls → Location. En un teléfono, salí al exterior.", "Reintentar")
    }
    OgtCard {
        OgtPill("GPS real")
        OgtSectionTitle(title)
        OgtCaption(body)
        if (action != null) {
            OgtPrimaryButton(action) {
                when {
                    gps.permission == LocationPermissionState.DENIED_FOREVER -> gps.openSettings()
                    !gps.permission.isGranted() || !gps.permission.covers(session.locationScope) -> {
                        if (session.gpsEnabled) gps.ensureScope(session.locationScope)
                        else session.askLocationScope = true
                    }
                    !gps.servicesEnabled -> gps.ensureServices()
                    else -> gps.refreshNow()
                }
            }
        }
    }
}

fun gpsStatusLabel(hasFix: Boolean, accuracyMeters: Float?): String = when {
    hasFix && accuracyMeters != null -> "GPS en vivo · ±${accuracyMeters.toInt()} m"
    hasFix -> "GPS en vivo"
    else -> "Sin fix GPS"
}

@Composable
fun gpsStatusLabelI18n(hasFix: Boolean, accuracyMeters: Float?): String {
    val copy = LocalOgtCopy.current
    return if (hasFix) copy.gpsLive(accuracyMeters?.toInt()) else copy.noGps
}
