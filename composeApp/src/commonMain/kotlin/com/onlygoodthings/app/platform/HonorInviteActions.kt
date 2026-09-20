package com.onlygoodthings.app.platform

import androidx.compose.runtime.Composable

data class DeviceContact(
    val name: String,
    val phone: String,
    val photoBytes: ByteArray? = null,
) {
    val digits: String get() = phone.filter { it.isDigit() }
}

class HonorInviteActions(
    val contacts: List<DeviceContact>,
    val contactsAllowed: Boolean,
    val requestContacts: () -> Unit,
    val shareWhatsApp: (phone: String, text: String) -> Unit,
    val sendSms: (phone: String, text: String) -> Unit,
)

/** Agenda del dispositivo para el bottom sheet de invitación. */
@Composable
expect fun rememberHonorInviteActions(): HonorInviteActions

fun filterDeviceContacts(query: String, contacts: List<DeviceContact>): List<DeviceContact> {
    val q = query.trim().lowercase()
    val qDigits = q.filter { it.isDigit() }
    val hits = if (q.isEmpty()) {
        contacts
    } else {
        contacts.filter { hit ->
            hit.name.lowercase().contains(q) ||
                (qDigits.length >= 3 && hit.digits.contains(qDigits)) ||
                hit.phone.contains(q)
        }
    }
    return hits.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name.trim() })
}
