package com.onlygoodthings.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
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
import com.onlygoodthings.app.ui.components.GhostLink
import com.onlygoodthings.app.ui.components.OgtCaption
import com.onlygoodthings.app.ui.components.OgtPill
import com.onlygoodthings.app.ui.components.OgtPrimaryButton
import com.onlygoodthings.app.ui.components.OgtSectionTitle
import com.onlygoodthings.shared.domain.titleCasePersonName
import kotlinx.coroutines.launch

@Composable
fun SignUpScreen(onOtp: () -> Unit, onLogin: () -> Unit, onRegistered: () -> Unit) {
    val auth = LocalAuth.current
    val scope = rememberCoroutineScope()
    var name by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var barrio by remember { mutableStateOf("Palermo Soho, Buenos Aires") }
    var password by remember { mutableStateOf("") }
    var pact by remember { mutableStateOf(false) }
    AuthScaffold(onBack = onLogin) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            OgtPill("Comunidad Segura")
            Text("Paso 1 de 2", color = OgtColors.muted, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        }
        Box(Modifier.fillMaxWidth().height(6.dp).clip(CircleShape).background(OgtColors.stone)) {
            Box(Modifier.fillMaxWidth(0.5f).height(6.dp).clip(CircleShape).background(OgtColors.primary))
        }
        OgtSectionTitle("Únete a la comunidad")
        OgtCaption("Sumate a quienes usan la app para mejorar y ayudar, cerca o en la red.")
        AuthFeatureReel()
        AuthProviderRow { provider ->
            scope.launch { if (auth.signInProvider(provider)) onRegistered() }
        }
        AuthField("Nombre y apellido", "Ej. Martín Soler", name) { name = titleCasePersonName(it) }
        AuthField("Correo o teléfono", "correo@comunidad.org o ${AuthLab.TEST_PHONE_E164}", email) { email = it }
        AuthField("Tu zona o comunidad", "Palermo Soho, Buenos Aires", barrio) { barrio = it }
        AuthField("Crear Contraseña segura", "••••••••", password, secret = true) { password = it }
        Row(verticalAlignment = Alignment.Top) {
            Checkbox(pact, { pact = it }, colors = CheckboxDefaults.colors(checkedColor = OgtColors.primary))
            Text(
                "Acepto el Código de Convivencia (cero odio, respeto mutuo, ayuda desinteresada y sin fines partidarios) y los Términos de Servicio.",
                fontSize = 13.sp,
                color = OgtColors.charcoal,
                modifier = Modifier.padding(top = 10.dp),
            )
        }
        AuthError(auth.error ?: if (!pact) "Tenés que aceptar el Código de Convivencia" else null)
        OgtPrimaryButton(
            if (auth.busy) "Creando cuenta…" else "Continuar",
            enabled = !auth.busy && pact,
        ) {
            scope.launch {
                val ok = auth.signUp(name, email, password, barrio)
                when {
                    auth.pendingPhone != null -> onOtp()
                    ok -> onRegistered()
                }
            }
        }
        GhostLink("¿Ya tienes cuenta en tu red? Inicia sesión") { onLogin() }
    }
}
