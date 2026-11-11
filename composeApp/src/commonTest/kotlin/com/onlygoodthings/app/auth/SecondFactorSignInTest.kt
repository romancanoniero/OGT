package com.onlygoodthings.app.auth

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlin.test.*

class SecondFactorSignInTest {
    private val factor = AuthSecondFactor(2, "totp", "Aplicación autenticadora")
    private val user = AuthUser("verified-user", null, null, "Persona", null)

    @Test fun validCodesAreExactlySixAsciiDigits() {
        assertTrue(validSecondFactorCode("012345"))
        for (code in listOf("12345", "1234567", "123 45", "１２３４５６", "abcdef", "")) assertFalse(validSecondFactorCode(code))
    }

    @Test fun wrongCodeDoesNotOpenOgtAndCanBeRetriedWithTheSameChallenge() = runBlocking {
        var opened = 0
        var kept: Boolean? = null
        val flow = SecondFactorSignIn(begin = { true }, verify = {
            if (it != "123456") throw AuthException("El código de verificación no es válido")
            user
        }, openSession = { value, keep -> assertEquals(user, value); opened++; kept = keep }, cancelNative = {})
        flow.require(listOf(factor), keepSession = false)
        assertFalse(flow.choose(2))
        assertEquals(0, opened)
        assertFailsWith<AuthException> { flow.confirm("000000") }
        assertEquals(listOf(factor), flow.factors)
        assertEquals(0, opened)
        flow.confirm("123456")
        assertEquals(1, opened)
        assertEquals(false, kept)
        assertTrue(flow.factors.isEmpty())
        assertNull(flow.selected)
    }

    @Test fun backendRetryDoesNotReuseAConsumedOneTimeCode() = runBlocking {
        var verified = 0
        var opened = 0
        val flow = SecondFactorSignIn(begin = { true }, verify = { verified++; user }, openSession = { _, _ ->
            opened++
            if (opened == 1) throw AuthException("Conexión interrumpida")
        }, cancelNative = {})
        flow.require(listOf(factor), true); flow.choose(2)
        assertFailsWith<AuthException> { flow.confirm("123456") }
        assertFalse(flow.factors.isEmpty())
        flow.confirm("123456")
        assertEquals(1, verified)
        assertEquals(2, opened)
        assertTrue(flow.factors.isEmpty())
    }

    @Test fun automaticSmsVerificationOpensTheSameSession() = runBlocking {
        var opened = false
        val phone = AuthSecondFactor(0, "phone", "SMS")
        val flow = SecondFactorSignIn(begin = { false }, verify = { assertEquals("", it); user }, openSession = { _, keep ->
            opened = true; assertTrue(keep)
        }, cancelNative = {})
        flow.require(listOf(phone), true)
        assertTrue(flow.choose(0))
        assertTrue(opened)
    }

    @Test fun cancellingAChallengeDiscardsALateVerificationBeforeOpeningOgt() = runBlocking {
        val reply = CompletableDeferred<AuthUser>()
        var opened = false
        var cancelled = 0
        val flow = SecondFactorSignIn(begin = { true }, verify = { reply.await() }, openSession = { _, _ -> opened = true }, cancelNative = { cancelled++ })
        flow.require(listOf(factor), true); flow.choose(2)
        val pending = async(start = CoroutineStart.UNDISPATCHED) { flow.confirm("123456") }
        flow.clear(); reply.complete(user)
        assertFailsWith<CancellationException> { pending.await() }
        assertFalse(opened)
        assertEquals(1, cancelled)
        assertTrue(flow.factors.isEmpty())
    }

    @Test fun invalidFactorCannotReachTheNativeVerifier() = runBlocking {
        val flow = SecondFactorSignIn(begin = { error("Unexpected native call") }, verify = { error("Unexpected verifier") }, openSession = { _, _ -> error("Unexpected session") }, cancelNative = {})
        flow.require(listOf(factor), true)
        assertFailsWith<AuthException> { flow.choose(0) }
        assertNull(flow.selected)
    }

