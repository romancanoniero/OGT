package com.onlygoodthings.app.platform

import android.content.Intent
import com.onlygoodthings.app.AndroidAuthHost
import com.onlygoodthings.app.OgtApplication

actual fun sharePlainText(text: String) {
    val context = runCatching { AndroidAuthHost.requireActivity() }.getOrElse { OgtApplication.instance }
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, text)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    context.startActivity(Intent.createChooser(send, "Compartir").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}
