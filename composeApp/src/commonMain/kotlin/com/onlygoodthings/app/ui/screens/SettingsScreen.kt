package com.onlygoodthings.app.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.onlygoodthings.app.data.LocalOgtSession
import com.onlygoodthings.app.i18n.LocalOgtCopy
import com.onlygoodthings.app.i18n.OgtLang
import com.onlygoodthings.app.map.LocalOgtLocation
import com.onlygoodthings.app.map.LocationPermissionState
import com.onlygoodthings.app.map.covers
import com.onlygoodthings.app.map.isGranted
import com.onlygoodthings.app.notify.rememberOgtNotify
import com.onlygoodthings.app.platform.rememberHonorInviteActions
import com.onlygoodthings.app.platform.rememberOgtCameraAccess
import com.onlygoodthings.app.theme.OgtColors
import com.onlygoodthings.app.theme.OgtMotion
import com.onlygoodthings.app.ui.components.CarSilhouette
import com.onlygoodthings.app.ui.components.OgtCaption
import com.onlygoodthings.app.ui.components.OgtTopBar
import com.onlygoodthings.app.ui.components.PrefCategory
import com.onlygoodthings.app.ui.components.PrefDivider
import com.onlygoodthings.app.ui.components.PrefGroup
import com.onlygoodthings.app.ui.components.PrefLine
import com.onlygoodthings.app.ui.components.PrefNav
import com.onlygoodthings.app.ui.components.PrefRadio
import com.onlygoodthings.app.ui.components.PrefRevealDelete
import com.onlygoodthings.app.ui.components.PrefSwitch
import com.onlygoodthings.app.ui.components.ScreenColumn
import com.onlygoodthings.shared.domain.LocationScope

/**
 * Ajustes al modo sistema: grupos, switch, fila de idioma.
 * Permisos del dispositivo viven acá. El garage se usa al ceder un lugar.
 */
