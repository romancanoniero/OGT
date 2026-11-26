package com.onlygoodthings.app.platform

import android.content.Intent
import android.net.Uri
import com.onlygoodthings.app.OgtApplication
import com.onlygoodthings.shared.domain.invitationEmailUri

actual fun sharePostInvitation(text:String,channel:String,contact:String) {
    val intent=when(channel) {
        "SMS" -> Intent(Intent.ACTION_SENDTO,Uri.parse("smsto:"+Uri.encode(contact))).putExtra("sms_body",text)
        "EMAIL" -> Intent(Intent.ACTION_SENDTO,Uri.parse(invitationEmailUri(contact,text)))
        else -> Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT,text)
    }
    val context=OgtApplication.instance
    runCatching { context.startActivity(Intent.createChooser(intent,"Enviar invitación con…").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        .getOrElse { sharePlainText(text) }
}
