package com.onlygoodthings.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.unit.dp
import com.onlygoodthings.app.data.*
import com.onlygoodthings.app.platform.OgtPickedMedia
import com.onlygoodthings.app.notify.showSystemNotice
import com.onlygoodthings.app.theme.OgtColors
import com.onlygoodthings.app.ui.components.*
import com.onlygoodthings.shared.domain.*
import com.onlygoodthings.shared.realtime.currentEpochMs
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlin.random.Random

/** One editor for general posts and recognitions; legacy kinds remain readable/editable. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PublishPostScreen(editPostId:String?=null,onDone:()->Unit,onBack:()->Unit=onDone,initialHashtag:String?=null) {
    val db=LocalOgtDb.current;val session=LocalOgtSession.current;val auth=LocalAuth.current
    val scope=rememberCoroutineScope();val me=session.me();val preview=LocalInspectionMode.current
    val existing=remember(editPostId) { editPostId?.let(db::post) }
    val editing=existing!=null
    var catalog by remember { mutableStateOf(legacyRecognitionCategories+listOf(RecognitionCategory("ternura","Ternura",tagKey="ternura",requiresRecognition=false))) }
    var ready by remember { mutableStateOf(preview) }
    var catalogError by remember { mutableStateOf<String?>(null) }
    var retry by remember { mutableStateOf(0) }
    LaunchedEffect(retry) {
        if(preview)return@LaunchedEffect
        catalogError=null
        try { catalog=auth.feed.recognitionCategories();ready=true }
        catch(cancel:CancellationException) { throw cancel }
        catch(_:Exception) { catalogError="No pudimos cargar los hashtags. Reintentá sin perder el borrador." }
    }
    val tags=remember(editPostId) { mutableStateListOf<PostHashtag>().apply {
        addAll(existing?.hashtags?.ifEmpty { Hashtags.legacy(existing.tag,existing.categoryId) }.orEmpty())
        if(existing==null && initialHashtag!=null)add(PostHashtag(initialHashtag,"Ternura"))
    } }
    var freeTag by remember { mutableStateOf("") }
    var body by remember(editPostId) { mutableStateOf(existing?.body.orEmpty()) }
    var fullName by remember(editPostId) { mutableStateOf(existing?.let(db::creditName).orEmpty()) }
    var protagonist by remember(editPostId) { mutableStateOf<MentionCandidate?>(null) }
    var attribution by remember(editPostId) { mutableStateOf(existing?.attributionUrl.orEmpty()) }
    var includePlace by remember(editPostId) { mutableStateOf(!existing?.place.isNullOrBlank()) }
    var place by remember(editPostId) { mutableStateOf(existing?.place?.takeIf(String::isNotBlank) ?: me.barrio) }
    val media=remember { mutableStateListOf<OgtPickedMedia>() }
    val invitations=remember { mutableStateListOf<PostInvitationDraft>() }
    val participants=remember { mutableStateListOf<MentionCandidate>() }
    var invitationPurpose by remember { mutableStateOf<String?>(null) }
    var published by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var preparing by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var attempt by remember { mutableStateOf<List<Any?>?>(null) }
    var requestId by remember { mutableStateOf("") }
    var uploaded by remember { mutableStateOf<List<PostMediaItem>?>(null) }
    val recognition=existing?.publicationKind=="ACTION" || tags.any { tag ->
        catalog.any { (it.id==tag.categoryId || it.tagKey.ifBlank { Hashtags.catalogKey(it.label,it.id) }==tag.key) && it.requiresRecognition }
    }

    fun addTag(tag:PostHashtag) {
        if(tags.any { it.key==tag.key })return
        if(tags.size>=Hashtags.MAX_TAGS) { error="Podés elegir hasta ${Hashtags.MAX_TAGS} hashtags.";return }
        tags+=tag;error=null
    }
    fun addFree() {
        try {
            val value=Hashtags.free(freeTag)
            val official=catalog.firstOrNull { it.tagKey.ifBlank { Hashtags.catalogKey(it.label,it.id) }==value.key || Hashtags.catalogKey(it.label,it.id)==value.key }
            addTag(if(official==null)value else PostHashtag(official.tagKey.ifBlank { value.key },official.label,official.id))
            freeTag=""
        } catch(failure:IllegalArgumentException) { error=failure.message }
    }
    fun publish() {
        if(busy || preparing)return
        if(freeTag.isNotBlank()) { addFree();if(freeTag.isNotBlank())return }
        try {
            require(ready) { "Esperá a que carguen los hashtags." }
            require(tags.isNotEmpty()) { "Elegí al menos un hashtag." }
            require(body.trim().isNotEmpty()) { "Contá lo que querés compartir." }
            require(recognition || invitations.none { it.purpose=="RECOGNITION" }) { "Elegí un hashtag de buena acción o quitá la invitación de reconocimiento." }
            require(editing || media.isNotEmpty()) { "Agregá una foto o un video." }
            if(recognition) {
                require(fullName.trim().length in 2..100 && !fullName.trim().startsWith("@")) { "Ingresá el nombre completo, separado del @nick." }
                if(!editing)GoodActionRecognition.validate(me.id,protagonist?.userId,fullName,me.displayName)
            }
            val credit=ContentAttribution.normalize(attribution)
            val selectedTags=tags.map { if(it.categoryId==null)it.label else it.key };val selectedMedia=media.toList();val selectedInvitations=invitations.toList()
            val text=body.trim();val name=fullName.trim().takeIf { recognition };val hero=if(recognition)protagonist?.userId else null
            val selectedPlace=if(includePlace)place.trim() else ""
            require(!includePlace || selectedPlace.length in 2..200) { "Completá el lugar o desactivá la ubicación opcional." }
            val participantIds=participants.mapNotNull { it.userId }.distinct().filter { it!=hero }
            val key=listOf(text,selectedTags,selectedMedia,selectedInvitations,credit,name,hero,participantIds,selectedPlace)
            if(attempt!=key) { attempt=key;requestId="post_${currentEpochMs()}_${Random.nextLong().toULong().toString(16)}";uploaded=null }
            val selectedRequest=requestId
            busy=true;error=null
            scope.launch {
                try {
                    val saved=if(existing!=null)auth.feed.editPublication(existing.id,text,selectedTags,name,credit.orEmpty(),selectedPlace)
                    else {
                        val remoteMedia=uploaded ?: auth.uploadPostMedia(selectedMedia,tags.first().label).also { uploaded=it }
                        check(session.currentUserId==me.id) { "La sesión cambió. Volvé a intentarlo." }
                        auth.feed.publishPublication(PublicationDraft(text,selectedTags,remoteMedia,selectedRequest,
                            honoreeFullName=name,protagonistUserId=if(recognition)hero else null,
                            participantUserIds=participantIds,invitations=selectedInvitations,attributionUrl=credit,place=selectedPlace.takeIf(String::isNotBlank)))
                    }
                    check(session.currentUserId==me.id) { "La sesión cambió. Volvé a intentarlo." }
                    db.upsertRemoteSocial(saved);db.bumpFeed()
                    if(!editing) {
                        participantIds.forEach { id -> if(db.postPeople.none { it.postId==saved.id && it.userId==id })
                            db.postPeople+=com.onlygoodthings.shared.data.local.LocalPostPerson(saved.id,id,PostPersonRole.PARTICIPANT) }
                        val mentioned=(participantIds+listOfNotNull(if(recognition)hero else null)).distinct()
                        db.notifyMentionedUsers(me.id,saved.id,mentioned).forEach(::showSystemNotice)
                        auth.notifyPublicationMentions(me.id,saved.id,mentioned)
                    }
                    session.persistSocialFeed()
                    scope.launch { announcePublishedPost(db,db.post(saved.id)) }
                    if(selectedInvitations.isEmpty() || editing)onDone() else published=saved.id
                } catch(cancel:CancellationException) { throw cancel }
                catch(failure:Exception) { error=failure.message ?: "No se pudo publicar. Tu borrador sigue aquí para reintentar." }
                finally { busy=false }
            }
        } catch(failure:IllegalArgumentException) { error=failure.message }
    }

    Column(Modifier.fillMaxSize().background(OgtColors.canvas)) {
        OgtTopBar(if(editing)"Editar publicación" else "Publicar",onBack={if(!busy)onBack()})
        Column(Modifier.weight(1f).ogtDismissImeOnScroll().verticalScroll(rememberScrollState()).padding(16.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
            Text("Compartí algo bueno. Los hashtags cuentan de qué se trata y ayudan a encontrar contenido que te interesa.")
            OgtSectionTitle("Hashtags")
            Text("Elegí los oficiales o agregá los tuyos. Hasta ${Hashtags.MAX_TAGS} por publicación.",color=OgtColors.muted)
            if(!ready && catalogError==null)Text("Cargando hashtags…")
            catalogError?.let { Text(it,color=OgtColors.error);TextButton(onClick={retry++}) { Text("Reintentar") } }
            FlowRow(horizontalArrangement=Arrangement.spacedBy(6.dp)) {
                catalog.forEach { category ->
                    val key=category.tagKey.ifBlank { Hashtags.catalogKey(category.label,category.id) }
                    val selected=tags.any { it.key==key }
                    FilterChip(selected,onClick={if(selected)tags.removeAll { it.key==key } else addTag(PostHashtag(key,category.label,category.id))},enabled=!busy,
                        label={Text(PostHashtag(key,category.label).display)})
                }
            }
            if(tags.isNotEmpty()) {
                Text("Seleccionados: ${tags.size}/${Hashtags.MAX_TAGS}")
                FlowRow(horizontalArrangement=Arrangement.spacedBy(6.dp)) {
                    tags.toList().forEach { tag -> InputChip(true,onClick={if(!busy)tags.remove(tag)},enabled=!busy,label={Text("${tag.display} ×")}) }
                }
            }
            OgtOutlinedTextField(value=freeTag,onValueChange={freeTag=it.take(60)},label={Text("Hashtag libre")},placeholder={Text("Ej. #HuertaEnCasa")},singleLine=true,enabled=!busy,modifier=Modifier.fillMaxWidth())
            TextButton(onClick=::addFree,enabled=!busy && freeTag.isNotBlank()) { Text("Agregar hashtag") }
            if(recognition) {
                OgtSectionTitle("¿Quién hizo esta buena acción?")
                Text("Reconocé lo que hizo otra persona. El nombre completo y el @nick se indican por separado.",color=OgtColors.muted)
                OgtOutlinedTextField(fullName,{fullName=it.take(100)},label={Text("Nombre y apellido")},singleLine=true,enabled=!busy,modifier=Modifier.fillMaxWidth())
                if(!editing) {
                    AccountMentionPicker(protagonist) { person -> protagonist=person;if(person!=null && fullName.isBlank())fullName=person.displayName }
                    GhostLink("Invitar a esta persona a ver el reconocimiento") { invitationPurpose="RECOGNITION" }
                }
            }
            OgtSectionTitle("Fotos o video")
            if(editing)Text("Los archivos de esta publicación se conservan.",color=OgtColors.muted)
            else OgtDraftMediaStrip(media,missing=false,heroDescription="Fotos o video de la publicación",onBusyChange={preparing=it})
            Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                Checkbox(includePlace,{includePlace=it},enabled=!busy)
                Text("Incluir lugar (opcional)")
            }
            if(includePlace)OgtOutlinedTextField(place,{place=it.take(200)},label={Text("Lugar")},enabled=!busy,modifier=Modifier.fillMaxWidth())
            OgtOutlinedTextField(body,{body=it.take(500)},label={Text("La historia")},minLines=4,enabled=!busy,modifier=Modifier.fillMaxWidth())
            AttributionField(attribution,{attribution=it},enabled=!busy)
            PublicationRightsNotice()
            if(!editing) {
                OgtSectionTitle("Otras personas que participaron")
                AccountMentionPicker(null) { person -> if(person!=null && (!recognition || person.userId!=protagonist?.userId) && participants.none { it.userId==person.userId })participants+=person }
                participants.toList().forEach { person ->
                    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
                        Text(person.displayName,Modifier.weight(1f));TextButton(onClick={participants.remove(person)}) { Text("Quitar") }
                    }
                }
                GhostLink("Invitar a participar") { invitationPurpose="PARTICIPATION" }
                invitations.toList().forEach { draft ->
                    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
                        Text(draft.recipientName,modifier=Modifier.weight(1f))
                        TextButton(onClick={invitations.remove(draft)},enabled=!busy) { Text("Quitar") }
                    }
                }
            }
            error?.let { Text(it,color=OgtColors.error) }
            OgtPrimaryButton(if(busy)"Subiendo publicación…" else if(editing)"Guardar cambios" else "Publicar",onClick=::publish,enabled=!busy && !preparing && ready)
        }
    }
    invitationPurpose?.let { purpose -> PostInvitationDraftEditor(givenName=if(purpose=="RECOGNITION")fullName else "",purpose=purpose,
        initialAccount=if(purpose=="RECOGNITION")protagonist else null,onDismiss={invitationPurpose=null},onAdd={ draft ->
            if(purpose=="RECOGNITION") {
                fullName=draft.recipientName
                protagonist=draft.recipientUserId?.let { id -> db.userOrNull(id)?.let { db.toMention(it) } }
            }
            invitations+=draft;invitationPurpose=null
        }) }
    published?.let { id -> PostInvitationManager(id) { published=null;onDone() } }
    if(busy)AlertDialog(onDismissRequest={},confirmButton={},title={Text("Publicando")},text={Text("Estamos subiendo tu publicación. Tu contenido se conserva si necesitás reintentar.")})
}
