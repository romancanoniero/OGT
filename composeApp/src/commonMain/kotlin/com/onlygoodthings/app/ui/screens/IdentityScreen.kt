package com.onlygoodthings.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.onlygoodthings.app.theme.OgtColors
import com.onlygoodthings.app.ui.components.GhostLink
import com.onlygoodthings.app.ui.components.OgtCaption
import com.onlygoodthings.app.ui.components.OgtPrimaryButton
import com.onlygoodthings.app.ui.components.OgtSecondaryButton
import com.onlygoodthings.app.ui.components.OgtTopBar
import com.onlygoodthings.app.ui.components.PrefCategory
import com.onlygoodthings.app.ui.components.PrefDivider
import com.onlygoodthings.app.ui.components.PrefGroup
import com.onlygoodthings.app.ui.components.PrefLine
import com.onlygoodthings.app.ui.components.ScreenColumn

@Composable
fun IdentityScreen(onContinue: () -> Unit) {
    Column(Modifier.fillMaxSize().background(OgtColors.canvas).verticalScroll(rememberScrollState())) {
        OgtTopBar(title = "Verificación")
        ScreenColumn {
            OgtCaption("OnlyGoodThings es una red de personas reales. Verificar el perfil abre alertas, trueques y voto comunitario.")
            PrefCategory("Beneficios")
            PrefGroup {
                PrefLine(
                    title = "Insignia de verificado",
                    body = "Se ve en el avatar, el perfil, las publicaciones y las respuestas.",
                )
                PrefDivider()
                PrefLine(
                    title = "Alertas de mascotas",
                    body = "Aviso geolocalizado cuando hay un extravío cerca.",
                )
                PrefDivider()
                PrefLine(
                    title = "Trueques y reservas",
                    body = "Sin comisión y con prioridad en las dársenas de la comunidad.",
                )
                PrefDivider()
                PrefLine(
                    title = "Presupuesto participativo",
                    body = "Voz y voto en el fondo ecológico y los proyectos compartidos.",
                )
            }
            PrefCategory("Proceso", "Paso 2 de 3")
            PrefGroup {
                PrefLine(
                    title = "Confirmación de zona",
                    body = "Palermo Soho, CABA · Zona validada",
                    trailing = "Listo",
                )
                PrefDivider()
                PrefLine(
                    title = "Documento o servicio",
                    body = "DNI o factura con domicilio. JPG, PNG o PDF. Máx. 10 MB.",
                    trailing = "En curso",
                )
                PrefDivider()
                PrefLine(
                    title = "Aval de la comunidad",
                    body = "Dos perfiles verificados pueden confirmar que formás parte.",
                    trailing = "Opcional",
                )
            }
            OgtCaption("Los documentos se procesan cifrados y se borran al validar. No se venden ni se comparten.")
            OgtPrimaryButton("Escanear documento") { onContinue() }
            OgtSecondaryButton("Subir PDF o foto") { onContinue() }
            GhostLink("Continuar sin verificar") { onContinue() }
            Spacer(Modifier.height(24.dp))
        }
    }
}
