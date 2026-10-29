package com.onlygoodthings.app.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import com.onlygoodthings.app.ui.components.OgtOutlinedTextField as OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.onlygoodthings.app.data.LocalAuth
import com.onlygoodthings.app.data.LocalOgtDb
import com.onlygoodthings.app.data.LocalOgtSession
import com.onlygoodthings.app.data.announcePublishedPost
import com.onlygoodthings.app.data.optimisticEditPost
import com.onlygoodthings.app.media.OgtMediaCache
import com.onlygoodthings.app.nav.ComposePostKind
import com.onlygoodthings.app.notify.showSystemNotice
import com.onlygoodthings.app.theme.OgtColors
import com.onlygoodthings.app.theme.OgtDimens
import com.onlygoodthings.app.theme.ogtOutlinedFieldColors
import com.onlygoodthings.app.platform.OgtPickedMedia
import com.onlygoodthings.app.platform.rememberHonorInviteActions
import com.onlygoodthings.app.ui.components.OgtDraftMediaStrip
import com.onlygoodthings.app.platform.sharePlainText
import com.onlygoodthings.shared.domain.HonorChannel
import com.onlygoodthings.app.ui.components.HonorInviteSheet
import com.onlygoodthings.app.ui.components.OgtMentionPicker
import com.onlygoodthings.app.ui.components.OgtPrimaryButton
import com.onlygoodthings.app.ui.components.OgtTopBar
import com.onlygoodthings.app.ui.components.ogtDismissImeOnScroll
import com.onlygoodthings.app.resources.Res
import com.onlygoodthings.app.resources.feed_avatar_me
import com.onlygoodthings.app.resources.publish_map
import com.onlygoodthings.shared.data.local.LocalPostMedia
import com.onlygoodthings.shared.data.local.LocalPostPerson
import com.onlygoodthings.shared.data.local.LocalSocialPost
import com.onlygoodthings.shared.domain.AuthorKind
import com.onlygoodthings.shared.domain.FeedEventKind
import com.onlygoodthings.shared.domain.MediaKind
import com.onlygoodthings.shared.domain.MentionCandidate
import com.onlygoodthings.shared.domain.mentionHandle
import com.onlygoodthings.shared.domain.namedMention
import com.onlygoodthings.shared.domain.PostMediaItem
import com.onlygoodthings.shared.domain.PostMediaRules
import com.onlygoodthings.shared.domain.PostPersonRole
import com.onlygoodthings.shared.domain.honorShareText
import com.onlygoodthings.shared.realtime.currentEpochMs
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource

private data class ImpactCategory(val id: String, val label: String, val icon: String)
private data class BarrioSpot(val title: String, val detail: String)

private val ImpactCategories = listOf(
    ImpactCategory("plantar", "Reforestación", "🌳"),
    ImpactCategory("limpieza", "Limpieza", "🧹"),
    ImpactCategory("reciclaje", "Reciclaje", "♻"),
    ImpactCategory("vecinal", "Ayuda comunitaria", "🤝"),
    ImpactCategory("animal", "Rescate Animal", "🐾"),
    ImpactCategory("movilidad", "Movilidad Eco", "🚲"),
)

private val TernuraCategories = listOf(
    ImpactCategory("cria", "Cría", "🐾"),
    ImpactCategory("casa", "Llegó a casa", "🐾"),
    ImpactCategory("tierna", "Historia tierna", "🐾"),
)

private val HomenajeCategories = listOf(
    ImpactCategory("envida", "En vida", ""),
    ImpactCategory("postmortem", "Post mortem", ""),
)

private fun categoriesFor(kind: ComposePostKind) = when (kind) {
    ComposePostKind.TERNURA -> TernuraCategories
    ComposePostKind.HOMENAJE -> HomenajeCategories
    else -> ImpactCategories
}

private fun defaultCategory(kind: ComposePostKind) =
    if (kind == ComposePostKind.HOMENAJE) "" else categoriesFor(kind).first().id

private val BarrioSpots = listOf(
    BarrioSpot("Parque Centenario, CABA", "Sector Canteros del Lago Central"),
    BarrioSpot("Plaza Armenia, Palermo", "Pérgola de aromáticas"),
    BarrioSpot("Parque Central", "Canteros del lago"),
)

