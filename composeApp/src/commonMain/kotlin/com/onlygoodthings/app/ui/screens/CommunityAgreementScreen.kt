package com.onlygoodthings.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.onlygoodthings.app.data.LocalAuth
import com.onlygoodthings.app.data.LocalOgtSession
import com.onlygoodthings.app.i18n.LocalOgtCopy
import com.onlygoodthings.app.i18n.OgtLang
import com.onlygoodthings.app.theme.OgtColors
import com.onlygoodthings.app.ui.components.*
import kotlinx.coroutines.launch

/** The same account-level gate covers email, SMS, Apple, Google and restored sessions. */
@Composable
fun CommunityAgreementScreen(onAccepted: () -> Unit, onSignOut: () -> Unit) {
    val auth = LocalAuth.current
    val owner = LocalOgtSession.current.me().id
    val scope = rememberCoroutineScope()
    val links = LocalUriHandler.current
    val english = LocalOgtCopy.current.lang == OgtLang.EN
    fun copy(es: String, en: String) = if (english) en else es
    var adult by remember(owner) { mutableStateOf(false) }
    var accepted by remember(owner) { mutableStateOf(false) }
    var deleteOpen by remember(owner) { mutableStateOf(false) }
    var deleteText by remember(owner) { mutableStateOf("") }
    Column(Modifier.fillMaxSize().background(OgtColors.canvas).safeDrawingPadding().verticalScroll(rememberScrollState())) {
        OgtTopBar(copy("Nuestra comunidad", "Our community"))
        ScreenColumn {
            OgtSectionTitle(copy("Antes de continuar", "Before you continue"))
            OgtCaption(copy("Only Good Things es una comunidad para mayores de 18 años. Cuidamos el trato, la privacidad y la seguridad de las personas y los animales.", "Only Good Things is a community for adults aged 18 and over. We care about respect, privacy and the safety of people and animals."))
            Row(Modifier.fillMaxWidth().toggleable(value = adult, enabled = !auth.busy, role = Role.Checkbox, onValueChange = { adult = it })) {
                Checkbox(checked = adult, onCheckedChange = null, enabled = !auth.busy)
                Text(copy("Confirmo que tengo 18 años o más y que alcanzo la mayoría de edad en mi país.", "I confirm I am at least 18 years old and have reached the age of majority in my country."), Modifier.padding(top = 12.dp))
            }
            TextButton(onClick = { links.openUri("https://onlygoodthings.lat/terms") }) { Text(copy("Leer términos y normas de la comunidad", "Read the terms and community rules")) }
            TextButton(onClick = { links.openUri("https://onlygoodthings.lat/privacy") }) { Text(copy("Leer política de privacidad", "Read the privacy policy")) }
            TextButton(onClick = { links.openUri("https://onlygoodthings.lat/terms#seguridad-infantil") }) { Text(copy("Seguridad infantil y cómo denunciar", "Child safety and reporting")) }
            Row(Modifier.fillMaxWidth().toggleable(value = accepted, enabled = !auth.busy, role = Role.Checkbox, onValueChange = { accepted = it })) {
                Checkbox(checked = accepted, onCheckedChange = null, enabled = !auth.busy)
                Text(copy("Leí y acepto los términos y las normas de la comunidad. Leí la política de privacidad.", "I have read and agree to the terms and community rules. I have read the privacy policy."), Modifier.padding(top = 12.dp))
            }
            OgtCaption(copy("Prohibimos el abuso y la explotación sexual infantil, el acoso, las amenazas y el contenido sexual. Podés denunciar y bloquear desde la app.", "Child sexual abuse and exploitation, harassment, threats and sexual content are prohibited. You can report and block in the app."))
            auth.error?.let { Text(it, color = OgtColors.error) }
            OgtPrimaryButton(if (auth.busy) copy("Confirmando…", "Confirming…") else copy("Aceptar y continuar", "Agree and continue"), enabled = adult && accepted && !auth.busy) {
                scope.launch { if (auth.acceptCommunityConsent()) onAccepted() }
            }
            TextButton(onClick = onSignOut, enabled = !auth.busy) { Text(copy("Cerrar sesión", "Sign out")) }
            TextButton(onClick = { auth.error = null; deleteOpen = true }, enabled = !auth.busy) { Text(copy("Borrar mi cuenta sin aceptar", "Delete my account without agreeing")) }
            Spacer(Modifier.height(24.dp))
        }
    }
    if (deleteOpen) AlertDialog(
        onDismissRequest = { if (!auth.busy) deleteOpen = false },
        title = { Text(copy("Borrar mi cuenta", "Delete my account")) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(copy("Se borrará tu cuenta y el contenido que publicaste. Las copias que otras personas ya tengan, los resguardos técnicos breves y los registros exigidos por ley se conservan solo durante el plazo necesario. Si hay una interrupción, el proceso continúa automáticamente. Escribí BAJA para confirmar.", "Your account and the content you published will be deleted. Copies others already have, short technical backups and legally required records are kept only as long as necessary. If interrupted, the process continues automatically. Type BAJA to confirm."))
                OutlinedTextField(value = deleteText, onValueChange = { deleteText = it.uppercase().take(8) }, label = { Text(copy("Confirmación: BAJA", "Confirmation: BAJA")) }, singleLine = true, enabled = !auth.busy)
                auth.error?.let { Text(it, color = OgtColors.error) }
            }
        },
        confirmButton = {
            TextButton(enabled = deleteText == "BAJA" && !auth.busy, onClick = {
                scope.launch { if (auth.deleteAccount()) { deleteOpen = false; onSignOut() } }
            }) { Text(if (auth.busy) copy("Borrando…", "Deleting…") else copy("Borrar mi cuenta", "Delete my account")) }
        },
        dismissButton = { TextButton(enabled = !auth.busy, onClick = { deleteOpen = false }) { Text(copy("Cancelar", "Cancel")) } },
    )
}
