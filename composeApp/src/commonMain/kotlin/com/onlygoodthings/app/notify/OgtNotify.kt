package com.onlygoodthings.app.notify

import androidx.compose.runtime.Composable
import com.onlygoodthings.shared.data.local.LocalNotification
import com.onlygoodthings.shared.domain.noticePresentation
import kotlinx.datetime.toLocalDateTime

data class OgtNotifyState(
    val permissionGranted: Boolean,
    val token: String?,
    val platform: String,
    val requestPermission: () -> Unit,
    /** Vuelve a pedir el diálogo. Si el sistema ya no lo muestra, abre los ajustes. */
    val retryPermission: () -> Unit,
)

/** Permiso + token FCM. Las acciones abren el diálogo nativo. */
@Composable
expect fun rememberOgtNotify(): OgtNotifyState

/** Banner del sistema (bandeja / Notification Center). */
expect fun showSystemNotice(notice: LocalNotification)
expect fun currentPushToken(): String?
expect fun clearSystemNotices()

class OgtPushDecision(val sound: Boolean)

object OgtPushPresentation {
    fun forRemote(recipientUserId: String, kind: String, sound: Boolean, quietHoursEnabled: Boolean): OgtPushDecision? =
        currentNoticePresentation(LocalNotification("push",kind,"","","",false,recipientUserId=recipientUserId,
            serverDelivered=true,serverSound=sound,serverQuietHours=quietHoursEnabled))?.let { OgtPushDecision(it.sound) }
}

internal fun currentNoticePresentation(notice: LocalNotification): com.onlygoodthings.shared.domain.NoticePresentation? {
    val prefs = com.onlygoodthings.app.auth.AuthPrefs()
    if(prefs.noticeOwnerId.isBlank() || (notice.recipientUserId != null && notice.recipientUserId != prefs.noticeOwnerId))return null
    val hour = kotlinx.datetime.Clock.System.now().toLocalDateTime(kotlinx.datetime.TimeZone.currentSystemDefault()).hour
    if(notice.serverDelivered)return com.onlygoodthings.shared.domain.NoticePresentation(true,
        notice.serverSound && !(notice.serverQuietHours && (hour>=22 || hour<7)))
    val settings = runCatching { kotlinx.serialization.json.Json.decodeFromString<com.onlygoodthings.shared.domain.AccountSettings>(prefs.noticeSettingsJson) }.getOrNull() ?: return null
    return settings.noticePresentation(notice.kind,hour).takeIf { it.deliver }
}
