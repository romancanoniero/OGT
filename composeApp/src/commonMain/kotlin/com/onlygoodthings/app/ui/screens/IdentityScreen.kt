package com.onlygoodthings.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.onlygoodthings.app.theme.OgtColors
import com.onlygoodthings.app.ui.components.*

/** Account access is not a documentary identity or address verification. */
@Composable
fun IdentityScreen(onContinue: () -> Unit) {
    Column(Modifier.fillMaxSize().background(OgtColors.canvas).navigationBarsPadding()) {
        OgtTopBar(title = "Tu identidad")
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            ScreenColumn {
                OgtCaption("En OGT podés participar sin presentar documentos personales.")
                PrefCategory("Tu cuenta")
                PrefGroup {
                    PrefLine(title = "Acceso a OGT", body = "El ingreso mediante correo, teléfono, Google o Apple permite acceder a tu cuenta. No certifica tu identidad civil ni tus antecedentes.")
                    PrefDivider()
                    PrefLine(title = "Ubicación opcional", body = "La ubicación del teléfono se usa en las funciones que la necesitan y se configura desde Ajustes. No verifica tu domicilio.")
                    PrefDivider()
                    PrefLine(title = "Sin documentos", body = "OGT no solicita ni recibe DNI o facturas para verificar personas. No compartas documentos en publicaciones, historias ni chats.")
                }
                OgtCaption("Esta versión no otorga una insignia de identidad verificada a partir del ingreso o de la ubicación. Las funciones comunitarias disponibles no requieren una verificación documental.")
                Spacer(Modifier.height(24.dp))
            }
        }
        Column(Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
            OgtPrimaryButton("Continuar") { onContinue() }
        }
    }
}
