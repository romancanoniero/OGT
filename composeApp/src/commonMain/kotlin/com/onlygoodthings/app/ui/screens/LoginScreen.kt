package com.onlygoodthings.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.onlygoodthings.app.auth.AuthLab
import com.onlygoodthings.app.data.LocalAuth
import com.onlygoodthings.app.theme.OgtColors
import com.onlygoodthings.app.theme.OgtLogo
import com.onlygoodthings.app.ui.components.GhostLink
import com.onlygoodthings.app.ui.components.OgtCaption
import com.onlygoodthings.app.ui.components.OgtPill
import com.onlygoodthings.app.ui.components.OgtPrimaryButton
import com.onlygoodthings.app.ui.components.OgtSectionTitle
import kotlinx.coroutines.launch

@Composable
fun LoginScreen(onEnter: () -> Unit, onSignUp: () -> Unit, onRecover: () -> Unit, onOtp: () -> Unit) {
    val auth = LocalAuth.current
    val scope = rememberCoroutineScope()
    var id by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var keep by remember { mutableStateOf(true) }
    AuthScaffold {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            OgtLogo(size = 88.dp)
            OgtPill("Red de impacto comunitario")
            OgtSectionTitle("Bienvenido de vuelta")
            OgtCaption("Seguí multiplicando las buenas acciones e historias de tu comunidad.")
        }
        AuthProviderRow { provider ->
            scope.launch {
                if (auth.signInProvider(provider)) onEnter()
            }
        }
        Box(
            Modifier.fillMaxWidth().clip(AuthCorner).background(OgtColors.sand).padding(14.dp)
                .clickable {
                    scope.launch {
                        if (auth.unlockWithBiometric()) {
                            auth.enableBiometricAfterLogin()
                            onEnter()
                        }
                    }
                },
            contentAlignment = Alignment.Center,
        ) { Text("Ingresar con Huella o Rostro · Rápido", fontWeight = FontWeight.SemiBold, fontSize = 13.sp) }
        Text("O CON TU CORREO / TELÉFONO", color = OgtColors.muted, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
        OgtCaption("Lab emulador: ${AuthLab.TEST_EMAIL} / ${AuthLab.TEST_PHONE_E164}")
        AuthField("Correo o teléfono", "correo@comunidad.org o ${AuthLab.TEST_PHONE_E164}", id) { id = it }
        GhostLink("¿Olvidaste tu contraseña?") { onRecover() }
        AuthField("Contraseña", "Tu clave secreta", password, secret = true) { password = it }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(keep, { keep = it }, colors = CheckboxDefaults.colors(checkedColor = OgtColors.primary))
            Text("Mantener sesión activa", fontSize = 13.sp, color = OgtColors.muted)
        }
        AuthError(auth.error)
        OgtPrimaryButton(
            if (auth.busy) "Ingresando…" else "Ingresar a mi comunidad",
            enabled = !auth.busy,
        ) {
            scope.launch {
                val ok = auth.signInIdentifier(id, password, keep)
                when {
                    auth.pendingPhone != null -> onOtp()
                    ok -> onEnter()
                }
            }
        }
        Text("Hoy en la comunidad: se sumaron 14 árboles y 3 huertos comunitarios.", color = OgtColors.muted, fontSize = 13.sp)
        GhostLink("¿Aún no eres parte de la red? Regístrate aquí") { onSignUp() }
    }
}
