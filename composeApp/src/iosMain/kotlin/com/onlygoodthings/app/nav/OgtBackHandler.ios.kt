package com.onlygoodthings.app.nav

import androidx.compose.runtime.Composable

@Composable
actual fun OgtBackHandler(enabled: Boolean, onBack: () -> Unit) {
    // iOS usa el chevron de la barra; no hay botón atrás del sistema.
}
