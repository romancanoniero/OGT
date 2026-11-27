package com.onlygoodthings.app.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.onlygoodthings.app.data.LocalAuth
import com.onlygoodthings.app.data.LocalOgtSession
import com.onlygoodthings.app.platform.sharePlainText
import com.onlygoodthings.app.platform.PostShareApp
import com.onlygoodthings.app.platform.installedPostShareApps
import com.onlygoodthings.app.platform.sharePostWithApp
import com.onlygoodthings.app.resources.*
import com.onlygoodthings.app.theme.OgtColors
import com.onlygoodthings.app.ui.screens.OgtNeighborAvatar
import com.onlygoodthings.shared.domain.NeighborCard
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.painterResource

/** Destinatarios de OGT arriba; acciones externas al pie. No envía nada hasta pulsar Enviar. */
@Composable
fun PostShareSheet(
    shareText: String,
    postLink: String,
    onDismiss: () -> Unit,
    onExternal: () -> Unit = {},
    title: String = "Compartir",
) {
    val auth=LocalAuth.current
    val session=LocalOgtSession.current
    val owner=session.currentUserId
    val recent=rememberAccountData(owner) { auth.account.threads() }
    val clipboard=LocalClipboardManager.current
    val scope=rememberCoroutineScope()
    var query by remember(owner) { mutableStateOf("") }
    var people by remember(owner) { mutableStateOf(emptyList<NeighborCard>()) }
    var searching by remember(owner) { mutableStateOf(false) }
    var selected by remember(owner) { mutableStateOf<NeighborCard?>(null) }
    var busy by remember(owner) { mutableStateOf(false) }
    var error by remember(owner) { mutableStateOf<String?>(null) }
    var sent by remember(owner) { mutableStateOf(false) }
    var shareStatus by remember { mutableStateOf<String?>(null) }
    var apps by remember { mutableStateOf(emptyList<PostShareApp>()) }
    var moreOpen by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { apps=runCatching { installedPostShareApps() }.getOrDefault(emptyList()) }
    LaunchedEffect(owner,query) {
        people=emptyList();searching=false;error=null
        if(query.trim().length>=2) {
            searching=true;delay(280)
            try { people=auth.feed.search(query.trim(),12).people.filter { it.userId!=owner } }
            catch(cancel:CancellationException) { throw cancel }
            catch(_:Exception) { error="No pudimos buscar personas. Volvé a intentarlo." }
            finally { searching=false }
        }
    }
    val rows=remember(recent.value,people,query,owner) {
        val known=recent.value.orEmpty().sortedByDescending { it.lastAtEpochMs }.map { NeighborCard(it.peerUserId,it.peerName) }
        (known.filter { query.isBlank() || it.displayName.contains(query.trim(),true) }+people)
            .filter { it.userId!=owner }.distinctBy { it.userId }.take(16)
    }
    OgtBottomSheet(onDismiss=onDismiss,maxHeight=520.dp,dismissEnabled=!busy) { sheet ->
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
            Text(title,fontSize=20.sp,fontWeight=FontWeight.Bold,color=OgtColors.ink,modifier=Modifier.weight(1f))
            Text("OGT",fontSize=12.sp,fontWeight=FontWeight.Bold,color=OgtColors.secondary)
        }
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(query,{query=it;sent=false},singleLine=true,enabled=!busy,
            label={Text("Buscar una persona")},modifier=Modifier.fillMaxWidth(),shape=RoundedCornerShape(16.dp))
        Spacer(Modifier.height(12.dp))
        Text("Enviar en OGT",fontSize=13.sp,fontWeight=FontWeight.SemiBold,color=OgtColors.muted)
        Spacer(Modifier.height(8.dp))
        if(recent.busy || searching) OgtCaption("Buscando contactos…")
        if(!recent.busy && rows.isEmpty() && !searching) OgtCaption(if(query.isBlank())"Tus conversaciones recientes aparecerán acá. También podés buscar una persona." else "No encontramos personas con ese nombre.")
        recent.error?.let { OgtCaption(it); androidx.compose.material3.TextButton(onClick=recent::reload,enabled=!busy) { Text("Reintentar") } }
        LazyRow(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            items(rows,key={it.userId}) { person ->
                val active=selected?.userId==person.userId
                Column(Modifier.width(74.dp).clip(RoundedCornerShape(14.dp)).semantics { this.selected=active;contentDescription="Seleccionar a ${person.displayName}" }
                    .clickable(enabled=!busy) { selected=if(active)null else person;sent=false;error=null }.padding(vertical=8.dp),horizontalAlignment=Alignment.CenterHorizontally) {
                    Box(Modifier.size(52.dp).border(if(active)2.dp else 1.dp,if(active)OgtColors.secondary else OgtColors.hairline,CircleShape).padding(4.dp)) {
                        val photo=person.photoUrl ?: com.onlygoodthings.app.data.LocalOgtDb.current.userOrNull(person.userId)?.photoUrl
                        if(photo.isNullOrBlank()) Box(Modifier.fillMaxSize().background(OgtColors.sand,CircleShape),contentAlignment=Alignment.Center) {
                            Text(person.displayName.trim().removePrefix("@").split(Regex("[\\s_.-]+")).take(2).mapNotNull { it.firstOrNull()?.takeIf(Char::isLetterOrDigit) }.joinToString(""),color=OgtColors.secondary,fontWeight=FontWeight.Bold)
                        } else OgtNeighborAvatar(person.userId,photo,null,Modifier.fillMaxSize().clip(CircleShape))
                        if(active) Text("✓",color=OgtColors.secondary,fontWeight=FontWeight.Bold,modifier=Modifier.align(Alignment.BottomEnd).background(OgtColors.surface,CircleShape))
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(person.displayName,fontSize=12.sp,color=OgtColors.ink,maxLines=2,overflow=TextOverflow.Ellipsis)
                }
            }
        }
        error?.let { OgtCaption(it) }
        if(sent) OgtCaption("Publicación enviada")
        selected?.let { recipient ->
            Spacer(Modifier.height(8.dp))
            OgtPrimaryButton(if(busy)"Enviando…" else if(sent)"Enviado" else "Enviar a ${recipient.displayName}",enabled=!busy && !sent) {
                busy=true;error=null
                scope.launch {
                    try { auth.sharePostWith(owner,recipient.userId,shareText,postLink);sent=true;onExternal() }
                    catch(cancel:CancellationException) { throw cancel }
                    catch(failure:Exception) { error=failure.message ?: "No se pudo enviar. Volvé a intentarlo." }
                    finally { busy=false }
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        Box(Modifier.fillMaxWidth().height(1.dp).background(OgtColors.hairline))
        Spacer(Modifier.height(12.dp))
        LazyRow(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            item("copy-link") {
                ShareCircleAction(null,"Copiar enlace",!busy,onClick={clipboard.setText(AnnotatedString(postLink));shareStatus="Enlace copiado";onExternal()}) {
                    OgtStitchIcon(Res.drawable.qs_link,"",size=24.dp,tint=OgtColors.secondary)
                }
            }
            items(apps,key={it.id}) { app ->
                ShareCircleAction(app.label,"Compartir por ${app.label}",!busy,onClick={
                    onExternal();sheet.closeNow();if(!sharePostWithApp(app,shareText,postLink))sharePlainText(shareText)
                }) {
                    if(app.icon!=null)Image(app.icon,null,Modifier.size(48.dp))
                    else Image(painterResource(when(app.brand){"whatsapp"->Res.drawable.share_app_whatsapp;"telegram"->Res.drawable.share_app_telegram;"messages"->Res.drawable.share_app_messages;"mail"->Res.drawable.share_app_mail;else->Res.drawable.qs_share}),null,Modifier.size(48.dp))
                }
            }
            item("more") {
                Box {
                    ShareCircleAction("Más","Más opciones para compartir",!busy,onClick={moreOpen=true}) { Text("•••",color=OgtColors.secondary,fontSize=22.sp) }
                    DropdownMenu(expanded=moreOpen,onDismissRequest={moreOpen=false}) {
                        DropdownMenuItem(text={Text("Copiar texto")},onClick={moreOpen=false;clipboard.setText(AnnotatedString(shareText));shareStatus="Texto copiado";onExternal()})
                        DropdownMenuItem(text={Text("Más apps")},onClick={moreOpen=false;onExternal();sheet.closeNow();sharePlainText(shareText)})
                    }
                }
            }
        }
        shareStatus?.let { OgtCaption(it) }
        Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun ShareCircleAction(label:String?,description:String,enabled:Boolean,onClick:()->Unit,art:@Composable ()->Unit) {
    Column(Modifier.width(68.dp).clip(RoundedCornerShape(12.dp)).semantics(mergeDescendants=true) { contentDescription=description }
        .clickable(enabled=enabled,onClick=onClick).padding(vertical=4.dp),horizontalAlignment=Alignment.CenterHorizontally) {
        Box(Modifier.size(48.dp).clip(CircleShape).background(OgtColors.sand),contentAlignment=Alignment.Center) { art() }
        Spacer(Modifier.height(4.dp))
        if(label!=null)Text(label,fontSize=11.sp,color=OgtColors.ink,maxLines=1,overflow=TextOverflow.Ellipsis)
        else Spacer(Modifier.height(14.dp))
    }
}
