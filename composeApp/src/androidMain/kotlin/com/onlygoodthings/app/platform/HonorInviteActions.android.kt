package com.onlygoodthings.app.platform

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.ContactsContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import com.onlygoodthings.app.AndroidAuthHost
import com.onlygoodthings.app.OgtApplication
import java.net.URLEncoder

@Composable
actual fun rememberHonorInviteActions(): HonorInviteActions {
    val context = shareContext()
    var allowed by remember { mutableStateOf(contactsGranted(context)) }
    var contacts by remember { mutableStateOf(if (allowed) loadDeviceContacts(context) else emptyList()) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        allowed = ok
        contacts = if (ok) loadDeviceContacts(shareContext()) else emptyList()
    }
    LaunchedEffect(allowed) {
        if (allowed && contacts.isEmpty()) contacts = loadDeviceContacts(shareContext())
    }
    return HonorInviteActions(
        contacts = contacts,
        contactsAllowed = allowed,
        requestContacts = {
            launcher.launch(Manifest.permission.READ_CONTACTS)
        },
        shareWhatsApp = ::shareViaWhatsApp,
        sendSms = ::sendSmsTo,
    )
}

private fun shareViaWhatsApp(phone: String, text: String) {
    val context = shareContext()
    val digits = phone.filter { it.isDigit() }
    if (digits.length >= 8) {
        val encoded = URLEncoder.encode(text, Charsets.UTF_8.name())
        val uri = android.net.Uri.parse("https://api.whatsapp.com/send?phone=$digits&text=$encoded")
        val view = Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val launched = listOf("com.whatsapp", "com.whatsapp.w4b").any { pkg ->
            runCatching {
                context.startActivity(Intent(view).setPackage(pkg))
                true
            }.getOrDefault(false)
        }
        if (!launched) runCatching { context.startActivity(view) }.getOrElse { sharePlainText(text) }
        return
    }
    // Sin teléfono: selector de chats de WhatsApp (fotos de perfil las muestra WhatsApp).
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, text)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    val opened = listOf("com.whatsapp", "com.whatsapp.w4b").any { pkg ->
        runCatching {
            context.startActivity(Intent(send).setPackage(pkg))
            true
        }.getOrDefault(false)
    }
    if (!opened) sharePlainText(text)
}

private fun sendSmsTo(phone: String, text: String) {
    val context = shareContext()
    val intent = Intent(Intent.ACTION_SENDTO).apply {
        data = android.net.Uri.parse("smsto:$phone")
        putExtra("sms_body", text)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    runCatching { context.startActivity(intent) }.getOrElse { sharePlainText(text) }
}

private fun loadDeviceContacts(context: Context): List<DeviceContact> {
    if (!contactsGranted(context)) return emptyList()
    val seen = mutableSetOf<String>()
    val rows = mutableListOf<DeviceContact>()
    context.contentResolver.query(
        ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
        arrayOf(
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
            ContactsContract.CommonDataKinds.Phone.NUMBER,
            ContactsContract.CommonDataKinds.Phone.PHOTO_THUMBNAIL_URI,
        ),
        "${ContactsContract.CommonDataKinds.Phone.HAS_PHONE_NUMBER}=1",
        null,
        "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} COLLATE LOCALIZED ASC",
    )?.use { cursor ->
        val nameAt = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
        val phoneAt = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
        val photoAt = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.PHOTO_THUMBNAIL_URI)
        while (cursor.moveToNext()) {
            val name = cursor.getString(nameAt).orEmpty().trim()
            val phone = cursor.getString(phoneAt).orEmpty()
            val digits = phone.filter { it.isDigit() }
            if (digits.length < 8 || !seen.add(digits)) continue
            val photo = cursor.getString(photoAt)?.let { thumbUri ->
                runCatching {
                    context.contentResolver.openInputStream(android.net.Uri.parse(thumbUri))?.use { it.readBytes() }
                }.getOrNull()
            }
            rows += DeviceContact(name.ifBlank { phone }, phone, photo)
        }
    }
    return rows
}

private fun contactsGranted(context: Context): Boolean =
    ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) ==
        PackageManager.PERMISSION_GRANTED

private fun shareContext(): Context =
    runCatching { AndroidAuthHost.requireActivity() as Context }.getOrElse { OgtApplication.instance }
