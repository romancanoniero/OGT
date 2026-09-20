package com.onlygoodthings.app.platform

import android.graphics.drawable.ColorDrawable
import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.window.DialogWindowProvider

/** Dim casi nulo + blur de ventana (API 31+) para el velo vidrioso. */
@Composable
actual fun SoftenDialogBackdrop() {
    val view = LocalView.current
    SideEffect {
        val window = (view.parent as? DialogWindowProvider)?.window ?: return@SideEffect
        window.setDimAmount(0f)
        window.setBackgroundDrawable(ColorDrawable(android.graphics.Color.TRANSPARENT))
        if (Build.VERSION.SDK_INT >= 31) {
            window.setBackgroundBlurRadius(36)
        }
    }
}
