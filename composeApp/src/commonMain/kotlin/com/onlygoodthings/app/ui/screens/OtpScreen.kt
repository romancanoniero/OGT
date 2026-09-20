package com.onlygoodthings.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
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
fun OtpScreen(onConfirm: () -> Unit, onBack: () -> Unit) {
    val auth = LocalAuth.current
    val scope = rememberCoroutineScope()
    var code by remember { mutableStateOf("") }
    val phone = auth.pendingPhone?.phoneE164 ?: "tu número"
    AuthScaffold(onBack = onBack) {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            OgtLogo(size = 80.dp)
            OgtSectionTitle("Verifica tu número")
            OgtCaption("Enviamos un código de 6 dígitos por SMS a $phone")
            GhostLink("¿Número incorrecto? Editar") { onBack() }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)) {
            repeat(6) { i ->
                val ch = code.getOrNull(i)?.toString() ?: "•"
                Box(
                    Modifier.size(48.dp, 56.dp).clip(AuthCorner)
                        .background(OgtColors.surface)
                        .border(1.dp, if (i == code.length) OgtColors.secondary else OgtColors.sand, AuthCorner),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(ch, fontWeight = FontWeight.Bold, fontSize = 22.sp, color = OgtColors.primary)
                }
            }
        }
        AuthField("Código", "Emulador: ${AuthLab.TEST_OTP}", code) { if (it.length <= 6) code = it.filter { c -> c.isDigit() } }
        GhostLink("Reenviar código") { scope.launch { auth.resendOtp() } }
        AuthError(auth.error)
        OgtPrimaryButton(
            if (auth.busy) "Confirmando…" else "Confirmar y activar cuenta",
            enabled = !auth.busy,
        ) {
            scope.launch { if (auth.confirmOtp(code)) onConfirm() }
        }
        OgtPill("Comunidad segura: verificamos a cada persona")
    }
}
