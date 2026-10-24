package com.onlygoodthings.app.notify

import android.Manifest
import android.app.Activity
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.google.firebase.messaging.FirebaseMessaging
import com.onlygoodthings.app.MainActivity
import com.onlygoodthings.app.R
import com.onlygoodthings.shared.data.local.LocalNotification

const val ChannelMentions = "ogt_mentions"
const val ChannelCommunity = "ogt_community"

@Composable
actual fun rememberOgtNotify(): OgtNotifyState {
    val context = LocalContext.current
    var granted by remember { mutableStateOf(notifyGranted(context)) }
    var token by remember { mutableStateOf<String?>(null) }
    var asked by remember { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        granted = notifyGranted(context)
    }
    LaunchedEffect(Unit) {
        ensureChannels(context)
        OgtPushToken.latest?.let { token = it }
        FirebaseMessaging.getInstance().token.addOnSuccessListener {
            OgtPushToken.latest = it
            token = it
        }
    }
    fun ask(insist: Boolean) {
        if (Build.VERSION.SDK_INT < 33) {
            granted = true
            return
        }
        if (notifyGranted(context)) {
            granted = true
            return
        }
        val activity = context.findActivity()
        val rationale = activity != null && ActivityCompat.shouldShowRequestPermissionRationale(
            activity,
            Manifest.permission.POST_NOTIFICATIONS,
        )
        if (asked && !rationale) {
            if (insist) openNotificationSettings(context)
            return
        }
        asked = true
        launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
    return OgtNotifyState(
        permissionGranted = granted,
        token = token,
        platform = "ANDROID",
        requestPermission = { ask(insist = false) },
        retryPermission = { ask(insist = true) },
    )
}

private fun Context.findActivity(): Activity? {
    var current: Context = this
    while (current is ContextWrapper) {
        if (current is Activity) return current
        current = current.baseContext
    }
    return null
}

private fun openNotificationSettings(context: Context) {
    val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
        putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    runCatching { context.startActivity(intent) }.onFailure {
        context.startActivity(
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.fromParts("package", context.packageName, null)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            },
        )
    }
}

actual fun showSystemNotice(notice: LocalNotification) {
    val context = com.onlygoodthings.app.OgtApplication.instance
    ensureChannels(context)
    if (!notifyGranted(context)) return
    val tap = Intent(context, MainActivity::class.java).apply {
        flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        data = android.net.Uri.parse(notice.deepLink ?: "ogt://p/${notice.postId.orEmpty()}")
    }
    val pending = PendingIntent.getActivity(
        context,
        notice.id.hashCode(),
        tap,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )
    val channel = if (notice.kind == "MENTION" || notice.urgent) ChannelMentions else ChannelCommunity
    val builder = NotificationCompat.Builder(context, channel)
        .setSmallIcon(R.mipmap.ic_launcher)
        .setContentTitle(notice.title)
        .setContentText(notice.body)
        .setStyle(NotificationCompat.BigTextStyle().bigText(notice.body))
        .setContentIntent(pending)
        .setAutoCancel(true)
        .setPriority(if (notice.kind == "MENTION" || notice.urgent) NotificationCompat.PRIORITY_HIGH else NotificationCompat.PRIORITY_DEFAULT)
        .setCategory(NotificationCompat.CATEGORY_SOCIAL)
    NotificationManagerCompat.from(context).notify(notice.id.hashCode(), builder.build())
}

fun ensureChannels(context: Context) {
    if (Build.VERSION.SDK_INT < 26) return
    val manager = context.getSystemService(NotificationManager::class.java) ?: return
    if (manager.getNotificationChannel(ChannelMentions) == null) {
        manager.createNotificationChannel(
            NotificationChannel(ChannelMentions, "Menciones", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Cuando alguien te nombra en una buena acción"
            },
        )
    }
    if (manager.getNotificationChannel(ChannelCommunity) == null) {
        manager.createNotificationChannel(
            NotificationChannel(ChannelCommunity, "Comunidad", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "Avisos de la red comunitaria"
            },
        )
    }
}

fun notifyGranted(context: Context): Boolean =
    if (Build.VERSION.SDK_INT < 33) true
    else ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
