package com.onlygoodthings.app.platform

import platform.Foundation.NSProcessInfo

actual fun isStorePreviewLaunch(): Boolean =
    NSProcessInfo.processInfo.arguments.any { it == "-OgtStorePreview" }

actual fun storePreviewScreenName(): String? {
    val args = NSProcessInfo.processInfo.arguments.map { it as String }
    val eq = args.firstOrNull { it.startsWith("-OgtStoreScreen=") }?.substringAfter("=")
    if (!eq.isNullOrBlank()) return eq
    val i = args.indexOf("-OgtStoreScreen")
    return args.getOrNull(i + 1)?.takeIf { it.isNotBlank() && !it.startsWith("-") }
}
