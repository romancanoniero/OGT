package com.onlygoodthings.app.ui.components

import androidx.compose.runtime.*
import androidx.compose.material3.*
import androidx.compose.foundation.layout.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.onlygoodthings.app.data.LocalAuth
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException

@Composable
fun BlockUserButton(userId:String,contentColor:androidx.compose.ui.graphics.Color?=null,onBlocked:()->Unit={}) {
    val auth=LocalAuth.current;val scope=rememberCoroutineScope()
    var confirm by remember(userId){mutableStateOf(false)}
    var busy by remember(userId){mutableStateOf(false)}
    var error by remember(userId){mutableStateOf<String?>(null)}
    TextButton(onClick={confirm=true},enabled=!busy){Text("Bloquear usuario",color=contentColor?:LocalContentColor.current)}
    if(confirm)AlertDialog(onDismissRequest={if(!busy)confirm=false},title={Text("¿Bloquear a esta persona?")},
        text={Column {Text("Dejarán de verse sus publicaciones e historias y no podrán contactarse. Podés desbloquearla en Ajustes.");error?.let {Text(it)} }},
        confirmButton={TextButton(enabled=!busy,onClick={busy=true;scope.launch {
            try{auth.setUserBlocked(userId,true);confirm=false;onBlocked()}
            catch(cancel:CancellationException){throw cancel}
            catch(failure:Exception){error=failure.message?:"No se pudo bloquear. Volvé a intentarlo."}
            finally{busy=false}
        }}){Text(if(busy)"Bloqueando…" else "Bloquear")}},
        dismissButton={TextButton(enabled=!busy,onClick={confirm=false}){Text("Cancelar")}})
}

@Composable
fun BlockedUsersControl() {
    val auth=LocalAuth.current;val scope=rememberCoroutineScope()
    var open by remember{mutableStateOf(false)}
    var rows by remember{mutableStateOf<List<com.onlygoodthings.shared.domain.BlockedUser>>(emptyList())}
    var loading by remember{mutableStateOf(false)}
    var error by remember{mutableStateOf<String?>(null)}
    fun load(){loading=true;error=null;scope.launch {try{rows=auth.feed.blockedUsers()}catch(cancel:CancellationException){throw cancel}catch(failure:Exception){error=failure.message}finally{loading=false}}}
    TextButton(onClick={open=true;load()}){Text("Usuarios bloqueados")}
    if(open)OgtBottomSheet(onDismiss={open=false}) {
        Text("Usuarios bloqueados",style=MaterialTheme.typography.titleLarge)
        if(loading)CircularProgressIndicator()
        error?.let {Text(it);TextButton(onClick={load()}){Text("Reintentar")}}
        if(!loading && error==null && rows.isEmpty())Text("No bloqueaste a ninguna persona.")
        rows.forEach { row ->Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
            Text(row.displayName,modifier=Modifier.weight(1f).padding(12.dp))
            TextButton(enabled=!loading,onClick={loading=true;scope.launch {
                try{auth.setUserBlocked(row.userId,false);rows=auth.feed.blockedUsers()}
                catch(cancel:CancellationException){throw cancel}
                catch(failure:Exception){error=failure.message}
                finally{loading=false}
            }}){Text("Desbloquear")}
        } }
    }
}
