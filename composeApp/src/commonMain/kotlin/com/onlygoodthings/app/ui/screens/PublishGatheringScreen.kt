package com.onlygoodthings.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.onlygoodthings.app.data.*
import com.onlygoodthings.app.platform.OgtPickedMedia
import com.onlygoodthings.app.theme.OgtColors
import com.onlygoodthings.app.ui.components.*
import com.onlygoodthings.shared.domain.*
import com.onlygoodthings.shared.realtime.currentEpochMs
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.datetime.*
import kotlin.random.Random

/** An upcoming activity belongs to its organizer; it is not a recognition of a past action. */
@Composable
fun PublishGatheringScreen(editPostId:String?=null,onDone:()->Unit,onBack:()->Unit=onDone) {
    val auth=LocalAuth.current;val session=LocalOgtSession.current;val db=LocalOgtDb.current
    val me=session.me();val scope=rememberCoroutineScope();val existing=remember(editPostId){editPostId?.let { db.post(it) }}
    val editing=existing!=null
    val date=remember(editPostId){existing?.eventStartsAtEpochMs?.let { Instant.fromEpochMilliseconds(it).toLocalDateTime(TimeZone.currentSystemDefault()) }}
    var topic by remember(editPostId){mutableStateOf(existing?.tag ?: gatheringTopics.first())}
    var topicsOpen by remember{mutableStateOf(false)}
    var body by remember(editPostId){mutableStateOf(existing?.body.orEmpty())}
    var place by remember(editPostId){mutableStateOf(existing?.place.orEmpty())}
    var day by remember(editPostId){mutableStateOf(date?.let { "${it.dayOfMonth.toString().padStart(2,'0')}/${it.monthNumber.toString().padStart(2,'0')}/${it.year}" }.orEmpty())}
    var clock by remember(editPostId){mutableStateOf(date?.let { "${it.hour.toString().padStart(2,'0')}:${it.minute.toString().padStart(2,'0')}" }.orEmpty())}
    var attribution by remember(editPostId){mutableStateOf(existing?.attributionUrl.orEmpty())}
    val media=remember{mutableStateListOf<OgtPickedMedia>()}
    var preparing by remember{mutableStateOf(false)};var busy by remember{mutableStateOf(false)};var error by remember{mutableStateOf<String?>(null)}
    var attempt by remember{mutableStateOf<List<Any?>?>(null)};var requestId by remember{mutableStateOf("")};var uploaded by remember{mutableStateOf<List<PostMediaItem>?>(null)}
    fun publish() {
        if(busy||preparing)return
        val start:Long?;val credit:String?
        try {
            start=if(editing && existing?.eventStartsAtEpochMs==null && day.isBlank() && clock.isBlank() && place==existing.place)null else gatheringStart(day,clock)
            if(start!=null)validateGathering(body,topic,place,start,currentEpochMs(),allowPast=editing && existing?.eventStartsAtEpochMs==start)
            else require(body.trim().isNotEmpty()){ "Contá qué actividad están organizando." }
            require(editing||media.isNotEmpty()){ "Agregá una foto o un video de la actividad." }
            credit=ContentAttribution.normalize(attribution)
        } catch(e:IllegalArgumentException){error=e.message;return}
        val text=body.trim();val location=place.trim();val selectedTopic=topic;val chosen=media.toList()
        val key=listOf(text,location,start,selectedTopic,chosen,credit)
        if(attempt!=key){attempt=key;uploaded=null;requestId="gather_${currentEpochMs()}_${Random.nextLong().toULong().toString(16)}"}
        busy=true;error=null
        scope.launch {
            try {
                val saved=if(existing!=null)auth.feed.editGathering(existing.id,text,selectedTopic,location,start,credit.orEmpty()) else {
                    val items=uploaded ?: auth.uploadPostMedia(chosen,selectedTopic).also{uploaded=it}
                    check(session.currentUserId==me.id){"La sesión cambió. Volvé a ingresar."}
                    auth.feed.publishGathering(GatheringPostDraft(text,selectedTopic,location,start!!,items,requestId,credit))
                }
                check(session.currentUserId==me.id){"La sesión cambió. Volvé a ingresar."}
                db.upsertRemoteSocial(saved);session.persistSocialFeed()
                scope.launch { announcePublishedPost(db,db.post(saved.id)) }
                onDone()
            } catch(cancel:CancellationException){throw cancel}
            catch(e:Exception){error=e.message ?: "No pudimos guardar la convocatoria. Tus datos siguen acá para reintentar."}
            finally{busy=false}
        }
    }
    Column(Modifier.fillMaxSize()) {
        OgtTopBar(if(editing)"Editar convocatoria" else "Convocatoria",onBack=onBack)
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),verticalArrangement=Arrangement.spacedBy(14.dp)) {
            Text("Invitá a la comunidad a una actividad que van a realizar.")
            OgtCaption("Contá qué proponen, cuándo y dónde se encuentran. La convocatoria se publica a tu nombre como organizador.")
            Text("Tipo de actividad")
            Box {
                GhostLink("$topic ▾"){if(!busy)topicsOpen=true}
                androidx.compose.material3.DropdownMenu(expanded=topicsOpen,onDismissRequest={topicsOpen=false}) {
                    gatheringTopics.forEach { value -> androidx.compose.material3.DropdownMenuItem(text={Text(value)},onClick={topic=value;topicsOpen=false}) }
                }
            }
            OgtOutlinedTextField(value=body,onValueChange={body=it.take(500)},label={Text("¿Qué están organizando?")},minLines=3,modifier=Modifier.fillMaxWidth(),enabled=!busy)
            if(editing && existing?.eventStartsAtEpochMs==null)OgtCaption("Esta publicación anterior no tiene horario. Podés conservarlo así o completar día, hora y lugar.")
            Row(horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                OgtOutlinedTextField(value=day,onValueChange={day=it.take(10)},label={Text("Día (DD/MM/AAAA)")},singleLine=true,modifier=Modifier.weight(1f),enabled=!busy)
                OgtOutlinedTextField(value=clock,onValueChange={clock=it.take(5)},label={Text("Hora (HH:mm)")},singleLine=true,modifier=Modifier.weight(1f),enabled=!busy)
            }
            OgtCaption("Usá el horario local de tu dispositivo. Asistiré estará disponible hasta que empiece la actividad.")
            PublicationPlaceField(value=place,onValueChange={place=it},label="Lugar o punto de encuentro",enabled=!busy)
            if(!editing)OgtDraftMediaStrip(items=media,missing=false,heroDescription="La actividad",onBusyChange={preparing=it},reorderEnabled=!busy) else OgtCaption("Se conservan las fotos y videos de esta publicación.")
            AttributionField(attribution,{attribution=it},enabled=!busy)
            PublicationRightsNotice()
            error?.let{Text(it,color=OgtColors.error)}
            OgtPrimaryButton(if(busy)"Guardando convocatoria…" else if(editing)"Guardar cambios" else "Publicar convocatoria",enabled=!busy&&!preparing,onClick=::publish)
            OgtCaption("Después de publicar, podés invitar a otras personas desde la publicación.")
            Spacer(Modifier.height(24.dp))
        }
    }
}
