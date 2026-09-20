package com.onlygoodthings.app.notify

import androidx.compose.runtime.Composable
import com.onlygoodthings.shared.data.local.LocalNotification

data class OgtNotifyState(
    val permissionGranted: Boolean,
    val token: String?,
    val platform: String,
    val requestPermission: () -> Unit,
)

/** Permiso + token FCM. Las acciones abren el diálogo nativo. */
@Composable
expect fun rememberOgtNotify(): OgtNotifyState

/** Banner del sistema (bandeja / Notification Center). */
expect fun showSystemNotice(notice: LocalNotification)
