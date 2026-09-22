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
import androidx.compose.material3.OutlinedTextField
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
import com.onlygoodthings.shared.data.local.OgtIds
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource

private data class ImpactCategory(val id: String, val label: String, val icon: String)
private data class BarrioSpot(val title: String, val detail: String)

private const val StoryByOther =
    "Camila y otras personas del pasaje recuperaron los canteros abandonados de la esquina. Plantaron 4 tipas jóvenes y armaron un punto comunitario de compostaje domiciliario para reducir residuos húmedos."
private const val StoryBySelf =
    "Con otras personas del pasaje recuperamos los canteros abandonados de la esquina. Plantamos 4 tipas jóvenes y armamos un punto comunitario de compostaje domiciliario para reducir residuos húmedos."

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

private const val StoryTernura =
    "Luna tiene ocho semanas. Duerme en un cesto y se despierta si escuchás el sobre de la comida."
private const val StoryHomenaje =
    "Don Héctor nos enseñó a no pasar de largo si un vecino necesita una mano. Se extraña su risa en el patio."

private fun categoriesFor(kind: ComposePostKind) = when (kind) {
    ComposePostKind.TERNURA -> TernuraCategories
    ComposePostKind.HOMENAJE -> HomenajeCategories
    else -> ImpactCategories
}

private fun defaultCategory(kind: ComposePostKind) =
    if (kind == ComposePostKind.HOMENAJE) "" else categoriesFor(kind).first().id

