package com.onlygoodthings.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.autofill.ContentType
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.contentType
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.onlygoodthings.app.auth.OtpArrivalEffect
import com.onlygoodthings.app.auth.SecondFactorKind
import com.onlygoodthings.app.auth.otpDigits
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
    var pasteNote by remember { mutableStateOf<String?>(null) }
    var submitting by remember { mutableStateOf(false) }
    val clipboard = LocalClipboardManager.current
    val factor = auth.pendingSecondFactor
    val sms = factor?.kind != SecondFactorKind.Totp
    val phone = auth.pendingPhone?.phoneE164 ?: "tu número"
    val title = if (factor != null) "Código extra" else "Verifica tu número"
    val caption = when (factor?.kind) {
        SecondFactorKind.Totp ->
            "Esta cuenta ya tiene un paso extra. Ingresá el código de 6 dígitos de tu app de autenticación."
        SecondFactorKind.Sms ->
            "Esta cuenta ya tiene un paso extra. Enviamos un código de 6 dígitos a ${factor?.hint?.ifBlank { null } ?: "tu teléfono"}."
        null ->
            "Enviamos un código de 6 dígitos a $phone. Si llega por SMS, el teclado lo ofrece arriba."
    }
    fun submit() {
        if (submitting || auth.busy || code.length != 6) return
        submitting = true
        val typed = code
        scope.launch {
            val ok = auth.confirmOtp(typed)
            submitting = false
            if (ok) onConfirm() else code = ""
        }
    }
    if (sms) {
        OtpArrivalEffect { received ->
            code = received
            pasteNote = null
        }
    }
    LaunchedEffect(code) {
        if (code.length == 6) submit()
    }
    AuthScaffold(onBack = onBack) {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            OgtLogo(size = 80.dp)
            OgtSectionTitle(title)
            OgtCaption(caption)
            GhostLink(if (factor == null) "¿Número incorrecto? Editar" else "Volver") { onBack() }
        }
        OtpCells(code, smsAutofill = sms) { next ->
            code = next
            pasteNote = null
        }
        GhostLink("Pegar código") {
            val digits = otpDigits(clipboard.getText()?.text.orEmpty())
            if (digits == null) {
                pasteNote = "No hay un código de 6 dígitos para pegar."
            } else {
                code = digits
                pasteNote = null
            }
        }
        if (pasteNote != null) OgtCaption(pasteNote!!)
        if (sms) {
            GhostLink("Reenviar código") { scope.launch { auth.resendOtp() } }
        }
        AuthError(auth.error)
        OgtPrimaryButton(
            if (auth.busy || submitting) "Confirmando…" else if (factor != null) "Confirmar" else "Confirmar y activar cuenta",
            enabled = !auth.busy && !submitting && code.length == 6,
        ) { submit() }
        OgtPill("Comunidad segura: verificamos a cada persona")
    }
}

/** Un solo campo. Las seis casillas son el ingreso, no un adorno encima de otro. */
@Composable
private fun OtpCells(code: String, smsAutofill: Boolean, onCode: (String) -> Unit) {
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    val active = if (code.length >= 6) 5 else code.length
    BasicTextField(
        value = code,
        onValueChange = { raw -> onCode(raw.filter { it.isDigit() }.take(6)) },
        modifier = Modifier
            .focusRequester(focus)
            .fillMaxWidth()
            .semantics {
                contentDescription = "Código de 6 dígitos"
                if (smsAutofill) contentType = ContentType.SmsOtpCode
            },
        textStyle = TextStyle(color = OgtColors.canvas, fontSize = 1.sp),
        cursorBrush = SolidColor(OgtColors.canvas),
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Number,
            imeAction = ImeAction.Done,
        ),
        singleLine = true,
        decorationBox = { inner ->
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                ) {
                    repeat(6) { i ->
                        Box(
                            Modifier
                                .size(48.dp, 56.dp)
                                .clip(AuthCorner)
                                .background(OgtColors.surface)
                                .border(
                                    1.dp,
                                    if (i == active) OgtColors.secondary else OgtColors.sand,
                                    AuthCorner,
                                ),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                code.getOrNull(i)?.toString().orEmpty(),
                                fontWeight = FontWeight.Bold,
                                fontSize = 22.sp,
                                color = OgtColors.primary,
                            )
                        }
                    }
                }
                Box(Modifier.matchParentSize().alpha(0.01f)) { inner() }
            }
        },
    )
}
