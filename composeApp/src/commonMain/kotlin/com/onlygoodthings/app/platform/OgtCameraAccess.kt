package com.onlygoodthings.app.platform

import androidx.compose.runtime.Composable

data class OgtCameraAccess(
    val granted: Boolean,
    val request: () -> Unit,
)

/** Camera permission only. System photo/video pickers do not require broad library access. */
@Composable
expect fun rememberOgtCameraAccess(): OgtCameraAccess
