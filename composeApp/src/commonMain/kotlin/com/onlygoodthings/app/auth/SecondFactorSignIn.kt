package com.onlygoodthings.app.auth

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CancellationException

/** A challenge is transient: no factor, code or partially verified user is persisted. */
internal class SecondFactorSignIn(
    private val begin: suspend (Int) -> Boolean,
    private val verify: suspend (String) -> AuthUser,
    private val openSession: suspend (AuthUser, Boolean) -> Unit,
    private val cancelNative: () -> Unit,
) {
    var factors by mutableStateOf<List<AuthSecondFactor>>(emptyList()); private set
    var selected by mutableStateOf<AuthSecondFactor?>(null); private set
    var expired by mutableStateOf(false); private set
    private var keepSession = true
    private var verifiedUser: AuthUser? = null
    private var generation = 0

    fun require(factors: List<AuthSecondFactor>, keepSession: Boolean) {
        check(factors.isNotEmpty()) { "No hay una verificación disponible" }
        generation++
        this.factors = factors.toList()
        this.keepSession = keepSession
        selected = null
        verifiedUser = null
        expired = false
    }

    fun clear() {
        generation++
        cancelNative()
        factors = emptyList()
        selected = null
        verifiedUser = null
        expired = false
    }

    suspend fun choose(index: Int): Boolean {
        val factor = factors.firstOrNull { it.index == index }
            ?: throw AuthException("Elegí una verificación disponible")
        val expected = generation
        selected = null
        verifiedUser = null
        val codeRequired = challengeStep(expected) { begin(index) }
        checkCurrent(expected)
        selected = factor
        if (codeRequired) return false
        complete("", expected)
        return true
    }

    suspend fun confirm(code: String) {
        check(selected != null) { "Elegí una verificación disponible" }
        require(validSecondFactorCode(code)) { "Ingresá los seis dígitos del código" }
        complete(code, generation)
    }

    private suspend fun complete(code: String, expected: Int) {
        // If OGT was temporarily unavailable after Firebase verified the code,
        // retry only opening OGT; do not consume the one-time code a second time.
        val user = verifiedUser ?: challengeStep(expected) { verify(code) }.also {
            checkCurrent(expected)
            verifiedUser = it
        }
        checkCurrent(expected)
        openSession(user, keepSession)
        checkCurrent(expected)
        clear()
    }

    private suspend fun <T> challengeStep(expected: Int, operation: suspend () -> T): T = try {
        checkCurrent(expected)
        operation()
    } catch (cancel: CancellationException) {
        throw cancel
    } catch (failure: Exception) {
        // A response from a discarded attempt must never clear its replacement.
        checkCurrent(expected)
        val timedOut = generateSequence<Throwable>(failure) { it.cause }.take(8).any {
            it.message?.contains(TotpChallengeTimeout) == true
        }
        if (!timedOut) throw failure
        clear()
        expired = true
        throw AuthException(
            "La verificación venció. Volvé a iniciar sesión y usá el código actual de tu aplicación autenticadora.",
            failure,
        )
    }

    private fun checkCurrent(expected: Int) {
        if (generation != expected) throw CancellationException("Sign-in challenge changed")
    }
}

private val TotpChallengeTimeout = Regex("\\bTOTP_CHALLENGE_TIMEOUT\\b", RegexOption.IGNORE_CASE)