private fun defaultStory(kind: ComposePostKind, self: Boolean) = when (kind) {
    ComposePostKind.TERNURA -> StoryTernura
    ComposePostKind.HOMENAJE -> StoryHomenaje
    else -> if (self) StoryBySelf else StoryByOther
}

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
        mutableStateOf(existing?.body?.takeIf { it.isNotBlank() } ?: if (storyMode) "" else defaultStory(kind, false))
    }
    val draftMedia = remember(kind) {
        mutableStateListOf<OgtPickedMedia>().also { list ->
            if (!storyMode) {
                list += OgtPickedMedia("jardin", MediaKind.IMAGE, "jardin", fromDevice = false)
            }
        }
    }
    var includePlace by remember { mutableStateOf(false) }
    var spotIndex by remember { mutableStateOf(0) }
    var toast by remember { mutableStateOf(false) }
    val mentionPool = remember(me.id, db.follows.size) { db.mentionPool(me.id) }
    var protagonist by remember(kind) {
        mutableStateOf(
            if (storyMode) null
            else db.userOrNull(OgtIds.Camila)?.let(db::toMention),
        )
    }
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
        if (!storyTouched) story = defaultStory(kind, self)
    }

    fun publish() {
        if ((honorForm || ternuraForm) && !canPublish) {
            triedPublish = true
            return
        }
        if (draftMedia.isEmpty() && !storyMode) {
            draftMedia += OgtPickedMedia("jardin", MediaKind.IMAGE, "jardin", fromDevice = false)
        }
        if (draftMedia.isEmpty() && !editing) return
        val tag = when {
            ternuraForm -> "Ternura"
            else -> categories.firstOrNull { it.id == category }?.label ?: "Ayuda vecinal"
        }
        if (editing && existing != null) {
            optimisticEditPost(db, auth.feed, scope, me.id, existing.id, story, tag) {
                session.persistSocialFeed()
            }
            toast = true
            return
        }
        val now = currentEpochMs()
        val postId = "post-pub-$now"
        db.posts += LocalSocialPost(
            id = postId,
            authorKind = AuthorKind.USER,
            authorUserId = me.id,
            authorCompanyId = null,
            place = when {
                includePlace -> spot.title
                ternuraForm -> ""
                else -> me.barrio
            },
            timeLabel = "Ahora",
            tag = tag,
            body = story,
            impactCount = 0,
            commentCount = 0,
            isStory = false,
            storyLabel = null,
            createdAtEpochMs = now,
            honoreeName = protagonist
                ?.takeIf { it.namedOnly }
                ?.displayName,
        )
        db.postPeople += LocalPostPerson(postId, me.id, PostPersonRole.AUTHOR)
        val heroId = when {
            ternuraForm -> null
            authorIsSelf -> me.id
            else -> protagonist?.userId
        }
        if (!heroId.isNullOrBlank()) {
            db.postPeople += LocalPostPerson(postId, heroId, PostPersonRole.PROTAGONIST)
        }
        neighbors.mapNotNull { it.userId }.distinct().forEach { uid ->
            if (uid != me.id && uid != heroId) {
                db.postPeople += LocalPostPerson(postId, uid, PostPersonRole.PARTICIPANT)
            }
        }
        if (!ternuraForm && !authorIsSelf) {
            protagonist?.honorId?.let { db.attachHonorToPost(postId, it, PostPersonRole.PROTAGONIST) }
        }
        neighbors.forEach { hit ->
            hit.honorId?.let { db.attachHonorToPost(postId, it, PostPersonRole.PARTICIPANT) }
        }
        draftMedia.take(PostMediaRules.MAX_ITEMS).forEachIndexed { order, item ->
            val video = item.kind == MediaKind.VIDEO
            val url = if (item.fromDevice) "file://${item.path}" else "asset://${item.path}"
            val poster = when {
                item.posterPath != null -> "file://${item.posterPath}"
                video -> "asset://feed_story_playa"
                else -> null
            }
            db.postMedia += LocalPostMedia(
                id = "$postId-m$order",
                postId = postId,
                kind = item.kind,
                url = url,
                posterUrl = poster,
                sortOrder = order,
                assetKey = if (item.fromDevice) "" else if (video) "feed_story_playa" else item.path,
                altText = tag,
            )
            if (item.fromDevice) {
                OgtMediaCache.ingest(url)
                poster?.let { OgtMediaCache.ingest(it) }
            }
        }
        db.recordFeedEvent(me.id, postId, FeedEventKind.SHARE)
        db.bumpFeed()
        val published = db.post(postId)
        scope.launch {
            auth.ensureDevBearer()
            val remote = runCatching {
                auth.feed.publishPost(
                    body = story,
                    topic = tag,
                    media = draftMedia.take(PostMediaRules.MAX_ITEMS).mapIndexed { order, item ->
                        val video = item.kind == MediaKind.VIDEO
                        PostMediaItem(
                            id = "$postId-m$order",
                            kind = item.kind,
                            url = if (item.fromDevice) "file://${item.path}" else "asset://${item.path}",
                            posterUrl = item.posterPath?.let { "file://$it" },
                            sortOrder = order,
                            altText = tag,
                            assetKey = if (item.fromDevice) "" else if (video) "feed_story_playa" else item.path,
                        )
                    },
                    protagonistUserId = heroId,
                    participantUserIds = neighbors.mapNotNull { it.userId }.distinct(),
                    honoreeName = protagonist?.takeIf { it.namedOnly }?.displayName,
                )
            }.getOrNull()
            if (remote != null) db.upsertRemoteSocial(remote)
            announcePublishedPost(db, published)
        }
        session.persistHonors()
        val mentionedIds = buildList {
            if (!ternuraForm && !authorIsSelf) protagonist?.userId?.let(::add)
            neighbors.mapNotNull { it.userId }.forEach(::add)
        }
        // En preview el destinatario comparte este dispositivo: el banner verifica el canal.
        db.notifyMentionedUsers(me.id, postId, mentionedIds).forEach(::showSystemNotice)
        val honorHits = buildList {
            if (!ternuraForm && !authorIsSelf) protagonist?.let { add(it to PostPersonRole.PROTAGONIST) }
            neighbors.forEach { add(it to PostPersonRole.PARTICIPANT) }
        }
        scope.launch {
            honorHits.forEach { (hit, role) ->
                val token = db.honors.firstOrNull { it.id == hit.honorId }?.claimToken ?: return@forEach
                auth.honor.attach(token, postId, role)
            }
            mentionedIds.distinct().filter { it != me.id }.forEach { rid ->
                val role = if (rid == heroId) "PROTAGONIST" else "PARTICIPANT"
                auth.notices.notifyMention(rid, postId, role)
            }
        }
        toast = true
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

                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            OgtPrimaryButton(
                                when {
                                    editing -> "Guardar cambios"
                                    kind == ComposePostKind.TERNURA -> "Compartir esta ternura"
                                    kind == ComposePostKind.HOMENAJE -> "Publicar el homenaje"
                                    authorIsSelf -> "Compartir con la comunidad"
                                    else -> "Hacerlo visible en la comunidad"
                                },
                                onClick = ::publish,
                            )
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
) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(OgtColors.sand).padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Image(
            painterResource(photo),
            contentDescription = null,
            modifier = Modifier.size(40.dp).clip(CircleShape).border(2.dp, OgtColors.secondary.copy(alpha = 0.25f), CircleShape),
            contentScale = ContentScale.Crop,
        )
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
