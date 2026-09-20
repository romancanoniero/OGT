package com.onlygoodthings.app.platform

import com.onlygoodthings.app.AndroidAuthHost

actual fun isStorePreviewLaunch(): Boolean = runCatching {
    val intent = AndroidAuthHost.requireActivity().intent
    intent.getBooleanExtra("ogt.store_preview", false) ||
        intent.getStringExtra("ogt.store_preview") == "1"
}.getOrDefault(false)

actual fun storePreviewScreenName(): String? = runCatching {
    AndroidAuthHost.requireActivity().intent.getStringExtra("ogt.store_screen")
}.getOrNull()
