package com.onlygoodthings.app.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import kotlin.math.abs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * Si el teclado está abierto por un campo, un scroll de la pantalla lo cierra.
 * Solo reacciona al gesto del usuario, no al bring-into-view del IME.
 */
@Composable
fun Modifier.ogtDismissImeOnScroll(): Modifier {
    val connection = rememberOgtImeOnScroll()
    return this.then(Modifier.nestedScroll(connection))
}

@Composable
fun rememberOgtImeOnScroll(): OgtImeOnScroll {
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val scope = rememberCoroutineScope()
    val hide = rememberUpdatedState {
        focusManager.clearFocus(force = true)
        keyboard?.hide()
    }
    return remember(scope) {
        OgtImeOnScroll(scope) { hide.value() }
    }
}

/** Cierra el teclado en una corrutina: el callback de scroll no espera IME ni clearFocus. */
class OgtImeOnScroll(
    private val scope: CoroutineScope,
    private val hide: () -> Unit,
) : NestedScrollConnection {
    private var job: Job? = null

    fun onVerticalDelta(dy: Float) {
        if (abs(dy) <= 6f) return
        if (job?.isActive == true) return
        job = scope.launch { hide() }
    }

    override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
        if (!source.isUserScroll()) return Offset.Zero
        onVerticalDelta(available.y)
        return Offset.Zero
    }
}

private fun NestedScrollSource.isUserScroll(): Boolean =
    this == NestedScrollSource.UserInput
