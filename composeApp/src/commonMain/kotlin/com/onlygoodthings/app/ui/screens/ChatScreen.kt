package com.onlygoodthings.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import com.onlygoodthings.app.ui.components.OgtOutlinedTextField as OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.onlygoodthings.app.data.LocalOgtDb
import com.onlygoodthings.app.data.LocalOgtSession
import com.onlygoodthings.app.resources.Res
import com.onlygoodthings.app.resources.qs_form
import com.onlygoodthings.app.resources.qs_send
import com.onlygoodthings.app.theme.OgtColors
import com.onlygoodthings.app.theme.OgtDimens
import com.onlygoodthings.app.theme.ogtOutlinedFieldColors
import com.onlygoodthings.app.ui.components.OgtCaption
import com.onlygoodthings.app.ui.components.OgtPill
import com.onlygoodthings.app.ui.components.OgtPrimaryButton
import com.onlygoodthings.app.ui.components.OgtSectionTitle
import com.onlygoodthings.app.ui.components.OgtStitchIcon
import com.onlygoodthings.app.ui.components.OgtTopBar
import com.onlygoodthings.app.ui.components.ogtDismissImeOnScroll
import com.onlygoodthings.shared.data.local.LocalAdoptRequest
import com.onlygoodthings.shared.data.local.OgtIds
import kotlinx.coroutines.launch

@Composable
fun ChatScreen(matchId: String = "", onBack: () -> Unit) {
    val me=LocalOgtSession.current.me()
    val db=LocalOgtDb.current
    if(db.matches.any { it.id==matchId && (me.owns(it.requesterId) || me.owns(it.providerId)) }) {
        LocalChatScreen(matchId,onBack)
    } else RemoteChatScreen(matchId,onBack)
}

@Composable
private fun RemoteChatScreen(matchId: String,onBack: () -> Unit) {
    val auth=com.onlygoodthings.app.data.LocalAuth.current
    val session=LocalOgtSession.current
    val owner=session.me().id
    val scope=rememberCoroutineScope()
    val state=com.onlygoodthings.app.ui.components.rememberAccountData("$owner:$matchId") {
        val thread=auth.account.threads().firstOrNull { it.matchId==matchId } ?: error("Conversación no disponible")
        thread to auth.account.messages(matchId)
    }
    var draft by remember(owner,matchId) { mutableStateOf("") }
    var sending by remember(owner,matchId) { mutableStateOf(false) }
    var error by remember(owner,matchId) { mutableStateOf<String?>(null) }
    val lifecycle=androidx.lifecycle.compose.LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(owner,matchId) {
        while(true) {
            kotlinx.coroutines.delay(5000)
            if(!sending && !state.busy && lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.STARTED))state.reload()
        }
    }
    Column(Modifier.fillMaxSize().background(OgtColors.canvas)) {
        OgtTopBar("Mensajes",onBack=onBack)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
            com.onlygoodthings.app.ui.components.AccountDataStatus(state)
            state.value?.let { (thread,messages) ->
                Text(thread.peerName,fontWeight=FontWeight.Bold,fontSize=20.sp)
                OgtCaption(thread.tagLabel)
                OgtCaption("Coordiná en un lugar público y de día. No compartas contraseñas ni datos bancarios.")
                if(messages.isEmpty())OgtCaption("Todavía no hay mensajes en esta conversación.")
                messages.forEach { msg -> Bubble(msg.body,msg.mine,com.onlygoodthings.shared.domain.relativeTimeLabel(msg.createdAtEpochMs,com.onlygoodthings.shared.realtime.currentEpochMs())) }
            }
        }
        Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
            error?.let { Text(it,color=OgtColors.error) }
            OutlinedTextField(value=draft,onValueChange={draft=it.take(800)},modifier=Modifier.fillMaxWidth(),enabled=!sending,
                placeholder={Text("Escribí un mensaje")},colors=ogtOutlinedFieldColors())
            OgtCaption("${draft.length}/800")
            OgtPrimaryButton(if(sending)"Enviando…" else "Enviar",enabled=!sending && draft.isNotBlank() && state.value!=null) {
                val body=draft
                sending=true;error=null
                scope.launch {
                    try {
                        check(session.me().id==owner)
                        auth.account.sendMessage(matchId,body)
                        if(session.me().id==owner) { draft="";state.reload() }
                    } catch(cancel:kotlinx.coroutines.CancellationException) { throw cancel }
                    catch(_:Exception) { error="No se pudo confirmar el envío. Actualizá la conversación antes de reintentarlo." }
                    finally { sending=false }
                }
            }
        }
    }
}

