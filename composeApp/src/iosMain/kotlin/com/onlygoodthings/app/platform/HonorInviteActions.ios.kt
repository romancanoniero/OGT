package com.onlygoodthings.app.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

@Composable
actual fun rememberHonorInviteActions(): HonorInviteActions {
    var allowed by remember { mutableStateOf(false) }
    var contacts by remember { mutableStateOf<List<DeviceContact>>(emptyList()) }
    fun refresh() {
        contacts = parseContactsBlob(IosHonorInviteRuntime.host?.contactsBlob().orEmpty())
    }
    LaunchedEffect(Unit) {
        IosHonorInviteRuntime.host?.requestContacts { ok ->
            allowed = ok
            if (ok) refresh()
        }
    }
    return HonorInviteActions(
        contacts = contacts,
        contactsAllowed = allowed,
        requestContacts = {
            IosHonorInviteRuntime.host?.requestContacts { ok ->
                allowed = ok
                if (ok) refresh()
            }
        },
        shareWhatsApp = { phone, text ->
            IosHonorInviteRuntime.host?.shareWhatsApp(phone, text) ?: sharePlainText(text)
        },
        sendSms = { phone, text ->
            IosHonorInviteRuntime.host?.sendSms(phone, text) ?: sharePlainText(text)
        },
    )
}

interface IosHonorInviteHost {
    fun requestContacts(onResult: (Boolean) -> Unit)
    fun contactsBlob(): String
    fun shareWhatsApp(phone: String, text: String)
    fun sendSms(phone: String, text: String)
}

object IosHonorInviteRuntime {
    var host: IosHonorInviteHost? = null
}

@OptIn(ExperimentalEncodingApi::class)
private fun decodeContactPhotoB64(raw: String): ByteArray? =
    runCatching { Base64.decode(raw) }.getOrNull()

internal fun parseContactsBlob(blob: String): List<DeviceContact> {
    val seen = mutableSetOf<String>()
    return blob.lineSequence().mapNotNull { line ->
        val parts = line.split('\u001f')
        if (parts.size < 2) return@mapNotNull null
        val name = parts[0].trim()
        val phone = parts[1].trim()
        val digits = phone.filter { it.isDigit() }
        if (digits.length < 8 || !seen.add(digits)) null
        else DeviceContact(
            name = name.ifBlank { phone },
            phone = phone,
            photoBytes = parts.getOrNull(2)?.takeIf { it.isNotBlank() }?.let { decodeContactPhotoB64(it) },
        )
    }.toList()
}
