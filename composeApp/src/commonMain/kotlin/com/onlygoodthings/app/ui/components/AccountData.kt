package com.onlygoodthings.app.ui.components

import androidx.compose.runtime.*
import androidx.compose.material3.*
import kotlinx.coroutines.CancellationException

/** Each owner gets a separate state object, so a late response cannot repaint another account. */
internal class AccountData<T> {
    var value by mutableStateOf<T?>(null)
    var busy by mutableStateOf(true)
    var error by mutableStateOf<String?>(null)
    var attempt by mutableStateOf(0)
    fun reload() { attempt++ }
}

@Composable
internal fun <T> rememberAccountData(owner: String, load: suspend () -> T): AccountData<T> {
    val state=remember(owner) { AccountData<T>() }
    val latest by rememberUpdatedState(load)
    LaunchedEffect(owner,state.attempt) {
        state.busy=true;state.error=null
        try { state.value=latest() }
        catch(cancel:CancellationException) { throw cancel }
        catch(_:Exception) { state.error="No pudimos actualizar los datos. Revisá la conexión y volvé a intentarlo." }
        finally { state.busy=false }
    }
    return state
}

@Composable
internal fun AccountDataStatus(state: AccountData<*>) {
    if(state.busy && state.value==null)LinearProgressIndicator()
    state.error?.let { Text(it) }
    TextButton(enabled=!state.busy,onClick=state::reload) { Text(if(state.error!=null)"Reintentar" else "Actualizar") }
}