/**
 * Dar a conocer una buena acción, una ternura o un homenaje.
 * En la acción el caso habitual es contar lo que hizo otra persona.
 */
@Composable
fun PublishActionScreen(
    kind: ComposePostKind = ComposePostKind.ACTION,
    editPostId: String? = null,
    onDone: () -> Unit,
    onBack: () -> Unit = onDone,
) {
    val db = LocalOgtDb.current
    val session = LocalOgtSession.current
    val auth = LocalAuth.current
    val scope = rememberCoroutineScope()
    val invite = rememberHonorInviteActions()
    val me = session.me()
    val editing = !editPostId.isNullOrBlank()
    val existing = remember(editPostId) { editPostId?.let { db.post(it) } }
    val categories = categoriesFor(kind)
    val storyMode = kind == ComposePostKind.TERNURA || kind == ComposePostKind.HOMENAJE
    val ternuraForm = kind == ComposePostKind.TERNURA
    val honorForm = kind == ComposePostKind.HOMENAJE
    var authorIsSelf by remember(kind) { mutableStateOf(false) }
    var storyTouched by remember(kind) { mutableStateOf(editing) }
    var category by remember(kind) {
        mutableStateOf(
            existing?.let { post -> categories.firstOrNull { it.label.equals(post.tag, ignoreCase = true) }?.id }
                ?: defaultCategory(kind),
        )
    }
    var categoryOpen by remember(kind) { mutableStateOf(false) }
    var story by remember(kind) {
        mutableStateOf(existing?.body?.takeIf { it.isNotBlank() }.orEmpty())
    }
    var attribution by remember(kind, editPostId) { mutableStateOf(existing?.attributionUrl.orEmpty()) }
    val draftMedia = remember(kind) { mutableStateListOf<OgtPickedMedia>() }
    var includePlace by remember { mutableStateOf(false) }
    var spotIndex by remember { mutableStateOf(0) }
    var toast by remember { mutableStateOf(false) }
    var publishing by remember { mutableStateOf(false) }
    var mediaPreparing by remember { mutableStateOf(false) }
    var publishError by remember { mutableStateOf<String?>(null) }
    val mentionPool = remember(me.id, db.follows.size) { db.mentionPool(me.id) }
    var protagonist by remember(kind) { mutableStateOf<MentionCandidate?>(null) }
    val neighbors = remember { mutableStateListOf<MentionCandidate>() }
    var inviteName by remember { mutableStateOf<String?>(null) }
    var inviteSlot by remember { mutableStateOf<HonorSlot?>(null) }
    val heroName = protagonist?.displayName ?: "esta persona"
    val heroAt = protagonist?.atHandle ?: "@vecina"
    val spot = BarrioSpots[spotIndex % BarrioSpots.size]
    val missingHonoree = honorForm && protagonist == null
    val missingType = honorForm && category.isBlank()
    val missingMedia = storyMode && draftMedia.isEmpty()
    val missingStory = storyMode && story.trim().isEmpty()
    val missingHonorBits = buildList {
        if (missingHonoree) add("a quién")
        if (missingType) add("si es en vida o post mortem")
        if (honorForm && missingMedia) add("una foto o un video")
        if (honorForm && missingStory) add("la historia")
    }
    val missingTernuraBits = buildList {
        if (ternuraForm && missingMedia) add("una foto o un video")
        if (ternuraForm && missingStory) add("lo que viste")
    }
    val canPublish = when {
        honorForm -> missingHonorBits.isEmpty()
        ternuraForm -> missingTernuraBits.isEmpty()
        else -> true
    }
    var triedPublish by remember(kind) { mutableStateOf(false) }
    val showMissing = triedPublish && !canPublish

    fun suggestHonorType(namedOnly: Boolean) {
        if (category.isNotBlank()) return
        category = if (namedOnly) "postmortem" else "envida"
    }

    fun chooseAuthor(self: Boolean) {
        authorIsSelf = self
    }

    fun publish() {
        if (publishing || mediaPreparing || toast) return
        if ((honorForm || ternuraForm) && !canPublish) {
            triedPublish = true
            return
        }
        val tag = when {
            ternuraForm -> "Ternura"
            else -> categories.firstOrNull { it.id == category }?.label ?: "Ayuda vecinal"
        }
        val credit = try { com.onlygoodthings.shared.domain.ContentAttribution.normalize(attribution) }
            catch (error: IllegalArgumentException) { publishError = error.message; return }
        if (editing && existing != null) {
            optimisticEditPost(db, auth.feed, scope, me.id, existing.id, story, tag, credit.orEmpty()) {
                session.persistSocialFeed()
            }
            toast = true
            return
        }
        // Capture one coherent draft. Failed requests keep all form inputs for retry.
        val body = story
        val selectedMedia = draftMedia.toList()
        val selectedHero = protagonist
        val selectedNeighbors = neighbors.toList()
        val selfAuthor = authorIsSelf
        val place = if (includePlace) spot.title else if (ternuraForm) "" else me.barrio
        val heroId = when { ternuraForm -> null; selfAuthor -> me.id; else -> selectedHero?.userId }
        val mentionedIds = buildList {
            if (!ternuraForm && !selfAuthor) selectedHero?.userId?.let(::add)
            selectedNeighbors.mapNotNull { it.userId }.forEach(::add)
        }
        val honorHits = buildList {
            if (!ternuraForm && !selfAuthor) selectedHero?.let { add(it to PostPersonRole.PROTAGONIST) }
            selectedNeighbors.forEach { add(it to PostPersonRole.PARTICIPANT) }
        }
        publishing = true
        publishError = null
        scope.launch {
            try {
                val uploaded = auth.uploadPostMedia(selectedMedia, tag)
                check(session.currentUserId == me.id) { "La sesión cambió. Volvé a intentarlo." }
                val remote = auth.feed.publishPost(
                    body = body, topic = tag, media = uploaded,
                    protagonistUserId = heroId,
                    participantUserIds = selectedNeighbors.mapNotNull { it.userId }.distinct(),
                    honoreeName = selectedHero?.takeIf { it.namedOnly }?.displayName,
                    attributionUrl = credit,
                )
                check(session.currentUserId == me.id) { "La sesión cambió. Volvé a intentarlo." }
                db.upsertRemoteSocial(remote)
                val postId = remote.id
                val index = db.posts.indexOfFirst { it.id == postId }
                if (index >= 0) db.posts[index] = db.posts[index].copy(place = place, timeLabel = "Ahora")
                db.postPeople.removeAll { it.postId == postId }
                db.postPeople += LocalPostPerson(postId, me.id, PostPersonRole.AUTHOR)
                if (!heroId.isNullOrBlank()) db.postPeople += LocalPostPerson(postId, heroId, PostPersonRole.PROTAGONIST)
                selectedNeighbors.mapNotNull { it.userId }.distinct().filter { it != me.id && it != heroId }.forEach {
                    db.postPeople += LocalPostPerson(postId, it, PostPersonRole.PARTICIPANT)
                }
                honorHits.forEach { (hit, role) -> hit.honorId?.let { db.attachHonorToPost(postId, it, role) } }
                db.recordFeedEvent(me.id, postId, FeedEventKind.SHARE)
                db.bumpFeed()
                session.persistHonors()
                session.persistSocialFeed()
                announcePublishedPost(db, db.post(postId))
                db.notifyMentionedUsers(me.id, postId, mentionedIds).forEach(::showSystemNotice)
                // The post is already confirmed; auxiliary notifications cannot turn it into a failed draft.
                scope.launch {
                    honorHits.forEach { (hit, role) ->
                        val token = db.honors.firstOrNull { it.id == hit.honorId }?.claimToken ?: return@forEach
                        runCatching { auth.honor.attach(token, postId, role) }
                    }
                    mentionedIds.distinct().filter { it != me.id }.forEach { rid ->
                        runCatching { auth.notices.notifyMention(rid, postId, if (rid == heroId) "PROTAGONIST" else "PARTICIPANT") }
                    }
                }
                toast = true
            } catch (cancel: CancellationException) { throw cancel }
            catch (failure: Exception) { publishError = failure.message ?: "No se pudo publicar. Tu contenido sigue aquí para reintentar." }
            finally { publishing = false }
        }
    }

    LaunchedEffect(toast) {
        if (!toast) return@LaunchedEffect
        delay(1600)
        onDone()
    }

    Column(Modifier.fillMaxSize().background(OgtColors.canvas)) {
        OgtTopBar(
            title = when {
                editing && honorForm -> "Editar homenaje"
                editing && ternuraForm -> "Editar ternura"
                editing -> "Editar publicación"
                kind == ComposePostKind.TERNURA -> "Ternura"
                kind == ComposePostKind.HOMENAJE -> "Homenaje"
                else -> "Dar a conocer"
            },
            onBack = onBack,
        )
        Box(Modifier.weight(1f)) {
            Column(Modifier.fillMaxSize().ogtDismissImeOnScroll().verticalScroll(rememberScrollState())) {
                Column(
                    Modifier
                        .padding(horizontal = OgtDimens.margin, vertical = 8.dp)
                        .shadow(8.dp, RoundedCornerShape(OgtDimens.cardRadius), ambientColor = OgtColors.ink.copy(alpha = 0.08f))
                        .clip(RoundedCornerShape(OgtDimens.cardRadius))
                        .background(OgtColors.canvas),
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(22.dp)) {
                        if (!ternuraForm) Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            PublishSectionLabel(
                                when {
                                    kind == ComposePostKind.HOMENAJE -> "1. ¿A quién homenajeás?"
                                    else -> "1. ¿Quién hizo esta buena acción?"
                                },
                                required = true,
                                complete = if (honorForm && triedPublish) !missingHonoree else null,
                            )
                            Text(
                                when {
                                    kind == ComposePostKind.HOMENAJE ->
                                        "Persona o mascota. Contá una enseñanza, una anécdota o las gracias que no llegaste a decir."
                                    else ->
                                        "Lo más habitual es hacer visible lo bueno que hizo otra persona. Contar lo propio también se puede."
                                },
                                color = OgtColors.muted,
                                fontSize = 13.sp,
                            )
                            if (kind != ComposePostKind.HOMENAJE) {
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    AuthorChoiceCard(
                                        title = "Alguien más",
                                        subtitle = "Lo más habitual",
                                        selected = !authorIsSelf,
                                        modifier = Modifier.weight(1f),
                                    ) { chooseAuthor(false) }
                                    AuthorChoiceCard(
                                        title = "Yo la hice",
                                        subtitle = "Mi propia acción",
                                        selected = authorIsSelf,
                                        modifier = Modifier.weight(1f),
                                    ) { chooseAuthor(true) }
                                }
                            }
                            if (authorIsSelf) {
                                ProtagonistRow(
                                    photo = Res.drawable.feed_avatar_me,
                                    sessionFace = true,
                                    title = "Vos",
                                    subtitle = "Estás contando algo que hiciste.",
                                    actionLabel = null,
                                    onAction = null,
                                )
                            } else {
                                Text(
                                    if (kind == ComposePostKind.HOMENAJE) {
                                        "Escribí el nombre. Si ya no está, alcanza con nombrarlo. Invitar es solo si alguien de la familia puede reivindicar el crédito."
                                    } else {
                                        "Escribí @ como en Instagram. Si no está en la app, invitala con su nombre."
                                    },
                                    color = OgtColors.muted,
                                    fontSize = 13.sp,
                                )
                                OgtMentionPicker(
                                    selected = listOfNotNull(protagonist),
                                    onSelect = { hit ->
                                        protagonist = hit
                                        neighbors.removeAll { it.userId != null && it.userId == hit.userId }
                                        suggestHonorType(namedOnly = hit.namedOnly)
                                    },
                                    onRemove = { protagonist = null },
                                    pool = mentionPool,
                                    single = true,
                                    placeholder = if (kind == ComposePostKind.HOMENAJE) {
                                        "Nombre o @nick"
                                    } else {
                                        "Buscá @nick o nombre"
                                    },
                                    atPrefix = kind != ComposePostKind.HOMENAJE,
                                    missing = showMissing && missingHonoree,
                                    allowNameOnly = kind == ComposePostKind.HOMENAJE,
                                    onInvite = { name ->
                                        inviteName = name
                                        inviteSlot = HonorSlot.HERO
                                    },
                                    onNameOnly = { name ->
                                        protagonist = namedMention(name)
                                        neighbors.removeAll { it.handle == mentionHandle(name) }
                                        suggestHonorType(namedOnly = true)
                                    },
                                )
                            }
                        }

                        if (!ternuraForm) Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            PublishSectionLabel(
                                when {
                                    kind == ComposePostKind.HOMENAJE -> "2. ¿Qué tipo de homenaje es?"
                                    authorIsSelf -> "2. ¿Qué tipo de impacto generaste?"
                                    else -> "2. ¿Qué tipo de impacto se generó?"
                                },
                                required = true,
                                complete = if (honorForm && triedPublish) !missingType else null,
                            )
                            Text(
                                when {
                                    kind == ComposePostKind.HOMENAJE ->
                                        "En vida, si todavía está. Post mortem, si ya no está. Podés cambiarlo."
                                    authorIsSelf -> "Seleccioná el eje positivo que mejor define tu iniciativa."
                                    else -> "Seleccioná el eje positivo que mejor define lo que hizo $heroName."
                                },
                                color = OgtColors.muted,
                                fontSize = 13.sp,
                            )
                            ImpactCategoryCombo(
                                categories = categories,
                                selectedId = category,
                                expanded = categoryOpen,
                                onExpandedChange = { categoryOpen = it },
                                onSelect = { category = it },
                                placeholder = if (honorForm) "Elegí: en vida o post mortem" else null,
                                missing = showMissing && missingType,
                            )
                        }

                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            PublishSectionLabel(
                                when {
                                    ternuraForm -> "1. Fotos o un video"
                                    storyMode -> "3. Fotos o un video"
                                    else -> "3. Evidencia visual de la acción"
                                },
                                required = storyMode,
                                complete = if (storyMode && triedPublish) !missingMedia else null,
                            )
                            OgtDraftMediaStrip(
                                items = draftMedia,
                                missing = showMissing && missingMedia,
                                heroDescription = "Evidencia",
                                onBusyChange = { mediaPreparing = it },
                            )
                        }

                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            PublishSectionLabel(
                                when {
                                    ternuraForm -> "2. Contá lo que viste"
                                    kind == ComposePostKind.HOMENAJE -> "4. La enseñanza o la anécdota"
                                    authorIsSelf -> "4. La historia de cambio"
                                    else -> "4. Contá lo que hizo $heroName"
                                },
                                required = storyMode,
                                complete = if (storyMode && triedPublish) !missingStory else null,
                            )
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                OutlinedTextField(
                                    value = story,
                                    onValueChange = {
                                        if (it.length <= 500) {
                                            story = it
                                            storyTouched = true
                                        }
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    minLines = 4,
                                    placeholder = when {
                                        honorForm -> {
                                            { Text("Contá la enseñanza, la anécdota o las gracias.", color = OgtColors.muted) }
                                        }
                                        ternuraForm -> {
                                            { Text("Unos gatitos, un gesto, algo que enterneció el día.", color = OgtColors.muted) }
                                        }
                                        else -> null
                                    },
                                    shape = RoundedCornerShape(OgtDimens.buttonRadius),
                                    colors = ogtOutlinedFieldColors(missing = showMissing && missingStory),
                                )
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text(
                                        when (kind) {
                                            ComposePostKind.HOMENAJE -> "Con respeto, sin obituario"
                                            ComposePostKind.TERNURA -> "Cálido, sin tipo ni dueño"
                                            else -> "Inspirador y constructivo"
                                        },
                                        color = OgtColors.muted,
                                        fontSize = 11.sp,
                                    )
                                    Text("${story.length} / 500", color = OgtColors.muted, fontSize = 11.sp)
                                }
                            }
                        }

                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("Lugar (si aplica)", fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = OgtColors.ink)
                            Text(
                                if (ternuraForm) {
                                    "Solo si le suma: el pasaje, la plaza. Unos gatitos no necesitan un pin."
                                } else {
                                    "Un favor, un gesto o una historia no siempre tienen un punto en el mapa."
                                },
                                color = OgtColors.muted,
                                fontSize = 13.sp,
                            )
                            if (!includePlace) {
                                Box(
                                    Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(OgtColors.sand)
                                        .tap { includePlace = true }
                                        .padding(horizontal = 12.dp, vertical = 12.dp),
                                ) {
                                    Text("+  Agregar un lugar", color = OgtColors.secondary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                                }
                            } else {
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Row(
                                        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(OgtColors.sand).padding(10.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Box(Modifier.size(36.dp).clip(CircleShape).background(OgtColors.stone), contentAlignment = Alignment.Center) {
                                            Text("📍", fontSize = 16.sp)
                                        }
                                        Spacer(Modifier.width(8.dp))
                                        Column(Modifier.weight(1f)) {
                                            Text(spot.title, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = OgtColors.ink)
                                            Text(spot.detail, color = OgtColors.muted, fontSize = 12.sp)
                                        }
                                        Box(
                                            Modifier.clip(CircleShape).background(OgtColors.stone)
                                                .tap { spotIndex += 1 }
                                                .padding(horizontal = 12.dp, vertical = 8.dp),
                                        ) {
                                            Text("Cambiar", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = OgtColors.ink)
                                        }
                                    }
                                    Box(Modifier.fillMaxWidth().height(112.dp).clip(RoundedCornerShape(16.dp))) {
                                        Image(
                                            painterResource(Res.drawable.publish_map),
                                            contentDescription = "Mapa del lugar",
                                            modifier = Modifier.fillMaxSize(),
                                            contentScale = ContentScale.Crop,
                                        )
                                        Box(
                                            Modifier.align(Alignment.BottomStart).padding(8.dp)
                                                .clip(CircleShape).background(OgtColors.canvas.copy(alpha = 0.92f))
                                                .padding(horizontal = 10.dp, vertical = 5.dp),
                                        ) {
                                            Text("📌  Pin fijado en el lugar", fontSize = 11.sp, fontWeight = FontWeight.Medium, color = OgtColors.ink)
                                        }
                                    }
                                    Text(
                                        "Quitar lugar",
                                        color = OgtColors.muted,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        modifier = Modifier.tap { includePlace = false },
                                    )
                                }
                            }
                        }

                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                when {
                                    kind == ComposePostKind.HOMENAJE -> "5. ¿Quién más quiere sumar un recuerdo?"
                                    ternuraForm -> "¿Alguien aparece en la foto?"
                                    authorIsSelf -> "5. ¿Quién más participó?"
                                    else -> "5. ¿Quién más acompañó a $heroName?"
                                },
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 14.sp,
                                color = OgtColors.ink,
                            )
                            OgtMentionPicker(
                                selected = neighbors.toList(),
                                onSelect = { hit ->
                                    if (neighbors.none { it.handle == hit.handle }) neighbors += hit
                                },
                                onRemove = { hit -> neighbors.removeAll { it.handle == hit.handle } },
                                pool = mentionPool,
                                excludeIds = setOfNotNull(protagonist?.userId),
                                placeholder = "Añadí @nick o nombre",
                                onInvite = { name ->
                                    inviteName = name
                                    inviteSlot = HonorSlot.CREW
                                },
                            )
                        }

                        com.onlygoodthings.app.ui.components.AttributionField(attribution, { attribution = it }, enabled = !publishing)
                        com.onlygoodthings.app.ui.components.PublicationRightsNotice()
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            OgtPrimaryButton(
                                when {
                                    publishing -> "Subiendo publicación…"
                                    mediaPreparing -> "Preparando video…"
                                    editing -> "Guardar cambios"
                                    kind == ComposePostKind.TERNURA -> "Compartir esta ternura"
                                    kind == ComposePostKind.HOMENAJE -> "Publicar el homenaje"
                                    authorIsSelf -> "Compartir con la comunidad"
                                    else -> "Hacerlo visible en la comunidad"
                                },
                                onClick = ::publish,
                                enabled = !publishing && !mediaPreparing && !toast,
                            )
                            publishError?.let { Text(it, color = OgtColors.error, fontSize = 13.sp) }
                            if (showMissing && honorForm && missingHonorBits.isNotEmpty()) {
                                Text(
                                    "Falta ${joinHonorMissing(missingHonorBits)}.",
                                    color = OgtColors.error,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium,
                                )
                            }
                            if (showMissing && ternuraForm && missingTernuraBits.isNotEmpty()) {
                                Text(
                                    "Falta ${joinHonorMissing(missingTernuraBits)}.",
                                    color = OgtColors.error,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium,
                                )
                            }
                            Text(
                                when {
                                    kind == ComposePostKind.TERNURA ->
                                        "Cualquiera puede contarlo. Si buscan hogar, publicá una adopción."
                                    kind == ComposePostKind.HOMENAJE ->
                                        "El aplauso es un gracias por contar, no un festejo."
                                    authorIsSelf ->
                                        "Contar lo que hiciste puede inspirar a más gente a sumarse."
                                    else ->
                                        "Hacer visible lo bueno de $heroName suma a la comunidad y a quien lo hizo."
                                },
                                color = OgtColors.muted,
                                fontSize = 12.sp,
                            )
                        }
                    }
                }
                Spacer(Modifier.height(32.dp))
            }
            if (publishing) androidx.compose.material3.AlertDialog(
                onDismissRequest = {},
                title = { Text("Subiendo publicación") },
                text = { Column(verticalArrangement = Arrangement.spacedBy(16.dp)) { androidx.compose.material3.CircularProgressIndicator(); Text("Estamos guardando tus archivos. Esperá un momento…") } },
                confirmButton = {},
            )
            if (toast) {
                Box(
                    Modifier.align(Alignment.BottomCenter).padding(bottom = 28.dp)
                        .clip(CircleShape).background(OgtColors.ink)
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                ) {
                    Text(
                        when {
                            kind == ComposePostKind.TERNURA -> "Ya se ve en la comunidad."
                            kind == ComposePostKind.HOMENAJE -> "El homenaje ya está publicado."
                            authorIsSelf -> "🎉  ¡Buena acción contada!"
                            else -> "🎉  ¡Lo bueno de $heroAt ya se ve en la comunidad!"
                        },
                        color = OgtColors.canvas,
                        fontSize = 13.sp,
                    )
                }
            }
        }
    }
    val pendingName = inviteName
    val slot = inviteSlot
    if (pendingName != null && slot != null) {
        HonorInviteSheet(
            givenName = pendingName,
            issuerName = me.displayName,
            onDismiss = {
                inviteName = null
                inviteSlot = null
            },
            onSend = { name, channel, contact ->
                val honor = db.issueHonor(me.id, name, channel, contact)
                session.persistHonors()
                scope.launch { auth.honor.issue(name, channel, contact, honor.claimToken) }
                val text = honorShareText(me.displayName, honor.givenName, honor.claimToken)
                when (channel) {
                    HonorChannel.WHATSAPP -> invite.shareWhatsApp(contact, text)
                    HonorChannel.SMS -> invite.sendSms(contact, text)
                    HonorChannel.EMAIL -> sharePlainText(text)
                }
                val mention = db.toMention(honor)
                when (slot) {
                    HonorSlot.HERO -> {
                        protagonist = mention
                        neighbors.removeAll { it.handle == mention.handle }
                        suggestHonorType(namedOnly = false)
                    }
                    HonorSlot.CREW -> {
                        if (neighbors.none { it.handle == mention.handle }) neighbors += mention
                    }
                }
                inviteName = null
                inviteSlot = null
            },
        )
    }
}

