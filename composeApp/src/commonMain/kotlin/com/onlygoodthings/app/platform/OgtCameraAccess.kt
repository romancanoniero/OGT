package com.onlygoodthings.app.platform

import androidx.compose.runtime.Composable

data class OgtCameraAccess(
    val granted: Boolean,
    val request: () -> Unit,
)

/** Permiso de cámara (y fotos en Android). El diálogo es el del sistema. */
@Composable
expect fun rememberOgtCameraAccess(): OgtCameraAccess
