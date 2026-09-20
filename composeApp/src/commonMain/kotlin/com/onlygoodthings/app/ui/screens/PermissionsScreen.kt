package com.onlygoodthings.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.onlygoodthings.app.theme.OgtColors
import com.onlygoodthings.app.ui.components.GhostLink
import com.onlygoodthings.app.ui.components.OgtCaption
import com.onlygoodthings.app.ui.components.OgtCard
import com.onlygoodthings.app.ui.components.OgtPill
import com.onlygoodthings.app.ui.components.OgtPrimaryButton
import com.onlygoodthings.app.ui.components.OgtSectionTitle
import com.onlygoodthings.app.ui.components.OgtTopBar
import com.onlygoodthings.app.ui.components.ScreenColumn

@Composable
fun PermissionsScreen(onContinue: () -> Unit, onRequestNotify: () -> Unit = {}) {
    LaunchedEffect(Unit) { onRequestNotify() }
    Column(Modifier.fillMaxSize().background(OgtColors.canvas).verticalScroll(rememberScrollState())) {
        OgtTopBar(title = "Transparencia Comunitaria")
        ScreenColumn {
            OgtSectionTitle("Tu privacidad y seguridad son primero")
            OgtCaption("Para ayudarte a liberar estacionamiento, encontrar mascotas y conectar con la comunidad, necesitamos algunos accesos.")
            OgtPill("3 de 4 listos")
            OgtCaption("Control total en tu dispositivo. Puedes desactivar permisos cuando gustes.")
            PermCard(
                "Ubicación precisa en tiempo real",
                "Esencial",
                "Necesario para distancias exactas en el Asistente de Estacionamiento y alertar mascotas perdidas en un radio de 2 km. Permitido solo en uso activo. Tus rutas nunca se venden.",
            )
            PermCard(
                "Notificaciones instantáneas",
                "Tiempo real",
                "Avisos de plazas libres y matches de trueque. Silenciado automático de 22:00 a 07:00 h.",
            )
            PermCard(
                "Contactos y Agenda",
                "Opcional",
                "Para invitar a personas de confianza y crear grupos de comunidad. Nunca subimos tu libreta entera a la nube.",
            )
            PermCard(
                "Cámara y Fotos",
                "Para aportar",
                "Fotos de buenas acciones, mascotas en adopción o reportes. Metadatos EXIF sensibles se depuran automáticamente.",
            )
            OgtCaption("Cero venta de datos personales a terceros, arquitectura auditada y cifrado de extremo a extremo.")
            OgtPrimaryButton("Aceptar y Continuar") { onContinue() }
            GhostLink("Personalizar permisos más tarde") { onContinue() }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun PermCard(title: String, tag: String, body: String) {
    OgtCard {
        OgtPill(tag)
        Text(title, fontWeight = FontWeight.Bold)
        OgtCaption(body)
    }
}