@Composable
private fun LocalChatScreen(matchId: String, onBack: () -> Unit) {
    val db = LocalOgtDb.current
    val me = LocalOgtSession.current.me()
    var tick by remember { mutableStateOf(0) }
    val match = db.matches.firstOrNull { it.id == matchId && (me.owns(it.requesterId) || me.owns(it.providerId)) }
    if (match == null) {
        Column { OgtTopBar("Mensajes",onBack=onBack);OgtCaption("Esta conversación no está disponible para tu cuenta.") }
        return
    }
    val peer = db.userOrNull(if (me.owns(match.providerId)) match.requesterId else match.providerId)
    if(peer==null) {
        Column { OgtTopBar("Mensajes",onBack=onBack);OgtCaption("No pudimos recuperar los datos de los participantes de esta conversación.") }
        return
    }
    val adopt = remember(matchId, tick, db.inboxEpoch) { db.adoptRequestOfMatch(matchId) }
    val sighting = remember(matchId, tick, db.inboxEpoch, db.lostEpoch) { db.sightingOfMatch(matchId) }
    val msgs = remember(matchId, tick, db.inboxEpoch) { db.messagesOf(matchId) }
    var draft by remember(me.id,matchId) { mutableStateOf("") }
    var openForm by remember(matchId) { mutableStateOf(adopt != null) }
    val adoption = adopt != null
    val lostChat = sighting != null
    Column(Modifier.fillMaxSize().background(OgtColors.canvas)) {
        OgtTopBar(title = if (adoption) "Adopción" else if (lostChat) "Avistaje" else "Mensajes", onBack = onBack)
        Column(
            Modifier.weight(1f).ogtDismissImeOnScroll().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column {
                    Text(peer.displayName, fontWeight = FontWeight.Bold, fontSize = 20.sp)
                    OgtCaption(
                        if (adoption || lostChat) "${match.requestedLabel} · ${peer.levelLabel}"
                        else "${peer.levelLabel} · ${peer.communityPoints} pts",
                    )
                }
                OgtPill(if (adoption) "Postulación" else if (lostChat) "Avistaje" else "Encuentro Seguro")
            }
            if (adoption) {
                OgtCaption("Tocá el documento para ver el formulario y respondé abajo para abrir la conversación.")
            } else if (lostChat) {
                OgtCaption("Este hilo nació de un pin en el mapa. Coordiná en un lugar público y de día.")
            } else {
                Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(OgtColors.sunset).padding(12.dp)) {
                    Text(
                        "Recomendamos coordinar siempre en lugares públicos y de día. Nunca compartas datos bancarios ni contraseñas.",
                        color = OgtColors.sunsetText,
                        fontSize = 13.sp,
                    )
                }
                OgtSectionTitle("Acción comunitaria en curso")
                OgtCaption("${match.offeredLabel} x ${match.requestedLabel}")
                Text(match.quote ?: match.distanceLabel, fontWeight = FontWeight.SemiBold)
            }
            msgs.forEach { msg ->
                val form = msg.adoptRequestId?.let { id -> db.adoptRequests.firstOrNull { it.id == id } }
                if (form != null) {
                    FormDocumentBubble(
                        request = form,
                        expanded = openForm,
                        onToggle = { openForm = !openForm },
                        time = msg.timeLabel,
                        mine = msg.senderId == me.id,
                    )
                } else {
                    Bubble(msg.body, mine = msg.senderId == me.id, time = msg.timeLabel)
                }
            }

        }
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OgtCaption("Conversación local en este dispositivo; no se sincroniza con el servidor.")
            if(!match.chatEnabled)OgtCaption("Esta conversación solo permite lectura.")
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.Bottom,
            ) {
                OutlinedTextField(
                    value = draft,
                    onValueChange = { draft = it },
                    enabled = match.chatEnabled,
                    modifier = Modifier.weight(1f),
                    placeholder = {
                        Text(
                            if (adoption) "Respondé la postulación" else if (lostChat) "Respondé el avistaje" else "Escribí un mensaje",
                            color = OgtColors.muted,
                        )
                    },
                    shape = RoundedCornerShape(OgtDimens.buttonRadius),
                    colors = ogtOutlinedFieldColors(),
                )
                val canSend = match.chatEnabled && draft.isNotBlank()
                Box(
                    Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(if (canSend) OgtColors.secondary.copy(alpha = 0.12f) else OgtColors.sand)
                        .then(if (canSend) Modifier.clickable {
                            if (db.addChatMessage(me, matchId, draft) != null) {
                                draft = ""
                                tick += 1
                            }
                        } else Modifier),
                    contentAlignment = Alignment.Center,
                ) {
                    OgtStitchIcon(
                        Res.drawable.qs_send,
                        "Enviar",
                        tint = if (canSend) OgtColors.secondary else OgtColors.muted,
                    )
                }
            }
        }
    }
}

