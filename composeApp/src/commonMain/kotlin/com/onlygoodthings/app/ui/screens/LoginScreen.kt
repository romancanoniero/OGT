package com.onlygoodthings.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.onlygoodthings.app.auth.AuthLab
import com.onlygoodthings.app.data.LocalAuth
import com.onlygoodthings.app.theme.OgtColors
import com.onlygoodthings.app.theme.OgtDimens
import com.onlygoodthings.app.theme.OgtLogo
import com.onlygoodthings.app.ui.components.GhostLink
import com.onlygoodthings.app.ui.components.OgtPrimaryButton
import com.onlygoodthings.app.ui.components.OgtSectionTitle
import kotlinx.coroutines.launch

@Composable
fun LoginScreen(onEnter: () -> Unit, onSignUp: () -> Unit, onRecover: () -> Unit, onOtp: () -> Unit) {
    val auth = LocalAuth.current
    val scope = rememberCoroutineScope()
    var byPhone by remember { mutableStateOf(false) }
    var email by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var keep by remember { mutableStateOf(true) }
    var biometric by remember { mutableStateOf(false) }
    val imeOpen = WindowInsets.ime.getBottom(LocalDensity.current) > 0
    LaunchedEffect(Unit) {
        biometric = auth.hasBiometric()
    }
    val scroll = rememberScrollState()

    Column(
        Modifier
            .fillMaxSize()
            .background(OgtColors.canvas)
            .windowInsetsPadding(WindowInsets.statusBars.union(WindowInsets.navigationBars))
            .imePadding()
            .then(if (imeOpen) Modifier.verticalScroll(scroll) else Modifier)
            .padding(horizontal = OgtDimens.margin)
            .padding(top = 8.dp, bottom = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        OgtLogo(size = 36.dp)
        Spacer(Modifier.height(6.dp))
        OgtSectionTitle("Bienvenido de vuelta")
        Text(
            "Seguí multiplicando las buenas acciones de tu comunidad.",
            color = OgtColors.muted,
            fontSize = 13.sp,
            lineHeight = 18.sp,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(16.dp))
        LoginMode(byPhone) { byPhone = it }
        Spacer(Modifier.height(12.dp))
        if (byPhone) {
            AuthField("Teléfono", AuthLab.TEST_PHONE_E164, phone) { phone = it }
        } else {
            AuthField("Correo", AuthLab.TEST_EMAIL, email) { email = it }
            Spacer(Modifier.height(8.dp))
            AuthField("Contraseña", "Tu clave secreta", password, secret = true) { password = it }
            Text(
                "¿Olvidaste tu contraseña?",
                modifier = Modifier
                    .align(Alignment.End)
                    .padding(top = 2.dp)
                    .clickable(onClick = onRecover),
                color = OgtColors.secondary,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }
        Row(
            Modifier.fillMaxWidth().padding(top = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Checkbox(
                keep,
                { keep = it },
                colors = CheckboxDefaults.colors(
                    checkedColor = OgtColors.secondary,
                    uncheckedColor = OgtColors.muted,
                    checkmarkColor = OgtColors.onPrimary,
                ),
            )
            Text("Mantener sesión activa", fontSize = 13.sp, color = OgtColors.muted)
        }
        AuthError(auth.error)
        OgtPrimaryButton(
            if (auth.busy) "Ingresando…" else if (byPhone) "Recibir código" else "Ingresar",
            enabled = !auth.busy,
        ) {
            scope.launch {
                val id = if (byPhone) phone else email
                val ok = auth.signInIdentifier(id, password, keep)
                when {
                    auth.pendingSecondFactor != null -> onOtp()
                    auth.pendingPhone != null -> onOtp()
                    ok -> onEnter()
                }
            }
        }
        Spacer(Modifier.height(14.dp))
        LoginOrDivider()
        Spacer(Modifier.height(10.dp))
        AuthGoogleButton {
            scope.launch {
                val ok = auth.signInProvider("google")
                when {
                    auth.pendingSecondFactor != null -> onOtp()
                    ok -> onEnter()
                }
            }
        }
        if (biometric) {
            Spacer(Modifier.height(8.dp))
            Text(
                "Huella o rostro",
                modifier = Modifier
                    .fillMaxWidth()
                    .height(OgtDimens.iconButton)
                    .clip(RoundedCornerShape(OgtDimens.buttonRadius))
                    .clickable {
                        scope.launch {
                            if (auth.unlockWithBiometric()) {
                                auth.enableBiometricAfterLogin()
                                onEnter()
                            }
                        }
                    }
                    .padding(vertical = 12.dp),
                color = OgtColors.ink,
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp,
                textAlign = TextAlign.Center,
            )
        }
        if (!imeOpen) Spacer(Modifier.weight(1f))
        GhostLink("¿Aún no eres parte de la red? Regístrate aquí") { onSignUp() }
    }
}

@Composable
private fun LoginMode(byPhone: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(OgtDimens.buttonRadius))
            .background(OgtColors.sand)
            .padding(3.dp),
    ) {
        LoginModeChip("Correo", selected = !byPhone, Modifier.weight(1f)) { onChange(false) }
        LoginModeChip("Teléfono", selected = byPhone, Modifier.weight(1f)) { onChange(true) }
    }
}

@Composable
private fun LoginModeChip(label: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Box(
        modifier
            .clip(RoundedCornerShape(8.dp))
            .background(if (selected) OgtColors.surface else OgtColors.sand)
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            color = if (selected) OgtColors.ink else OgtColors.muted,
            fontWeight = FontWeight.SemiBold,
            fontSize = 14.sp,
        )
    }
}

@Composable
private fun LoginOrDivider() {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.weight(1f).height(1.dp).background(OgtColors.hairline))
        Text(
            "o",
            modifier = Modifier.padding(horizontal = 10.dp),
            color = OgtColors.muted,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
        )
        Box(Modifier.weight(1f).height(1.dp).background(OgtColors.hairline))
    }
}
