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
    LaunchedEffect(Unit) {
        while (true) {
            granted = OgtPushRuntime.permissionGranted
            token = OgtPushRuntime.token
            delay(800)
        }
    }
    return OgtNotifyState(
        permissionGranted = granted,
        token = token,
        platform = "IOS",
        requestPermission = { OgtPushRuntime.host?.requestPermission() },
    )
}

actual fun showSystemNotice(notice: LocalNotification) {
    onMain {
        val content = UNMutableNotificationContent().apply {
            setTitle(notice.title)
            setBody(notice.body)
            setSound(UNNotificationSound.defaultSound)
            setUserInfo(
                mapOf(
                    "kind" to notice.kind,
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

@OptIn(ExperimentalForeignApi::class)
internal fun onMain(block: () -> Unit) {
    dispatch_async(dispatch_get_main_queue()) { block() }
}
