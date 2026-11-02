package com.onlygoodthings.app.notify

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.onlygoodthings.shared.data.local.LocalNotification
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.delay
import platform.UserNotifications.UNMutableNotificationContent
import platform.UserNotifications.UNNotificationRequest
import platform.UserNotifications.UNNotificationSound
import platform.UserNotifications.UNTimeIntervalNotificationTrigger
import platform.UserNotifications.UNUserNotificationCenter
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue

/** Swift pide el permiso y registra APNs; Kotlin no toca UIKit en el callback. */
interface IosPushHost {
    fun requestPermission()
}

@Composable
actual fun rememberOgtNotify(): OgtNotifyState {
    var granted by remember { mutableStateOf(OgtPushRuntime.permissionGranted) }
    var token by remember { mutableStateOf(OgtPushRuntime.token) }
    val lifecycleOwner=androidx.lifecycle.compose.LocalLifecycleOwner.current
    androidx.compose.runtime.DisposableEffect(lifecycleOwner) {
        val observer=androidx.lifecycle.LifecycleEventObserver { _,event ->
            if(event==androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                UNUserNotificationCenter.currentNotificationCenter().getNotificationSettingsWithCompletionHandler { settings ->
                    onMain { OgtPushRuntime.permissionGranted = settings?.authorizationStatus == platform.UserNotifications.UNAuthorizationStatusAuthorized || settings?.authorizationStatus == platform.UserNotifications.UNAuthorizationStatusProvisional }
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(Unit) {
        while (true) {
            granted = OgtPushRuntime.permissionGranted
            token = OgtPushRuntime.token
            delay(800)
        }
    }
    val ask: () -> Unit = {
        OgtPushRuntime.host?.requestPermission()
        Unit
    }
    return OgtNotifyState(
        permissionGranted = granted,
        token = token,
        platform = "IOS",
        requestPermission = ask,
        retryPermission = ask,
    )
}

actual fun showSystemNotice(notice: LocalNotification) {
    onMain {
        val presentation = currentNoticePresentation(notice) ?: return@onMain
        val content = UNMutableNotificationContent().apply {
            setTitle(notice.title)
            setBody(notice.body)
            if(presentation.sound)setSound(UNNotificationSound.defaultSound)
            setUserInfo(
                mapOf(
                    "kind" to notice.kind,
                    "recipientUserId" to com.onlygoodthings.app.auth.AuthPrefs().noticeOwnerId,
                    "sound" to presentation.sound.toString(),
                    "quietHoursEnabled" to runCatching { kotlinx.serialization.json.Json.decodeFromString<com.onlygoodthings.shared.domain.AccountSettings>(com.onlygoodthings.app.auth.AuthPrefs().noticeSettingsJson).quietHoursEnabled }.getOrDefault(true).toString(),
                    "postId" to (notice.postId ?: ""),
                    "deepLink" to (notice.deepLink ?: ""),
                ),
            )
        }
        val trigger = UNTimeIntervalNotificationTrigger.triggerWithTimeInterval(0.15, repeats = false)
        val request = UNNotificationRequest.requestWithIdentifier(notice.id, content, trigger)
        UNUserNotificationCenter.currentNotificationCenter().addNotificationRequest(request, null)
    }
}

object OgtPushRuntime {
    var host: IosPushHost? = null
    var token: String? = null
    var permissionGranted: Boolean = false
}

actual fun currentPushToken(): String? = OgtPushRuntime.token
actual fun clearSystemNotices() {
    UNUserNotificationCenter.currentNotificationCenter().removeAllDeliveredNotifications()
    UNUserNotificationCenter.currentNotificationCenter().removeAllPendingNotificationRequests()
}

@OptIn(ExperimentalForeignApi::class)
internal fun onMain(block: () -> Unit) {
    dispatch_async(dispatch_get_main_queue()) { block() }
}
