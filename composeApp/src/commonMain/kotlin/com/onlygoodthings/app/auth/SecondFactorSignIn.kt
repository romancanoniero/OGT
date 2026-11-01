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
    }

    fun clear() {
        generation++
        cancelNative()
        factors = emptyList()
        selected = null
        verifiedUser = null
    }

    suspend fun choose(index: Int): Boolean {
        val factor = factors.firstOrNull { it.index == index }
            ?: throw AuthException("Elegí una verificación disponible")
        val expected = generation
        selected = null
        verifiedUser = null
        val codeRequired = begin(index)
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
        val user = verifiedUser ?: verify(code).also {
            checkCurrent(expected)
            verifiedUser = it
        }
        checkCurrent(expected)
        openSession(user, keepSession)
        checkCurrent(expected)
        clear()
    }

    private fun checkCurrent(expected: Int) {
        if (generation != expected) throw CancellationException("Sign-in challenge changed")
    }
}
