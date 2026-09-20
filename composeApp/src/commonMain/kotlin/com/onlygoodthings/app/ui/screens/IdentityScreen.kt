package com.onlygoodthings.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.onlygoodthings.app.theme.OgtColors
import com.onlygoodthings.app.ui.components.OgtCard
import com.onlygoodthings.app.ui.components.OgtCaption
import com.onlygoodthings.app.ui.components.OgtPill
import com.onlygoodthings.app.ui.components.GhostLink
import com.onlygoodthings.app.ui.components.OgtPrimaryButton
import com.onlygoodthings.app.ui.components.OgtSecondaryButton
import com.onlygoodthings.app.ui.components.OgtSectionTitle
import com.onlygoodthings.app.ui.components.OgtTopBar
import com.onlygoodthings.app.ui.components.ScreenColumn

@Composable
fun IdentityScreen(onContinue: () -> Unit) {
    Column(Modifier.fillMaxSize().background(OgtColors.canvas).verticalScroll(rememberScrollState())) {
        OgtTopBar(title = "Verificación de perfil")
        ScreenColumn {
            OgtPill("Validación de Confianza Comunitaria")
            OgtCaption("Red segura")
            OgtSectionTitle("Verificá tu perfil en la comunidad")
            OgtCaption("OnlyGoodThings es una red segura basada en personas reales. Verificá tu identidad para activar todos los beneficios y generar máxima confianza en trueques y alertas, cerca o en la red.")
            OgtCard {
                Text("Comunidad activa", fontWeight = FontWeight.Bold)
                OgtCaption("94% de perfiles verificados")
                OgtPill("Nivel 2 / 3")
            }
            OgtSectionTitle("Beneficios de estar verificado")
            Benefit("Insignia de perfil verificado", "Destacado en tu avatar, perfil, publicaciones y respuestas.")
            Benefit("Alertas urgentes de mascotas", "Notificación push geolocalizada en 2 km para extravíos.")
            Benefit("Trueques ilimitados y reservas", "Sin comisiones y prioridad en las dársenas de la comunidad.")
            Benefit("Presupuesto participativo", "Voz y voto en el fondo ecológico y proyectos compartidos.")
            OgtSectionTitle("Proceso de verificación")
            OgtCaption("Paso 2 de 3")
            OgtCard {
                Text("Paso 1: Confirmación de zona", fontWeight = FontWeight.SemiBold)
                OgtCaption("Palermo Soho, CABA · Zona validada · Completado")
            }
            OgtCard {
                OgtPill("En curso")
                Text("Paso 2: Documento o Servicio de Residencia", fontWeight = FontWeight.Bold)
                OgtCaption("Subí o escaneá DNI o factura con domicilio coincidente. JPG, PNG o PDF digital. Máx 10 MB.")
            }
            OgtCard {
                OgtPill("Opcional")
                Text("Paso 3: Aval de la comunidad", fontWeight = FontWeight.SemiBold)
                OgtCaption("2 perfiles verificados pueden avalar que formás parte de la comunidad.")
            }
            OgtCaption("Tus documentos se procesan cifrados y se eliminan tras la validación automática. Nunca vendemos ni compartimos datos con terceros.")
            OgtPrimaryButton("Escanear Documento con la Cámara") { onContinue() }
            OgtSecondaryButton("Subir comprobante en PDF o Foto") { onContinue() }
            GhostLink("Continuar sin verificar") { onContinue() }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun Benefit(title: String, body: String) {
    OgtCard {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OgtPill("Exclusivo")
        }
        Text(title, fontWeight = FontWeight.Bold)
        OgtCaption(body)
    }
}
