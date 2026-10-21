package com.onlygoodthings.app.auth

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class OtpDigitsTest {
    @Test
    fun extractsAStandaloneSixDigitCode() {
        assertEquals("123456", otpDigits("123456"))
        assertEquals("123456", otpDigits("Tu código de Only Good Things es 123456."))
        assertEquals("654321", otpDigits("pedido 99 y código 654321 fin"))
    }

    @Test
    fun ignoresShorterAndLongerNumbers() {
        assertNull(otpDigits("12345"))
        assertNull(otpDigits("1234567"))
        assertNull(otpDigits("el código 1234567890 no entra"))
        assertNull(otpDigits(""))
    }
}
