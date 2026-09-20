package com.onlygoodthings.app.platform

import android.content.ClipboardManager
import android.content.Context
import com.android.installreferrer.api.InstallReferrerClient
import com.android.installreferrer.api.InstallReferrerStateListener
import com.onlygoodthings.app.OgtApplication
import com.onlygoodthings.shared.domain.parseHonorClipboard
import com.onlygoodthings.shared.domain.parseHonorReferrer

actual fun readHonorClipboard(): String? {
    val context = OgtApplication.instance
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return null
    val text = runCatching {
        clipboard.primaryClip?.getItemAt(0)?.coerceToText(context)?.toString()
    }.getOrNull().orEmpty()
    return parseHonorClipboard(text)
}

actual fun startHonorInstallReferrer(onToken: (String) -> Unit) {
    val context = OgtApplication.instance
    val client = InstallReferrerClient.newBuilder(context).build()
    client.startConnection(object : InstallReferrerStateListener {
        override fun onInstallReferrerSetupFinished(responseCode: Int) {
            if (responseCode == InstallReferrerClient.InstallReferrerResponse.OK) {
                runCatching { client.installReferrer.installReferrer }
                    .getOrNull()
                    ?.let { parseHonorReferrer(it) }
                    ?.let(onToken)
            }
            runCatching { client.endConnection() }
        }

        override fun onInstallReferrerServiceDisconnected() = Unit
    })
}
