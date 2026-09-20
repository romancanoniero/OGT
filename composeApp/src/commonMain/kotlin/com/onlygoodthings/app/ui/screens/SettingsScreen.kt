package com.onlygoodthings.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.onlygoodthings.app.data.LocalOgtSession
import com.onlygoodthings.app.i18n.LocalOgtCopy
import com.onlygoodthings.app.i18n.OgtLang
import com.onlygoodthings.app.map.LocalOgtLocation
import com.onlygoodthings.app.theme.OgtColors
import com.onlygoodthings.app.ui.components.OgtCaption
import com.onlygoodthings.app.ui.components.OgtGpsBanner
import com.onlygoodthings.app.ui.components.gpsStatusLabel
import com.onlygoodthings.app.ui.components.OgtCard
import com.onlygoodthings.app.ui.components.OgtPill
import com.onlygoodthings.app.ui.components.OgtSectionTitle
import com.onlygoodthings.app.ui.components.OgtTopBar
import com.onlygoodthings.app.ui.components.ScreenColumn

@Composable
fun SettingsScreen(onLogout: () -> Unit = {}, onOpen: (String) -> Unit) {
    val session = LocalOgtSession.current
    val copy = LocalOgtCopy.current
    val me = session.me()
    Column(Modifier.fillMaxSize().background(OgtColors.canvas).verticalScroll(rememberScrollState())) {
        OgtTopBar(title = "Perfil Ajustes")
        ScreenColumn {
            OgtSectionTitle("Configuración y Privacidad")
            OgtCaption("Gestiona tus preferencias de comunidad y huella cívica")
            OgtCard {
                Text(me.displayName, fontWeight = FontWeight.Bold)
                OgtCaption(me.email.orEmpty())
                OgtPill(me.levelLabel)
                Text("${me.communityPoints} pts ecológicos", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = OgtColors.primary)
            }
            OgtSectionTitle(copy.yourCars)
            OgtCaption(copy.vehicleWhy)
            if (session.vehicles.isEmpty()) {
                OgtCaption(copy.vehicleNeeded)
            } else {
                session.vehicles.forEach { car ->
                    OgtCard {
                        com.onlygoodthings.app.ui.components.CarSilhouette(car.colorHex, width = 72.dp)
                        Text(car.label(), fontWeight = FontWeight.SemiBold)
                        if (session.selectedVehicleId == car.id) OgtPill(copy.selectedCar)
                    }
                }
            }
            OgtSectionTitle(copy.languageTitle)
            OgtCaption(copy.languageHint)
            SettingRow(copy.languageEs, if (session.language == OgtLang.ES) "●" else "○") {
                session.applyLanguage(OgtLang.ES)
            }
            SettingRow(copy.languageEn, if (session.language == OgtLang.EN) "●" else "○") {
                session.applyLanguage(OgtLang.EN)
            }
            OgtSectionTitle("Ajustes de Impacto y Geolocalización")
            if (session.here() == null) {
                OgtGpsBanner()
            } else {
                val gps = LocalOgtLocation.current
                SettingRow(
                    "GPS en vivo",
                    buildString {
                        append(gpsStatusLabel(true, session.locationAccuracy))
                        gps.fix?.let { append(" · ${it.latitude}, ${it.longitude}") }
                    },
                ) { gps.refreshNow() }
            }
            SettingRow(
                "Radar de Estacionamiento automático",
                if (session.radarEnabled) "Activo · detecta plazas solidarias cerca tuyo" else "Pausado",
            ) { session.radarEnabled = !session.radarEnabled }
            SettingRow("Radio de alertas comunitarias", "2 km a la redonda · ${me.barrio}")
            SettingRow("Modo Ahorro de Huella de Carbono", "Agrupa trayectos de carpooling y avisos eco locales")
            OgtSectionTitle("Privacidad y Visibilidad Social")
            SettingRow("Mostrar ubicación exacta en matches", "Desactivado: solo distancia relativa (ej. a 400 m)")
            SettingRow("Perfil visible para usuarios no registrados", "Permite validar donaciones comunitarias públicamente")
            SettingRow("Descargar mis datos de impacto", "Archivo cifrado ZIP con historial ecológico y GDPR")
            OgtSectionTitle("Notificaciones y Alertas")
            SettingRow("Alertas de animales en riesgo", "Inmediatas (Push)")
            SettingRow("Intercambio de Ayuda", "Avisos de una mano por otra")
            SettingRow("Sonidos de radar de parking", "Vibración suave")
            OgtSectionTitle("Cuenta y laboratorio")
            listOf(
                "Invitar amigos y contactos" to "invite",
                "Billetera de puntos" to "wallet",
                "Ranking de la comunidad" to "ranking",
                "Mensajes" to "messages",
                "Sponsors RSE" to "sponsors",
                "Celebrar hito" to "milestone",
                "Splash sponsors" to "splashSponsors",
                "Diálogo GPS" to "gps",
                "Permisos del dispositivo" to "permissions",
                "Logo animado" to "animatedSvg",
            ).forEach { (label, key) ->
                TextButton(onClick = { onOpen(key) }, modifier = Modifier.fillMaxWidth()) {
                    Text(label, color = OgtColors.primary, fontWeight = FontWeight.SemiBold)
                }
            }
            TextButton(onClick = onLogout, modifier = Modifier.fillMaxWidth()) {
                Text("Cerrar sesión", color = OgtColors.secondary, fontWeight = FontWeight.Bold)
            }
            OgtCaption("OnlyGoodThings v2.8.4 · energía renovable")
            Spacer(Modifier.height(80.dp))
        }
    }
}

@Composable
private fun SettingRow(title: String, body: String, onClick: (() -> Unit)? = null) {
    OgtCard(modifier = if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier) {
        Text(title, fontWeight = FontWeight.SemiBold)
        OgtCaption(body)
    }
}
