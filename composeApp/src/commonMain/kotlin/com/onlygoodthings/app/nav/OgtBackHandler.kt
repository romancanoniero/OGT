package com.onlygoodthings.app.nav

import androidx.compose.runtime.Composable

/** Atrás del sistema (Android). En iOS no hay botón nativo: vale el chevron. */
@Composable
expect fun OgtBackHandler(enabled: Boolean = true, onBack: () -> Unit)
