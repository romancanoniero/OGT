package com.onlygoodthings.app.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.onlygoodthings.app.map.decodeTileBitmap
import com.onlygoodthings.app.platform.DeviceContact
import com.onlygoodthings.app.platform.filterDeviceContacts
import com.onlygoodthings.app.platform.rememberHonorInviteActions
import com.onlygoodthings.app.resources.Res
import com.onlygoodthings.app.resources.qs_invite
import com.onlygoodthings.app.resources.qs_search
import com.onlygoodthings.app.theme.OgtColors
import com.onlygoodthings.app.theme.OgtDimens
import com.onlygoodthings.app.theme.ogtOutlinedFieldColors
import com.onlygoodthings.shared.domain.titleCasePersonName
import com.onlygoodthings.shared.domain.HonorChannel
import com.onlygoodthings.shared.domain.HonorWhatsAppPicker
import com.onlygoodthings.shared.domain.honorChannelLabel
import com.onlygoodthings.shared.domain.honorContactOk

/**
 * Bottom sheet: WhatsApp abre su selector; SMS usa la agenda; mail se escribe.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun HonorInviteSheet(
    givenName: String,
    issuerName: String,
    onDismiss: () -> Unit,
    onSend: (givenName: String, channel: HonorChannel, contact: String) -> Unit,
) {
    val actions = rememberHonorInviteActions()
    var name by remember(givenName) { mutableStateOf(titleCasePersonName(givenName)) }
    var channel by remember { mutableStateOf(HonorChannel.WHATSAPP) }
    var contact by remember { mutableStateOf("") }
    var query by remember { mutableStateOf("") }
    val needsAgenda = channel == HonorChannel.SMS
    val readyWhatsApp = name.trim().length >= 2
    LaunchedEffect(needsAgenda, actions.contactsAllowed) {
        if (needsAgenda && !actions.contactsAllowed) actions.requestContacts()
    }
    val hits = remember(query, actions.contacts, channel) {
        if (needsAgenda) filterDeviceContacts(query, actions.contacts) else emptyList()
    }
    val readyMail = name.trim().length >= 2 && honorContactOk(HonorChannel.EMAIL, contact)
    OgtBottomSheet(onDismiss = onDismiss, maxHeight = 620.dp) { sheet ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OgtStitchIcon(Res.drawable.qs_invite, "Invitar", tint = OgtColors.secondary)
                    Text(
                        "Invitar a ${name.ifBlank { "esta persona" }}",
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp,
                        color = OgtColors.ink,
                    )
                }
                Text(
                    "$issuerName le va a contar que hizo algo bueno. El nick lo elige ella al entrar.",
                    color = OgtColors.muted,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(top = 6.dp, bottom = 8.dp),
                )
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = titleCasePersonName(it) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    placeholder = { Text("Nombre con el que la mencionás", color = OgtColors.muted) },
                    shape = RoundedCornerShape(OgtDimens.buttonRadius),
                    colors = ogtOutlinedFieldColors(),
                )
                Spacer(Modifier.height(10.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    HonorChannel.entries.forEach { option ->
                        val on = option == channel
                        Column(Modifier.clickable { channel = option; query = "" }) {
                            OgtPill(
                                honorChannelLabel(option),
                                if (on) OgtColors.secondary else OgtColors.sand,
                                if (on) OgtColors.onPrimary else OgtColors.ink,
                            )
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
                if (channel == HonorChannel.WHATSAPP) {
                    Text(
                        "Se abre WhatsApp para que elijas el chat. Ahí ves las fotos de perfil; nosotros no leemos esa lista.",
                        color = OgtColors.muted,
                        fontSize = 13.sp,
                    )
                    Spacer(Modifier.height(12.dp))
                    OgtPrimaryButton("Abrir WhatsApp", enabled = readyWhatsApp) {
                        onSend(name.trim(), HonorChannel.WHATSAPP, HonorWhatsAppPicker)
                    }
                } else if (channel == HonorChannel.EMAIL) {
                    Text("Escribí el correo. Después se abre la hoja para enviarlo.", color = OgtColors.muted, fontSize = 13.sp)
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = contact,
                        onValueChange = { contact = it },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        placeholder = { Text("su correo", color = OgtColors.muted) },
                        shape = RoundedCornerShape(OgtDimens.buttonRadius),
                        colors = ogtOutlinedFieldColors(),
                    )
                    Spacer(Modifier.height(12.dp))
                    OgtPrimaryButton("Enviar invitación", enabled = readyMail) {
                        onSend(name.trim(), HonorChannel.EMAIL, contact)
                    }
                } else {
                    Text(
                        "Buscá en la agenda y se abre el SMS.",
                        color = OgtColors.muted,
                        fontSize = 13.sp,
                    )
                    Spacer(Modifier.height(8.dp))
                    if (!actions.contactsAllowed) {
                        OgtCaption("Para listar la agenda necesitamos el permiso de contactos. No subimos tu libreta.")
                        Spacer(Modifier.height(8.dp))
                        OgtPrimaryButton("Permitir agenda") { actions.requestContacts() }
                    } else {
                        OutlinedTextField(
                            value = query,
                            onValueChange = { query = it },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            leadingIcon = { OgtStitchIcon(Res.drawable.qs_search, "Buscar", tint = OgtColors.muted) },
                            placeholder = { Text("Nombre o teléfono", color = OgtColors.muted) },
                            shape = RoundedCornerShape(OgtDimens.buttonRadius),
                            colors = ogtOutlinedFieldColors(),
                        )
                        Spacer(Modifier.height(8.dp))
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .heightIn(max = 280.dp)
                                .verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(2.dp),
                        ) {
                            if (hits.isEmpty()) {
                                OgtCaption(
                                    if (actions.contacts.isEmpty()) "No hay teléfonos en la agenda."
                                    else "Ningún contacto coincide.",
                                )
                            } else {
                                hits.forEach { hit ->
                                    ContactRow(hit) {
                                        val mention = name.trim().ifBlank { hit.name }
                                        onSend(mention, channel, hit.phone)
                                    }
                                }
                            }
                        }
                    }
                }
                Spacer(Modifier.height(4.dp))
                GhostLink("Ahora no", sheet.close)
                Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun ContactRow(contact: DeviceContact, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(OgtDimens.buttonRadius))
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        val photo = remember(contact.photoBytes) {
            contact.photoBytes?.let(::decodeTileBitmap)
        }
        Box(
            Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(OgtColors.sand)
                .border(1.dp, OgtColors.hairline, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            if (photo != null) {
                Image(
                    bitmap = photo,
                    contentDescription = contact.name,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
            } else {
                Text(
                    contact.name.trim().firstOrNull()?.uppercase() ?: "#",
                    fontWeight = FontWeight.SemiBold,
                    color = OgtColors.ink,
                )
            }
        }
        Column(Modifier.weight(1f)) {
            Text(contact.name, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = OgtColors.ink)
            OgtCaption(contact.phone)
        }
    }
}
