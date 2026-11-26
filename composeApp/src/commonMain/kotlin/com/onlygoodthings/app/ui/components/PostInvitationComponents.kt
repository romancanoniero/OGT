package com.onlygoodthings.app.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.onlygoodthings.app.data.LocalAuth
import com.onlygoodthings.app.platform.sharePostInvitation
import com.onlygoodthings.app.theme.OgtColors
import com.onlygoodthings.shared.domain.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable
fun PostInvitationDraftEditor(givenName:String="",purpose:String="PARTICIPATION",initialAccount:MentionCandidate?=null,busy:Boolean=false,errorMessage:String?=null,onDismiss:()->Unit,onAdd:(PostInvitationDraft)->Unit) {
    var name by remember { mutableStateOf(givenName) }
    var account by remember { mutableStateOf(initialAccount) }
    var channel by remember { mutableStateOf(if(initialAccount!=null)"IN_APP" else "SHARE") }
    var contact by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    OgtBottomSheet(onDismiss=onDismiss,maxHeight=650.dp) { sheet ->
        Column(Modifier.heightIn(max=540.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            Text(if(purpose=="RECOGNITION")"Invitar a la persona reconocida" else "Invitar a ver y participar")
            OgtCaption("El enlace es personal, vence a los 30 días y podés retirarlo desde el post.")
            if(purpose=="RECOGNITION")OgtCaption("Compartilo solo con la persona reconocida para que pueda confirmar su identidad.")
            OgtCaption("Los SMS pueden tener costo según tu plan. WhatsApp y correo usan tu conexión habitual.")
            OgtCaption("La invitación se prepara al guardar el post. Vos elegís la app y confirmás el envío del mensaje. Las personas registradas que elegís también verán su invitación en Notificaciones.")
            OgtOutlinedTextField(value=name,onValueChange={name=it},label={Text("Nombre y apellido")},singleLine=true,modifier=Modifier.fillMaxWidth())
            AccountMentionPicker(account) { account=it;if(it!=null && name.isBlank())name=it.displayName }
            for((value,label) in listOf("SHARE" to "Elegir app para compartir", "EMAIL" to "Email", "SMS" to "Teléfono (SMS)", "IN_APP" to "Aviso a una cuenta de OGT")) {
                GhostLink((if(channel==value)"✓ " else "")+label) { channel=value;error=null }
            }
            if(channel=="EMAIL"||channel=="SMS")OgtOutlinedTextField(value=contact,onValueChange={contact=it},label={Text(if(channel=="EMAIL")"Correo del destinatario" else "Teléfono con código de país")},placeholder={Text(if(channel=="EMAIL")"persona@ejemplo.com" else "+541155551234")},singleLine=true,modifier=Modifier.fillMaxWidth())
            (error ?: errorMessage)?.let { Text(it,color=OgtColors.error) }
            OgtPrimaryButton("Agregar invitación",enabled=!busy) {
                val draft=PostInvitationDraft(name.trim(),purpose,channel,contact.trim(),account?.userId)
                try { validateInvitationDraft(draft);onAdd(draft) } catch(e:IllegalArgumentException) { error=e.message }
            }
            GhostLink("Cancelar",sheet.close)
        }
    }
}

@Composable
fun PostInvitationManager(postId:String,onDismiss:()->Unit) {
    val auth=LocalAuth.current;val scope=rememberCoroutineScope()
    var rows by remember(postId) { mutableStateOf<List<PostInvitationView>>(emptyList()) }
    var loading by remember { mutableStateOf(true) };var error by remember { mutableStateOf<String?>(null) }
    var editor by remember { mutableStateOf(false) }
    suspend fun refresh() { rows=auth.postInvitations.list(postId) }
    LaunchedEffect(postId) {
        try { refresh() } catch(cancel:CancellationException) { throw cancel } catch(e:Exception) { error=e.message } finally { loading=false }
    }
    if(editor) { PostInvitationDraftEditor(busy=loading,errorMessage=error,onDismiss={if(!loading)editor=false},onAdd={ draft ->
        loading=true;error=null
        scope.launch {
            try { rows=auth.postInvitations.create(postId,listOf(draft));editor=false }
            catch(cancel:CancellationException) { throw cancel } catch(e:Exception) { error=e.message }
            finally { loading=false }
        }
    });return }
    OgtBottomSheet(onDismiss=onDismiss,maxHeight=650.dp) { sheet ->
        Text("Invitaciones a esta publicación")
        OgtCaption("Compartir abre la hoja del sistema. La entrega depende de la app que elijas; abrirla no confirma que el mensaje se haya enviado.")
        if(loading)OgtCaption("Preparando invitaciones…")
        Column(Modifier.heightIn(max=360.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(10.dp)) {
            for(row in rows)Column(verticalArrangement=Arrangement.spacedBy(4.dp)) {
                Text(row.recipientName)
                OgtCaption(when { row.status=="PENDING" && row.expiresAtEpochMs<=com.onlygoodthings.shared.realtime.currentEpochMs() -> "Invitación vencida";row.status=="ACCEPTED" -> "Invitación aceptada";row.status=="DECLINED" -> "Invitación rechazada";row.status=="REVOKED" -> "Invitación retirada";row.deliveryStatus=="SHARE_OPENED" -> "Se abrió la hoja de compartir";row.channel=="IN_APP" -> "Disponible en Notificaciones de OGT";else -> "Lista para compartir" })
                val token=row.token
                if(row.status=="PENDING" && token!=null && row.expiresAtEpochMs>com.onlygoodthings.shared.realtime.currentEpochMs()) {
                    OgtPrimaryButton("Compartir con ${row.recipientName}",enabled=!loading) {
                        val text=if(row.purpose=="RECOGNITION")"${row.issuerName} reconoció una buena acción tuya, ${row.recipientName}. Ingresá o registrate para verla y confirmar que sos vos: ${invitationLink(token)}"
                            else "${row.issuerName} te invita a ver y participar de una publicación en OGT: ${invitationLink(token)}. Podés registrarte y volver a este post."
                        sharePostInvitation(text,row.channel,row.contact.orEmpty())
                        scope.launch { runCatching { auth.postInvitations.shareOpened(row.id);refresh() } }
                    }
                    GhostLink("Elegir otra aplicación") {
                        sharePostInvitation("${row.issuerName} te invita a esta publicación de OGT: ${invitationLink(token)}","SHARE","")
                        scope.launch { runCatching { auth.postInvitations.shareOpened(row.id);refresh() } }
                    }
                    GhostLink("Retirar invitación") { scope.launch { try { auth.postInvitations.revoke(row.id);refresh() } catch(e:Exception) { error=e.message } } }
                }
            }
        }
        error?.let { Text(it,color=OgtColors.error) }
        OgtPrimaryButton("Invitar a otra persona",enabled=!loading) { editor=true }
        GhostLink("Listo",sheet.close)
    }
}
