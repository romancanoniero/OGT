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

actual suspend fun shareAccountExport(json: String, isCurrentOwner: () -> Boolean) {
    val context = runCatching { AndroidAuthHost.requireActivity() }.getOrElse { OgtApplication.instance }
    val file = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        val directory = java.io.File(context.cacheDir,"ogt-media/exports").apply { mkdirs() }
        directory.listFiles()?.filter { it.isFile && it.lastModified() < System.currentTimeMillis()-86_400_000 }?.forEach { it.delete() }
        java.io.File(directory,"OnlyGoodThings-mis-datos-${java.util.UUID.randomUUID()}.json").apply { writeText(json) }
    }
    val uri = androidx.core.content.FileProvider.getUriForFile(context,"${context.packageName}.fileprovider",file)
    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
        check(isCurrentOwner()) { "La sesión cambió" }
        val send=Intent(Intent.ACTION_SEND).apply {
            type="application/json";putExtra(Intent.EXTRA_STREAM,uri)
            clipData=android.content.ClipData.newRawUri("Mis datos de OGT",uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(send,"Guardar copia de mis datos").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}
