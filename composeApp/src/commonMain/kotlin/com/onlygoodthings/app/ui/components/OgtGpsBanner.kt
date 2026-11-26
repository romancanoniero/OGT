package com.onlygoodthings.app.ui.components

import androidx.compose.runtime.Composable
import com.onlygoodthings.app.i18n.LocalOgtCopy
import com.onlygoodthings.app.map.LocalOgtLocation
import com.onlygoodthings.app.map.LocationRequirement
import com.onlygoodthings.app.map.ensureLocationAccess
import com.onlygoodthings.app.map.locationRequirement
/** CTA cuando todavía no hay fix GPS real. */
@Composable
fun OgtGpsBanner() {
    val gps = LocalOgtLocation.current
    val session = com.onlygoodthings.app.data.LocalOgtSession.current
    val requirement=locationRequirement(session.gpsEnabled,gps.permission,gps.servicesEnabled,gps.hasLiveFix,gps.acquiring)
    val (title, body, action) = when (requirement) {
        LocationRequirement.APP_DISABLED ->
            Triple("GPS desactivado en OGT", "Activá la ubicación en OGT para usar las funciones que necesitan tu posición. Podés elegir cuándo se utiliza.", "Activar ubicación")
        LocationRequirement.UNSUPPORTED ->
            Triple("Ubicación no disponible", "Este dispositivo no permite obtener la ubicación. Podés indicar una dirección o un punto en el mapa.", null)
        LocationRequirement.PERMISSION_BLOCKED ->
            Triple("GPS bloqueado", "Permití el acceso desde Ajustes del sistema para usar tu ubicación en esta función.", "Abrir ajustes")
        LocationRequirement.PERMISSION_REQUIRED ->
            Triple("Falta permiso de ubicación", "OGT necesita el permiso del dispositivo para obtener tu posición real. No usamos la del perfil.", "Permitir ubicación")
        LocationRequirement.SYSTEM_DISABLED ->
            Triple("Ubicación del sistema desactivada", "Encendé el GPS del teléfono para continuar.", "Activar GPS")
        LocationRequirement.ACQUIRING ->
            Triple("Buscando señal GPS", "Esperando el fix del dispositivo o del GPS del emulador. No usamos la del perfil.", null)
        LocationRequirement.NO_FIX ->
            Triple("Sin señal GPS", "En el emulador fijá un punto en Extended controls → Location. En un teléfono, salí al exterior.", "Reintentar")
        LocationRequirement.READY -> Triple("GPS listo", "La ubicación del dispositivo está disponible.", null)
    }
    OgtCard {
        OgtPill("GPS real")
        OgtSectionTitle(title)
        OgtCaption(body)
        if (action != null) {
            OgtPrimaryButton(action) {
                if(ensureLocationAccess(session.gpsEnabled,gps) { session.askLocationScope=true })gps.refreshNow()
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
