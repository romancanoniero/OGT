package com.onlygoodthings.app.platform

import com.onlygoodthings.shared.domain.parseHonorClipboard
import platform.UIKit.UIPasteboard

actual fun readHonorClipboard(): String? =
    parseHonorClipboard(UIPasteboard.generalPasteboard.string.orEmpty())

actual fun startHonorInstallReferrer(onToken: (String) -> Unit) = Unit
