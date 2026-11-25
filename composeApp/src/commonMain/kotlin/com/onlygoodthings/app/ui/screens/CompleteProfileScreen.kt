package com.onlygoodthings.app.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import com.onlygoodthings.app.data.LocalAuth
import com.onlygoodthings.app.data.LocalOgtSession
import com.onlygoodthings.app.ui.components.GhostLink
import com.onlygoodthings.app.ui.components.OgtCaption
import com.onlygoodthings.app.ui.components.OgtPrimaryButton
import com.onlygoodthings.app.ui.components.OgtSectionTitle
import kotlinx.coroutines.launch

@Composable
fun CompleteProfileScreen(onSaved: () -> Unit, onSignOut: () -> Unit) {
    val auth = LocalAuth.current
    val session = LocalOgtSession.current
    val me = session.me()
    val scope = rememberCoroutineScope()
    var name by remember(me.id) { mutableStateOf(me.displayName.takeUnless {
        '@' in it || it == me.firebaseUid || it in setOf("Vecino", "Alguien de la comunidad")
    }.orEmpty()) }
    var barrio by remember(me.id) { mutableStateOf(session.accountSettings.barrio) }
    var savingPhoto by remember(me.id) { mutableStateOf(false) }
    AuthScaffold {
        OgtSectionTitle("Completá tu perfil")
        OgtCaption("Tu cuenta ya está creada. Confirmá tu nombre, tu zona o comunidad y tu foto para continuar.")
        ProfilePhotoEditor(enabled = !auth.busy, onSavingChanged = { savingPhoto = it })
        AuthField("Nombre y apellido", "Tu nombre", name, prose = true) { name = it }
        AuthField("Tu zona o comunidad", "Ej. Palermo, Buenos Aires", barrio, prose = true) { barrio = it }
        AuthError(auth.error)
        OgtPrimaryButton(if (auth.busy || savingPhoto) "Guardando…" else "Guardar y continuar", enabled = !auth.busy && !savingPhoto) {
            scope.launch { if (auth.completeProfile(name, barrio)) onSaved() }
        }
        GhostLink("Cerrar sesión") { if (!auth.busy && !savingPhoto) onSignOut() }
    }
}