private enum class HonorSlot { HERO, CREW }

@Composable
private fun AuthorChoiceCard(
    title: String,
    subtitle: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Column(
        modifier
            .clip(RoundedCornerShape(16.dp))
            .background(if (selected) OgtColors.secondary else OgtColors.stone)
            .tap(onClick)
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            title,
            fontWeight = FontWeight.SemiBold,
            fontSize = 14.sp,
            color = if (selected) OgtColors.onPrimary else OgtColors.ink,
        )
        Text(
            subtitle,
            fontSize = 11.sp,
            color = if (selected) OgtColors.onPrimary.copy(alpha = 0.85f) else OgtColors.muted,
        )
    }
}

@Composable
private fun ProtagonistRow(
    photo: DrawableResource,
    title: String,
    subtitle: String,
    actionLabel: String?,
    onAction: (() -> Unit)?,
    sessionFace: Boolean = false,
) {
    val face = Modifier.size(40.dp).clip(CircleShape).border(2.dp, OgtColors.secondary.copy(alpha = 0.25f), CircleShape)
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(OgtColors.sand).padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (sessionFace) {
            OgtSessionAvatar(contentDescription = title, modifier = face)
        } else {
            Image(
                painterResource(photo),
                contentDescription = null,
                modifier = face,
                contentScale = ContentScale.Crop,
            )
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = OgtColors.ink)
            Text(subtitle, color = OgtColors.muted, fontSize = 12.sp)
        }
        if (actionLabel != null && onAction != null) {
            Box(
                Modifier.clip(CircleShape).background(OgtColors.stone)
                    .tap(onAction)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            ) {
                Text(actionLabel, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = OgtColors.ink)
            }
        }
    }
}

