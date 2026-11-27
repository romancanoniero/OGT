package com.onlygoodthings.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.onlygoodthings.app.data.LocalAuth
import com.onlygoodthings.app.i18n.LocalOgtCopy
import com.onlygoodthings.app.i18n.OgtLang
import com.onlygoodthings.app.theme.OgtLogo
import com.onlygoodthings.app.ui.components.OgtCaption
import com.onlygoodthings.app.ui.components.OgtPrimaryButton
import com.onlygoodthings.app.ui.components.OgtSectionTitle
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** Unlock the persisted Firebase identity without presenting the registration/login form. */
@Composable
fun UnlockSessionScreen(onUnlocked: () -> Unit, onUseOtherMethod: () -> Unit) {
    val auth = LocalAuth.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val scope = rememberCoroutineScope()
    val latestUnlocked by rememberUpdatedState(onUnlocked)
    val english = LocalOgtCopy.current.lang == OgtLang.EN
    fun copy(es: String, en: String) = if (english) en else es
    suspend fun unlock() {
        if (auth.unlockWithBiometric()) latestUnlocked()
    }

    // Wait until the app can present the system prompt. Face ID's own lifecycle changes
    // must not automatically start another prompt after cancellation or failure.
    LaunchedEffect(auth, lifecycle) {
        lifecycle.currentStateFlow.first { it.isAtLeast(Lifecycle.State.RESUMED) }
        unlock()
    }

    AuthScaffold {
        Column(
            Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            OgtLogo(size = 80.dp, animated = false)
            OgtSectionTitle(copy("Desbloqueá OGT", "Unlock OGT"))
            OgtCaption(copy(
                "Tu sesión sigue guardada. Confirmá tu identidad para continuar.",
                "Your session is saved. Confirm your identity to continue.",
            ))
        }
        AuthError(auth.error)
        OgtPrimaryButton(
            if (auth.busy) copy("Verificando…", "Verifying…") else copy("Desbloquear", "Unlock"),
            enabled = !auth.busy,
        ) { scope.launch { unlock() } }
        TextButton(onClick = onUseOtherMethod, enabled = !auth.busy) {
            Text(copy("Usar otro método de ingreso", "Use another sign-in method"))
        }
    }
}
