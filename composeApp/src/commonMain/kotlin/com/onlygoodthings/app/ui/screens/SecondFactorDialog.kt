package com.onlygoodthings.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.onlygoodthings.app.auth.AuthController
import com.onlygoodthings.app.auth.validSecondFactorCode
import kotlinx.coroutines.launch

/** Continues the same Firebase sign-in; it never enrolls, resets or removes a factor. */
@Composable
fun SecondFactorDialog(auth: AuthController, onAuthenticated: () -> Unit) {
    if (auth.secondFactors.isEmpty()) return
    val scope = rememberCoroutineScope()
    val selected = auth.selectedSecondFactor
    var code by remember(selected) { mutableStateOf("") }
    fun choose(index: Int) {
        code = ""
        scope.launch { if (auth.chooseSecondFactor(index)) onAuthenticated() }
    }
    LaunchedEffect(auth.secondFactors) {
        val only = auth.secondFactors.singleOrNull()
        if (only?.kind == "totp" && selected == null) {
            if (auth.chooseSecondFactor(only.index)) onAuthenticated()
        }
    }
    AlertDialog(
        onDismissRequest = { auth.cancelSecondFactor() },
        title = { Text("Confirmá que sos vos") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (auth.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                if (selected == null) {
                    Text("Para completar el ingreso, elegí el segundo factor de tu cuenta.")
                    auth.secondFactors.forEach { factor ->
                        TextButton(onClick = { choose(factor.index) }, enabled = !auth.busy) {
                            Text(if (factor.kind == "phone") "Enviar código por ${factor.label}" else factor.label)
                        }
                    }
                } else {
                    Text(if (selected.kind == "totp") "Ingresá el código de seis dígitos de tu aplicación autenticadora." else "Ingresá el código que enviamos a ${selected.label}.")
                    AuthField("Código de verificación", "000000", code, otp = true) { next ->
                        code = next.filter { it in '0'..'9' }.take(6)
                    }
                    if (selected.kind == "phone") {
                        TextButton(onClick = { choose(selected.index) }, enabled = !auth.busy) { Text("Reenviar código") }
                    }
                    if (auth.secondFactors.size > 1) {
                        auth.secondFactors.filter { it.index != selected.index }.forEach { factor ->
                            TextButton(onClick = { choose(factor.index) }, enabled = !auth.busy) { Text("Usar ${factor.label}") }
                        }
                    }
                }
                AuthError(auth.error)
            }
        },
        confirmButton = {
            if (selected != null) TextButton(
                enabled = !auth.busy && validSecondFactorCode(code),
                onClick = { scope.launch { if (auth.confirmSecondFactor(code)) onAuthenticated() } },
            ) { Text(if (auth.busy) "Verificando…" else "Verificar e ingresar") }
        },
        dismissButton = {
            TextButton(onClick = { auth.cancelSecondFactor() }) { Text("Volver al ingreso") }
        },
    )
}