@Composable
private fun ImpactCategoryCombo(
    categories: List<ImpactCategory> = ImpactCategories,
    selectedId: String,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    onSelect: (String) -> Unit,
    placeholder: String? = null,
    missing: Boolean = false,
) {
    val selected = categories.firstOrNull { it.id == selectedId }
    val density = LocalDensity.current
    var menuWidth by remember { mutableStateOf(0.dp) }
    Box(Modifier.fillMaxWidth()) {
        Row(
            Modifier
                .fillMaxWidth()
                .onGloballyPositioned { coords ->
                    menuWidth = with(density) { coords.size.width.toDp() }
                }
                .clip(RoundedCornerShape(16.dp))
                .background(if (missing) OgtColors.errorContainer else OgtColors.sand)
                .border(
                    1.dp,
                    when {
                        missing -> OgtColors.error
                        expanded -> OgtColors.secondary
                        else -> OgtColors.hairline
                    },
                    RoundedCornerShape(16.dp),
                )
                .tap { onExpandedChange(!expanded) }
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (selected != null && selected.icon.isNotBlank()) {
                CategoryGlyph(selected.icon, highlighted = true)
            }
            Text(
                selected?.label ?: placeholder.orEmpty(),
                modifier = Modifier.weight(1f),
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp,
                color = if (selected == null) {
                    if (missing) OgtColors.onErrorContainer else OgtColors.muted
                } else {
                    OgtColors.ink
                },
            )
            Text(if (expanded) "▴" else "▾", color = OgtColors.secondary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { onExpandedChange(false) },
            modifier = Modifier.width(menuWidth).background(OgtColors.canvas),
        ) {
            categories.forEach { item ->
                val isSelected = item.id == selectedId
                DropdownMenuItem(
                    text = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            if (item.icon.isNotBlank()) {
                                CategoryGlyph(item.icon, highlighted = isSelected)
                            }
                            Text(
                                item.label,
                                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                                fontSize = 14.sp,
                                color = if (isSelected) OgtColors.secondary else OgtColors.ink,
                            )
                        }
                    },
                    onClick = {
                        onSelect(item.id)
                        onExpandedChange(false)
                    },
                )
            }
        }
    }
}

