package com.onlygoodthings.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
fun RecoverScreen(onSent: () -> Unit, onLogin: () -> Unit, onOtp: () -> Unit) {
    val auth = LocalAuth.current
    val scope = rememberCoroutineScope()
    var email by remember { mutableStateOf("") }
    var byMail by remember { mutableStateOf(true) }
    AuthScaffold(onBack = onLogin) {
        OgtPill("Red de Apoyo 24/7")
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            OgtLogo(size = 88.dp)
            OgtPill("Sol Naciente • Manos Solidarias")
            OgtSectionTitle("Recuperar acceso a tu cuenta")
            OgtCaption("Te enviaremos un enlace de restablecimiento o un código SMS. No perdés tus Puntos Karma.")
        }
        Row(Modifier.fillMaxWidth().clip(CircleShape).background(OgtColors.sand).padding(4.dp)) {
            Box(
                Modifier.weight(1f).clip(CircleShape).background(if (byMail) OgtColors.surface else OgtColors.sand)
                    .clickable { byMail = true }
                    .padding(12.dp),
                contentAlignment = Alignment.Center,
            ) { Text("Por Correo", fontWeight = FontWeight.SemiBold, color = OgtColors.primary) }
            Box(
                Modifier.weight(1f).clip(CircleShape).background(if (!byMail) OgtColors.surface else OgtColors.sand)
                    .clickable { byMail = false }
                    .padding(12.dp),
                contentAlignment = Alignment.Center,
            ) { Text("WhatsApp / SMS", color = OgtColors.muted, fontWeight = FontWeight.SemiBold) }
        }
        AuthField(
            if (byMail) "Correo registrado" else "Teléfono registrado",
            if (byMail) "comunidad.solidaria@gmail.com" else "+54 9 11…",
            email,
        ) { email = it }
        Text(
            "Tip vecinal: si cambiaste de número, el soporte comunitario te ayuda en minutos.",
            color = OgtColors.sunsetText,
            fontSize = 13.sp,
            modifier = Modifier.clip(RoundedCornerShape(16.dp)).background(OgtColors.sunset).padding(12.dp),
        )
        AuthError(auth.error)
        OgtPrimaryButton(
            if (auth.busy) "Enviando…" else if (byMail) "Enviar enlace de restablecimiento" else "Enviar código SMS",
            enabled = !auth.busy,
        ) {
            scope.launch {
                val ok = auth.recover(email, byMail)
                when {
                    auth.pendingPhone != null -> onOtp()
                    ok -> onSent()
                }
            }
        }
        GhostLink("¿Recordaste tu contraseña? Volver a Iniciar Sesión") { onLogin() }
    }
}