@Composable
private fun FormDocumentBubble(
    request: LocalAdoptRequest,
    expanded: Boolean,
    onToggle: () -> Unit,
    time: String,
    mine: Boolean,
) {
    Column(Modifier.fillMaxWidth(), horizontalAlignment = if (mine) Alignment.End else Alignment.Start) {
        Column(
            Modifier
                .fillMaxWidth(0.92f)
                .clip(RoundedCornerShape(16.dp))
                .background(if (mine) OgtColors.mint else OgtColors.surface)
                .border(1.dp, OgtColors.hairline, RoundedCornerShape(16.dp))
                .clickable(onClick = onToggle)
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Box(
                    Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(OgtColors.sand)
                        .border(1.dp, OgtColors.hairline, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    OgtStitchIcon(Res.drawable.qs_form, "Formulario", tint = OgtColors.secondary)
                }
                Column(Modifier.weight(1f)) {
                    Text("Formulario de adopción", fontWeight = FontWeight.SemiBold, color = OgtColors.ink)
                    OgtCaption(if (expanded) "Ocultar detalle" else "Tocá para ver y responder")
                }
            }
            if (expanded) {
                FormLine("Nombre", request.applicantName)
                FormLine("Teléfono", request.phone)
                FormLine("Domicilio", request.address)
                FormLine("Hogar", request.homeKind)
                FormLine("Otras mascotas", request.otherPets)
                if (request.otherSpecies.isNotBlank()) FormLine("Especies", request.otherSpecies)
                FormLine("Familia / hogar", request.household)
                FormLine("Horas fuera", request.hoursAway)
                FormLine("Experiencia", request.experience)
                FormLine("Motivo", request.motive)
            }
        }
        Text(time, color = OgtColors.muted, fontSize = 11.sp)
    }
}

@Composable
private fun FormLine(label: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(label, color = OgtColors.muted, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
        Text(value, color = OgtColors.ink, fontSize = 14.sp)
    }
}

@Composable
private fun Bubble(text: String, mine: Boolean, time: String) {
    val bg = if (mine) OgtColors.mint else OgtColors.surface
    Column(Modifier.fillMaxWidth(), horizontalAlignment = if (mine) Alignment.End else Alignment.Start) {
        Box(Modifier.fillMaxWidth(0.86f).clip(RoundedCornerShape(16.dp)).background(bg).padding(12.dp)) {
            Text(text, fontSize = 14.sp)
        }
        Text(time, color = OgtColors.muted, fontSize = 11.sp)
    }
}
