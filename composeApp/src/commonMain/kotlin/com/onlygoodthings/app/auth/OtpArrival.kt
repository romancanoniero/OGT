package com.onlygoodthings.app.auth

import androidx.compose.runtime.Composable

/** Seis dígitos seguidos, sin tragarse un número más largo. */
fun otpDigits(text: String): String? =
    Regex("(?<!\\d)(\\d{6})(?!\\d)").find(text)?.groupValues?.get(1)

/**
 * Mientras la pantalla de código está abierta, el sistema puede entregar el SMS.
 * En iOS el teclado lo ofrece con el tipo de campo; acá no hay un listener extra.
 */
@Composable
expect fun OtpArrivalEffect(onCode: (String) -> Unit)
