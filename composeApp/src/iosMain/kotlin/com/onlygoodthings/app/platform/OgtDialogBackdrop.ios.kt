package com.onlygoodthings.app.platform

import androidx.compose.runtime.Composable

/** En iOS el velo vidrioso lo pinta Compose; el Dialog nativo no aporta dim extra. */
@Composable
actual fun SoftenDialogBackdrop() = Unit
