package com.onlygoodthings.app.platform

import platform.UIKit.UIActivityViewController
import platform.UIKit.UIApplication

actual fun sharePlainText(text: String) {
    val sheet = UIActivityViewController(
        activityItems = listOf(text),
        applicationActivities = null,
    )
    UIApplication.sharedApplication.keyWindow?.rootViewController?.presentViewController(
        sheet,
        animated = true,
        completion = null,
    )
}
