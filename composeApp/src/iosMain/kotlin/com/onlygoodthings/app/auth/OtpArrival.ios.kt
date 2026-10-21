package com.onlygoodthings.app.auth

import androidx.compose.runtime.Composable

@Composable
actual fun OtpArrivalEffect(onCode: (String) -> Unit) {
    // iOS muestra el código de SMS o Mail arriba del teclado si el campo es oneTimeCode.
    if (false) onCode("")
}