    @Test fun expiredTotpChallengeIsDiscardedAndFreshSignInCanSucceed() = runBlocking {
        var verified = 0
        var opened = 0
        var cancelled = 0
        val flow = SecondFactorSignIn(begin = { true }, verify = {
            verified++
            if (verified == 1) throw AuthException("An internal error has occurred. [ TOTP_CHALLENGE_TIMEOUT:TOTP challenge timeout, provide first factor again. ]")
            user
        }, openSession = { _, keep -> assertFalse(keep); opened++ }, cancelNative = { cancelled++ })
        flow.require(listOf(factor), false); flow.choose(2)
        val failure = assertFailsWith<AuthException> { flow.confirm("123456") }
        assertFalse(failure.message.orEmpty().contains("TOTP_CHALLENGE_TIMEOUT"))
        assertTrue(flow.factors.isEmpty())
        assertNull(flow.selected)
        assertEquals(1, cancelled)
        assertEquals(0, opened)
        assertTrue(flow.expired)
        assertFailsWith<IllegalStateException> { flow.confirm("123456") }
        assertEquals(1, verified)
        flow.require(listOf(factor), false)
        assertFalse(flow.expired)
        flow.choose(2); flow.confirm("654321")
        assertEquals(1, opened)
        assertEquals(2, verified)
        assertTrue(flow.factors.isEmpty())
        assertFalse(flow.expired)
    }

    @Test fun expirationWhileChoosingAFactorDiscardsItsNativeChallenge() = runBlocking {
        var cancelled = 0
        val flow = SecondFactorSignIn(begin = {
            throw AuthException("No se pudo verificar", IllegalStateException("TOTP_CHALLENGE_TIMEOUT: provide first factor again"))
        }, verify = { error("Expired challenge must not verify") }, openSession = { _, _ -> error("Expired challenge must not open OGT") }, cancelNative = { cancelled++ })
        flow.require(listOf(factor), true)
        assertFailsWith<AuthException> { flow.choose(2) }
        assertTrue(flow.factors.isEmpty())
        assertNull(flow.selected)
        assertEquals(1, cancelled)
        assertTrue(flow.expired)
        flow.clear()
        assertFalse(flow.expired)
    }

    @Test fun aLateExpirationCannotDiscardANewerChallenge() = runBlocking {
        val reply = CompletableDeferred<AuthUser>()
        var opened = false
        val flow = SecondFactorSignIn(begin = { true }, verify = { reply.await() }, openSession = { _, _ -> opened = true }, cancelNative = {})
        flow.require(listOf(factor), true); flow.choose(2)
        val pending = async(start = CoroutineStart.UNDISPATCHED) { flow.confirm("123456") }
        flow.clear(); flow.require(listOf(factor), false); flow.choose(2)
        reply.completeExceptionally(AuthException("TOTP_CHALLENGE_TIMEOUT"))
        assertFailsWith<CancellationException> { pending.await() }
        assertEquals(listOf(factor), flow.factors)
        assertEquals(factor, flow.selected)
        assertFalse(flow.expired)
        assertFalse(opened)
    }

    @Test fun backendErrorsNeverDiscardAnAlreadyVerifiedUser() = runBlocking {
        var verified = 0
        var opened = 0
        val flow = SecondFactorSignIn(begin = { true }, verify = { verified++; user }, openSession = { _, _ ->
            opened++
            if (opened == 1) throw AuthException("TOTP_CHALLENGE_TIMEOUT from a downstream service")
        }, cancelNative = {})
        flow.require(listOf(factor), true); flow.choose(2)
        assertFailsWith<AuthException> { flow.confirm("123456") }
        assertEquals(listOf(factor), flow.factors)
        assertFalse(flow.expired)
        flow.confirm("123456")
        assertEquals(1, verified)
        assertEquals(2, opened)
    }
}