@Composable
fun SettingsScreen(onLogout: () -> Unit = {}, onBack: () -> Unit = {}, onOpen: (String) -> Unit) {
    val session = LocalOgtSession.current
    val copy = LocalOgtCopy.current
    val me = session.me()
    val gps = LocalOgtLocation.current
    val notify = rememberOgtNotify()
    val contacts = rememberHonorInviteActions()
    val camera = rememberOgtCameraAccess()
    val gpsDetail = when {
        !session.gpsEnabled -> null
        gps.permission == LocationPermissionState.DENIED_FOREVER -> "Bloqueado en el sistema"
        !gps.permission.covers(session.locationScope) &&
            session.locationScope == LocationScope.ALWAYS -> "Falta el permiso en segundo plano"
        !gps.permission.isGranted() -> "Sin permiso de ubicación"
        !gps.servicesEnabled -> "Apagado en el teléfono"
        gps.acquiring -> "Buscando señal"
        session.here() != null -> session.locationAccuracy?.let { "Señal ±${it.toInt()} m" } ?: "Con señal"
        else -> "Sin señal"
    }
    Column(Modifier.fillMaxSize().background(OgtColors.canvas).verticalScroll(rememberScrollState())) {
        OgtTopBar(title = "Ajustes", onBack = onBack)
        ScreenColumn {
            Text(me.displayName, fontWeight = FontWeight.Bold, fontSize = 22.sp, color = OgtColors.ink)
            if (!me.email.isNullOrBlank()) OgtCaption(me.email.orEmpty())
            OgtCaption("${me.levelLabel} · ${me.communityPoints} pts")

            PrefCategory(copy.languageTitle)
            PrefGroup {
                PrefNav(
                    title = copy.languageTitle,
                    value = if (session.language == OgtLang.ES) copy.languageEs else copy.languageEn,
                ) { onOpen("language") }
            }

            PrefCategory(copy.yourCars, copy.vehicleWhy)
            PrefGroup {
                session.vehicles.forEachIndexed { index, car ->
                    if (index > 0) PrefDivider()
                    PrefRevealDelete(onDelete = { session.removeVehicle(car.id) }) {
                        PrefLine(
                            title = car.make.ifBlank { copy.vehicleTitle },
                            body = listOf(car.color, car.plate).filter { it.isNotBlank() }.joinToString(" · "),
                            trailing = if (session.selectedVehicleId == car.id) copy.carInUse else null,
                            leading = { CarSilhouette(car.colorHex, width = 56.dp) },
                        ) { onOpen("car:${car.id}") }
                    }
                }
                if (session.vehicles.isNotEmpty()) PrefDivider()
                PrefNav(copy.addAnotherCar) { onOpen("car") }
            }

            PrefCategory("Permisos", "Para ceder un lugar, avisar de una mascota o invitar. Los podés apagar cuando quieras.")
            PrefGroup {
                PrefSwitch(
                    title = "GPS",
                    body = gpsDetail,
                    checked = session.gpsEnabled,
                ) { on ->
                    session.applyGpsEnabled(on)
                    if (!on) return@PrefSwitch
                    gps.ensureScope(session.locationScope)
                }
                AnimatedVisibility(
                    visible = session.gpsEnabled,
                    enter = fadeIn(OgtMotion.fade) + expandVertically(OgtMotion.size),
                    exit = fadeOut(OgtMotion.fade) + shrinkVertically(OgtMotion.size),
                ) {
                    Column {
                        PrefDivider()
                        PrefRadio(
                            title = "Mientras uses la app",
                            body = "Sincroniza cesiones y distancias con Parking o el mapa abiertos.",
                            selected = session.locationScope == LocationScope.WHILE_USING,
                        ) {
                            session.applyLocationScope(LocationScope.WHILE_USING)
                            gps.ensureScope(LocationScope.WHILE_USING)
                        }
                        PrefDivider()
                        PrefRadio(
                            title = "Siempre",
                            body = "Sigue midiendo aunque cambies de app. No guardamos el historial.",
                            selected = session.locationScope == LocationScope.ALWAYS,
                        ) {
                            session.applyLocationScope(LocationScope.ALWAYS)
                            gps.ensureScope(LocationScope.ALWAYS)
                        }
                    }
                }
                PrefDivider()
                PrefLine(
                    title = "Notificaciones",
                    body = "Plazas, matches y menciones. De 22 a 7 se silencia solo.",
                    trailing = if (notify.permissionGranted) "Activo" else "Pedir",
                ) { if (!notify.permissionGranted) notify.requestPermission() }
                PrefDivider()
                PrefLine(
                    title = "Contactos",
                    body = "Para elegir a quién invitar. No subimos tu agenda.",
                    trailing = if (contacts.contactsAllowed) "Permitido" else "Pedir",
                ) { if (!contacts.contactsAllowed) contacts.requestContacts() }
                PrefDivider()
                PrefLine(
                    title = "Cámara y fotos",
                    body = "Para publicar un recuerdo o un reporte. Se limpian los EXIF sensibles.",
                    trailing = if (camera.granted) "Permitido" else "Pedir",
                ) { if (!camera.granted) camera.request() }
                PrefDivider()
                PrefSwitch(
                    title = "Radar de estacionamiento",
                    body = "Detecta plazas solidarias cerca tuyo",
                    checked = session.radarEnabled,
                ) { session.radarEnabled = it }
                PrefDivider()
                PrefSwitch(
                    title = "Ahorro de huella",
                    body = "Agrupa trayectos y avisos eco locales",
                    checked = session.carbonSaveMode,
                ) { session.carbonSaveMode = it }
            }

            PrefCategory("Privacidad")
            PrefGroup {
                PrefSwitch(
                    title = "Ubicación exacta en matches",
                    body = "Si está apagado, solo se ve la distancia relativa",
                    checked = session.showExactMatchLocation,
                ) { session.showExactMatchLocation = it }
                PrefDivider()
                PrefSwitch(
                    title = "Perfil visible sin cuenta",
                    body = "Permite validar donaciones comunitarias",
                    checked = session.publicProfileVisible,
                ) { session.publicProfileVisible = it }
                PrefDivider()
                PrefNav("Descargar mis datos", body = "Historial de impacto") { }
            }

            PrefCategory("Notificaciones")
            PrefGroup {
                PrefSwitch(
                    title = "Animales en riesgo",
                    body = "Alertas push inmediatas",
                    checked = session.animalAlertPush,
                ) { session.animalAlertPush = it }
                PrefDivider()
                PrefSwitch(
                    title = "Intercambio de ayuda",
                    body = "Avisos de una mano por otra",
                    checked = session.skillAlertPush,
                ) { session.skillAlertPush = it }
                PrefDivider()
                PrefSwitch(
                    title = "Sonidos de radar",
                    body = "Aviso al ceder o pedir un lugar",
                    checked = session.parkingRadarSounds,
                ) { session.parkingRadarSounds = it }
            }

            PrefCategory("Comunidad")
            PrefGroup {
                PrefNav("Reglas de la comunidad", body = "Qué se publica y qué no") { onOpen("rules") }
            }

            PrefCategory("Cuenta")
            PrefGroup {
                listOf(
                    "Invitar amigos y contactos" to "invite",
                    "Billetera de puntos" to "wallet",
                    "Ranking de la comunidad" to "ranking",
                    "Mensajes" to "messages",
                    "Sponsors RSE" to "sponsors",
                    "Celebrar hito" to "milestone",
                    "Splash sponsors" to "splashSponsors",
                    "Logo animado" to "animatedSvg",
                ).forEachIndexed { index, (label, key) ->
                    if (index > 0) PrefDivider()
                    PrefNav(label) { onOpen(key) }
                }
            }
            Text(
                "Cerrar sesión",
                color = OgtColors.secondary,
                fontWeight = FontWeight.SemiBold,
                fontSize = 16.sp,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onLogout)
                    .padding(vertical = 16.dp),
            )
            OgtCaption("OnlyGoodThings v2.8.4")
            Spacer(Modifier.height(80.dp))
        }
    }
}