@Composable
private fun CategoryGlyph(icon: String, highlighted: Boolean) {
    Box(
        Modifier.size(28.dp).clip(CircleShape)
            .background(if (highlighted) OgtColors.secondary else OgtColors.stone),
        contentAlignment = Alignment.Center,
    ) {
        Text(icon, fontSize = 13.sp)
    }
}

private fun joinHonorMissing(bits: List<String>): String = when (bits.size) {
    0 -> ""
    1 -> bits[0]
    2 -> "${bits[0]} y ${bits[1]}"
    else -> bits.dropLast(1).joinToString(", ") + " y " + bits.last()
}

@Composable
private fun PublishSectionLabel(
    title: String,
    required: Boolean,
    complete: Boolean? = null,
) {
    val mark = when {
        !required -> null
        complete == false -> "Falta" to OgtColors.error
        complete == true -> "Listo" to OgtColors.secondary
        else -> "Obligatorio" to OgtColors.muted
    }
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            title,
            modifier = Modifier.weight(1f),
            fontWeight = FontWeight.SemiBold,
            fontSize = 14.sp,
            color = OgtColors.ink,
        )
        if (mark != null) {
            Text(
                mark.first,
                color = mark.second,
                fontSize = 11.sp,
                fontWeight = if (complete == false) FontWeight.SemiBold else FontWeight.Medium,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Clip,
            )
        }
    }
}

/** Hit-target explícito: el `OgtPill` original no era clickeable. */
private fun Modifier.tap(onClick: () -> Unit): Modifier = clickable(onClick = onClick)
