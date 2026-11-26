package com.onlygoodthings.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.onlygoodthings.app.data.LocalAuth
import com.onlygoodthings.app.data.LocalOgtDb
import com.onlygoodthings.app.data.LocalOgtSession
import com.onlygoodthings.app.theme.OgtColors
import com.onlygoodthings.app.ui.components.*
import com.onlygoodthings.shared.domain.PostInvitationView
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable
fun PostInvitationScreen(onOpenPost:(String)->Unit,onBack:()->Unit) {
    val auth=LocalAuth.current; val session=LocalOgtSession.current; val db=LocalOgtDb.current
    val token=session.pendingInvitationToken
    val scope=rememberCoroutineScope()
    var invitation by remember(token) { mutableStateOf<PostInvitationView?>(null) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(token) {
        if(token==null)return@LaunchedEffect
        busy=true
        try { invitation=auth.postInvitations.peek(token) }
        catch(cancel:CancellationException) { throw cancel }
        catch(e:Exception) { error=e.message }
        finally { busy=false }
    }
    Column(Modifier.fillMaxSize()) {
        OgtTopBar("Invitación a una publicación",onBack=onBack)
        Column(Modifier.padding(24.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
            invitation?.let { row ->
                Text("${row.issuerName} te invitó, ${row.recipientName}.")
                OgtCaption(if(row.purpose=="RECOGNITION")"Reconoció una buena acción tuya. Confirmá solo si sos la persona nombrada." else "Podés ver el post y sumarte a la conversación. Aceptar la invitación no te atribuye la acción publicada.")
                OgtPrimaryButton(if(busy)"Abriendo…" else "Aceptar y ver publicación",enabled=!busy) {
                    scope.launch {
                        busy=true;error=null
                        try {
                            auth.postInvitations.accept(token!!)
                            db.upsertRemoteSocial(auth.feed.getPost(row.postId));session.persistSocialFeed()
                            session.rememberInvitationToken(null);onOpenPost(row.postId)
                        } catch(cancel:CancellationException) { throw cancel }
                        catch(e:Exception) { error=e.message }
                        finally { busy=false }
                    }
                }
                GhostLink("Rechazar invitación") {
                    if(!busy)scope.launch {
                        busy=true;error=null
                        try { auth.postInvitations.accept(token!!,true);session.rememberInvitationToken(null);onBack() }
                        catch(cancel:CancellationException) { throw cancel }
                        catch(e:Exception) { error=e.message }
                        finally { busy=false }
                    }
                }
            }
            if(busy && invitation==null)OgtCaption("Cargando invitación…")
            error?.let { Text(it,color=OgtColors.error) }
            GhostLink("Continuar en OGT",onBack)
        }
    }
}
