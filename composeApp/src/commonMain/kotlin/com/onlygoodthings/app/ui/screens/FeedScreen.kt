package com.onlygoodthings.app.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.Velocity
import com.onlygoodthings.app.theme.OgtDimens
import com.onlygoodthings.app.theme.OgtMotion
import com.onlygoodthings.app.ui.components.OgtLoader
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
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
import androidx.compose.ui.zIndex
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import com.onlygoodthings.app.data.LocalOgtDb
import com.onlygoodthings.app.data.LocalOgtSession
import com.onlygoodthings.app.data.LocalOgtSession
import com.onlygoodthings.app.resources.Res
import com.onlygoodthings.app.resources.feed_avatar_carlos
import com.onlygoodthings.app.resources.feed_avatar_mariana
import com.onlygoodthings.app.resources.feed_avatar_me
import com.onlygoodthings.app.resources.feed_avatar_reply
import com.onlygoodthings.app.resources.feed_avatar_roberto
import com.onlygoodthings.app.resources.feed_avatar_sofia
import com.onlygoodthings.app.resources.feed_logo
import com.onlygoodthings.app.resources.feed_photo_arboles
import com.onlygoodthings.app.resources.feed_photo_bebederos
import com.onlygoodthings.app.resources.feed_story_compost
import com.onlygoodthings.app.resources.feed_story_donacion
import com.onlygoodthings.app.resources.feed_story_me
import com.onlygoodthings.app.resources.feed_story_patitas
import com.onlygoodthings.app.resources.feed_story_playa
import com.onlygoodthings.app.resources.feed_story_solar
import com.onlygoodthings.app.resources.qs_feed
import com.onlygoodthings.app.resources.qs_notifications
import com.onlygoodthings.app.theme.OgtColors
import com.onlygoodthings.app.i18n.LocalOgtCopy
import com.onlygoodthings.app.resources.qs_invite
import com.onlygoodthings.app.resources.qs_paw
import com.onlygoodthings.app.resources.qs_pets
import com.onlygoodthings.app.resources.qs_search
import com.onlygoodthings.app.ui.components.CircleIconButton
import com.onlygoodthings.app.ui.components.MediaPagerIndicator
import com.onlygoodthings.app.ui.components.OgtCaption
import com.onlygoodthings.app.ui.components.OgtPill
import com.onlygoodthings.app.ui.components.LostAlertMap
import com.onlygoodthings.app.ui.components.OgtPrimaryButton
import com.onlygoodthings.app.ui.components.OgtStitchIcon
import com.onlygoodthings.app.ui.components.PostActionRow
import com.onlygoodthings.shared.data.local.LocalAnimalListing
import com.onlygoodthings.shared.data.local.LocalComment
import com.onlygoodthings.shared.data.local.LocalPostMedia
import com.onlygoodthings.shared.data.local.LocalSocialPost
import com.onlygoodthings.shared.data.local.OgtIds
import com.onlygoodthings.shared.domain.AuthorKind
import com.onlygoodthings.shared.domain.FeedCardKind
import com.onlygoodthings.shared.domain.FeedEventKind
import com.onlygoodthings.shared.domain.FeedMode
import com.onlygoodthings.shared.domain.FeedTopicFamily
import com.onlygoodthings.shared.domain.MediaKind
import com.onlygoodthings.shared.domain.feedCardKind
import com.onlygoodthings.shared.domain.canRsvpToGathering
import com.onlygoodthings.shared.domain.gatheringWhenWhere
import com.onlygoodthings.shared.domain.isGatheringPost
import com.onlygoodthings.shared.realtime.OgtRealtime
import com.onlygoodthings.shared.realtime.OgtSdk
import com.onlygoodthings.shared.realtime.currentEpochMs
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource

private const val FeedPageSize = 12

/** Feed Social de Buenas Acciones — fiel a Stitch `6b7ded9b…`. */
@Composable
fun FeedScreen(
    onOpenPost: (String) -> Unit,
    onNotifications: () -> Unit,
    onProfile: () -> Unit,
    onMessages: () -> Unit = {},
    onInvite: () -> Unit = {},
    onOpenNeighbor: (String) -> Unit = {},
    onPetsMap: () -> Unit = {},
    onAdopt: () -> Unit = {},
    onEditAdoption: (String) -> Unit = {},
) {
    val db = LocalOgtDb.current
    val me = LocalOgtSession.current.me()
    val lostTick = db.lostEpoch
    val socialTick by db.socialTick.collectAsState()
    val feedTick by db.feedTick.collectAsState()
    val followed = remember { followedNeighbors() }
    val stories = remember { storiesFrom(followed) }
    var feedMode by remember { mutableStateOf(FeedMode.HOME) }
    var feedFamily by remember { mutableStateOf<FeedTopicFamily?>(null) }
    val copy = LocalOgtCopy.current
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    var page by remember { mutableStateOf(1) }
    var refreshTick by remember { mutableStateOf(0) }
    var refreshing by remember { mutableStateOf(false) }
    var pullPx by remember { mutableStateOf(0f) }
    var feedPosts by remember { mutableStateOf(listOf<LocalSocialPost>()) }
    var incomingToast by remember { mutableStateOf(false) }
    var suppressIncomingToast by remember { mutableStateOf(false) }
    val seenPostIds = remember { mutableStateListOf<String>() }
    LaunchedEffect(feedMode, feedFamily) { page = 1 }
    LaunchedEffect(me.id, feedMode, feedFamily, refreshTick, feedTick, socialTick) {
        feedPosts = db.visibleFeed(me.id, feedMode, feedFamily)
        if (seenPostIds.isEmpty()) seenPostIds.addAll(db.feedPosts().map { it.id })
    }
    LaunchedEffect(feedTick) {
        val first = feedPosts.firstOrNull()
        if (first != null && db.isOwnPost(first, me.aliases())) listState.animateScrollToItem(0)
    }
    LaunchedEffect(Unit) {
        if (!OgtSdk.isStarted()) return@LaunchedEffect
        runCatching {
            OgtRealtime().observeSocialPosts().collect { live ->
                db.applySocialLive(live)
            }
        }
    }
    LaunchedEffect(db.posts.size, feedTick) {
        if (seenPostIds.isEmpty()) {
            seenPostIds.addAll(db.feedPosts().map { it.id })
            return@LaunchedEffect
        }
        val fresh = db.feedPosts().filter { post ->
            post.id !in seenPostIds &&
                post.authorUserId != null &&
                post.authorUserId != me.id &&
                db.shouldShowInFeed(me.id, post, feedMode, feedFamily)
        }
        seenPostIds.addAll(db.feedPosts().map { it.id })
        if (fresh.isNotEmpty() && !suppressIncomingToast) incomingToast = true
    }
    val hasMore = false
    LaunchedEffect(incomingToast) {
        if (!incomingToast) return@LaunchedEffect
        delay(4_200)
        incomingToast = false
    }
    LaunchedEffect(listState, hasMore, feedPosts.size) {
        snapshotFlow {
            val last = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            val total = listState.layoutInfo.totalItemsCount
            last to total
        }.collect { (last, total) ->
            if (hasMore && total > 0 && last >= total - 3 && feedPosts.size == page * FeedPageSize) {
                page += 1
            }
        }
    }
    fun revealFresh() {
        incomingToast = false
        page = 1
        refreshTick += 1
        scope.launch { listState.animateScrollToItem(0) }
    }
    fun forceRefresh() {
        if (refreshing) return
        scope.launch {
            suppressIncomingToast = true
            refreshing = true
            delay(380)
            page = 1
            refreshTick += 1
            refreshing = false
            pullPx = 0f
            delay(80)
            suppressIncomingToast = false
        }
    }
    val pull = remember(listState) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                val atTop = listState.firstVisibleItemIndex == 0 && listState.firstVisibleItemScrollOffset == 0
                if (atTop && available.y > 0f && !refreshing) {
                    pullPx = (pullPx + available.y * 0.42f).coerceAtMost(140f)
                    return Offset(0f, available.y)
                }
                if (available.y < 0f && pullPx > 0f) {
                    pullPx = (pullPx + available.y).coerceAtLeast(0f)
                    return Offset(0f, available.y)
                }
                return Offset.Zero
            }

            override suspend fun onPreFling(available: Velocity): Velocity {
                if (pullPx > 72f) forceRefresh() else pullPx = 0f
                return Velocity.Zero
            }
        }
    }
    var viewingStoryId by remember { mutableStateOf<String?>(null) }
    val seenStoryIds = remember { mutableStateListOf<String>() }
    Box(Modifier.fillMaxSize().background(Color(0xFFFBF8FC))) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize().nestedScroll(pull),
            verticalArrangement = Arrangement.spacedBy(0.dp),
        ) {
            item(key = "chrome-header") {
                FeedHeader(onNotifications = onNotifications, onMessages = onMessages, onProfile = onProfile)
            }
            item(key = "chrome-stories") {
                FeedStories(
                    stories = stories,
                    seenIds = seenStoryIds,
                    onOpen = { viewingStoryId = it },
                )
            }
            item(key = "chrome-mode") {
                FeedModeRow(mode = feedMode, onMode = { feedMode = it }, copy = copy)
            }
            item(key = "chrome-family") {
                FeedFamilyRow(family = feedFamily, onFamily = { feedFamily = it }, copy = copy)
            }
            if (refreshing || pullPx > 16f) {
                item(key = "chrome-refresh") {
                    Box(Modifier.fillMaxWidth().padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
                        OgtLoader(size = 28.dp)
                    }
                }
            }
            items(feedPosts, key = { it.id }) { post ->
                val look = postLook(post, followed, db.authorName(post))
                val listing = remember(post.id, lostTick) { post.listingId?.let { id -> db.animals.firstOrNull { it.id == id } } }
                val comments = remember(post.id, socialTick) { db.commentsOf(post.id) }
                val kind = feedCardKind(post.tag, listing?.kind, post.sourceUrl)
                NeighborPostCard(
                    post = post.copy(commentCount = maxOf(post.commentCount, comments.size)),
                    profile = look,
                    media = db.mediaOf(post.id),
                    listing = listing,
                    kind = kind,
                    discovery = db.isDiscovery(me.id, post),
                    comments = comments,
                    onOpen = {
                        db.recordFeedEvent(me.id, post.id, FeedEventKind.IMPRESSION)
                        onOpenPost(post.id)
                    },
                    onOpenAuthor = {
                        val authorId = post.authorUserId
                        if (authorId != null) {
                            db.recordFeedEvent(me.id, post.id, FeedEventKind.PROFILE_TAP)
                            onOpenNeighbor(authorId)
                        }
                    },
                    onInvite = onInvite,
                    onClap = { db.recordFeedEvent(me.id, post.id, FeedEventKind.CLAP) },
                    onHeart = { db.recordFeedEvent(me.id, post.id, FeedEventKind.HEART) },
                    onPetsMap = onPetsMap,
                    onAdopt = onAdopt,
                    onEditAdoption = { onEditAdoption(post.id) },
                    isAdoptionAuthor = me.owns(post.authorUserId) || me.owns(listing?.reporterUserId),
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                )
            }
            item(key = "chrome-end") {
                if (hasMore) {
                    Box(Modifier.fillMaxWidth().padding(vertical = 16.dp), contentAlignment = Alignment.Center) {
                        OgtLoader(size = 24.dp)
                    }
                }
                Spacer(Modifier.height(88.dp))
            }
        }
        AnimatedVisibility(
            visible = incomingToast,
            enter = OgtMotion.enterDown,
            exit = OgtMotion.exitUp,
            modifier = Modifier.align(Alignment.TopCenter).padding(top = 64.dp).zIndex(3f),
        ) {
            Text(
                "Hay contenido nuevo",
                color = OgtColors.onPrimary,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .shadow(10.dp, RoundedCornerShape(OgtDimens.pill), ambientColor = OgtColors.ink.copy(alpha = 0.08f))
                    .clip(RoundedCornerShape(OgtDimens.pill))
                    .background(OgtColors.primary)
                    .clickable { revealFresh() }
                    .padding(horizontal = 16.dp, vertical = 10.dp),
            )
        }
        viewingStoryId?.let { startId ->
            Dialog(
                onDismissRequest = { viewingStoryId = null },
                properties = DialogProperties(usePlatformDefaultWidth = false),
            ) {
                StoryViewer(
                    stories = stories,
                    startId = startId,
                    onSeen = { id -> if (id !in seenStoryIds) seenStoryIds += id },
                    onClose = { viewingStoryId = null },
                    onOpenAuthor = { userId ->
                        viewingStoryId = null
                        if (userId != null) onOpenNeighbor(userId) else onProfile()
                    },
                )
            }
        }
    }
}

@Composable
private fun FeedHeader(onNotifications: () -> Unit, onMessages: () -> Unit, onProfile: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(Color.White.copy(alpha = 0.92f))
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        androidx.compose.foundation.Image(
            painterResource(Res.drawable.feed_logo),
            contentDescription = "OnlyGoodThings",
            modifier = Modifier.size(36.dp),
            contentScale = ContentScale.Fit,
        )
        Spacer(Modifier.width(8.dp))
        Text("OnlyGoodThings", color = OgtColors.primary, fontWeight = FontWeight.Bold, fontSize = 20.sp)
        Spacer(Modifier.weight(1f))
        Box {
            CircleIconButton(Res.drawable.qs_notifications, "Alertas", onNotifications)
            Box(
                Modifier.align(Alignment.TopEnd).padding(8.dp).size(7.dp).clip(CircleShape).background(OgtColors.ink),
            )
        }
        Spacer(Modifier.width(6.dp))
        CircleIconButton(Res.drawable.qs_feed, "Mensajes", onMessages)
        Spacer(Modifier.width(6.dp))
        androidx.compose.foundation.Image(
            painterResource(Res.drawable.feed_avatar_me),
            contentDescription = "Perfil",
            modifier = Modifier.size(44.dp).clip(CircleShape).border(1.dp, OgtColors.hairline, CircleShape).clickable(onClick = onProfile),
            contentScale = ContentScale.Crop,
        )
    }
}

private const val StoryRailPage = 5

private data class FollowedNeighbor(
    val storyId: String,
    val postId: String,
    val userId: String,
    val firstName: String,
    val fullName: String,
    val avatar: DrawableResource,
    val photo: DrawableResource,
    val storyCaption: String,
    val timeLabel: String,
    val hashtags: List<String>,
    val recency: Int,
    val verified: Boolean = false,
)

private data class FeedStoryItem(
    val id: String,
    val label: String,
    val author: String,
    val caption: String,
    val timeLabel: String,
    val avatar: DrawableResource,
    val image: DrawableResource,
    val mine: Boolean,
    val recency: Int,
    val userId: String? = null,
)

/** Riel de historias: solo el grafo. El listado vertical lo rankea HOME / FOLLOWING. */
private fun followedNeighbors(): List<FollowedNeighbor> = listOf(
    FollowedNeighbor("follow-valeria", OgtIds.PostPlaya, OgtIds.ValeriaP, "Valeria", "Valeria P.", Res.drawable.feed_avatar_reply, Res.drawable.feed_story_playa, "Hoy juntamos plásticos en la costa con el club de remo.", "Hace 20 min", listOf("#LimpiezaCostera", "#AccionComunitaria"), 95),
    FollowedNeighbor("follow-carlosr", OgtIds.PostPatitas, OgtIds.CarlosR, "Carlos", "Carlos R.", Res.drawable.feed_avatar_carlos, Res.drawable.feed_story_patitas, "Milo y Rocco ya tienen patio nuevo en el refugio.", "Hace 1 h", listOf("#RescateAnimal", "#RefugioPatitas"), 90),
    FollowedNeighbor("follow-camila", OgtIds.PostCompost, OgtIds.Camila, "Camila", "Camila T.", Res.drawable.feed_avatar_sofia, Res.drawable.feed_story_compost, "El punto comunitario de compost ya suma 45 kg.", "Hace 3 h", listOf("#Compost", "#HuertaComunitaria"), 80),
    FollowedNeighbor("follow-mariana", OgtIds.PostArboles, OgtIds.Mariana, "Mariana", "Mariana Cordero", Res.drawable.feed_avatar_mariana, Res.drawable.feed_photo_arboles, "Plantamos 45 árboles nativos en el Parque Central.", "Hace 5 h", listOf("#ReforestacionUrbana", "#AccionComunitaria"), 70, verified = true),
    FollowedNeighbor("follow-carlosg", OgtIds.PostBebederos, OgtIds.CarlosG, "Carlos G.", "Carlos Gutiérrez", Res.drawable.feed_avatar_carlos, Res.drawable.feed_photo_bebederos, "Los bebederos solares ya están dando agua fresca.", "Hace 8 h", listOf("#EnergiaSolar", "#ProteccionAnimal"), 60),
    FollowedNeighbor("follow-sofia", OgtIds.PostTaller, OgtIds.Sofia, "Sofía", "Sofía M.", Res.drawable.feed_avatar_sofia, Res.drawable.feed_photo_arboles,         "Plantines de albahaca para quien se sume el sábado.", "Ayer", listOf("#HuertaComunitaria", "#PalermoVerde"), 50, verified = true),
    FollowedNeighbor("follow-lucas", OgtIds.PostBebederoPlaza, OgtIds.Lucas, "Lucas", "Lucas V.", Res.drawable.feed_avatar_me, Res.drawable.feed_photo_bebederos, "Revisamos el filtro del bebedero de la plaza.", "Ayer", listOf("#CuidadoComunitario"), 40),
    FollowedNeighbor("follow-mateo", OgtIds.PostTrueque, OgtIds.Mateo, "Mateo", "Mateo R.", Res.drawable.feed_avatar_roberto, Res.drawable.feed_story_donacion, "Cerámicas y un par de bicis listas para intercambiar.", "Hace 2 d", listOf("#Trueque", "#BancoDeTiempo"), 30),
    FollowedNeighbor("follow-diego", OgtIds.PostRescate, OgtIds.DiegoF, "Diego", "Diego F.", Res.drawable.feed_avatar_carlos, Res.drawable.feed_story_patitas, "Dos gatitos en tránsito hasta el fin de semana.", "Hace 2 d", listOf("#RescateAnimal"), 20),
    FollowedNeighbor("follow-lucia", OgtIds.PostMerienda, OgtIds.Lucia, "Lucía", "Lucía V.", Res.drawable.feed_avatar_mariana, Res.drawable.feed_story_donacion, "La merienda comunitaria salió con lo que sobró del domingo.", "Hace 3 d", listOf("#Merienda", "#Comedor"), 10),
)

private fun storiesFrom(followed: List<FollowedNeighbor>): List<FeedStoryItem> {
    val mine = FeedStoryItem(
        id = "story-me",
        label = "Tu historia",
        author = "Vos",
        caption = "Un recorte de lo bueno que viste o hiciste hoy.",
        timeLabel = "Ahora",
        avatar = Res.drawable.feed_avatar_me,
        image = Res.drawable.feed_story_me,
        mine = true,
        recency = Int.MAX_VALUE,
    )
    val others = followed.map { row ->
        FeedStoryItem(
            id = row.storyId,
            label = row.firstName,
            author = row.fullName,
            caption = row.storyCaption,
            timeLabel = row.timeLabel,
            avatar = row.avatar,
            image = row.photo,
            mine = false,
            recency = row.recency,
            userId = row.userId,
        )
    }
    return listOf(mine) + others
}

private data class PostLook(
    val postId: String,
    val fullName: String,
    val avatar: DrawableResource,
    val photo: DrawableResource,
    val hashtags: List<String>,
    val verified: Boolean,
)

private fun postLook(
    post: LocalSocialPost,
    followed: List<FollowedNeighbor>,
    authorName: String,
): PostLook {
    val row = followed.firstOrNull { it.postId == post.id }
    if (row != null) {
        return PostLook(row.postId, row.fullName, row.avatar, row.photo, row.hashtags, row.verified)
    }
    return PostLook(
        postId = post.id,
        fullName = authorName,
        avatar = commentAvatar(post.authorUserId ?: ""),
        photo = Res.drawable.feed_story_donacion,
        hashtags = listOf("#${post.tag.replace(" ", "")}"),
        verified = post.authorKind == AuthorKind.COMPANY,
    )
}

@Composable
private fun FeedModeRow(
    mode: FeedMode,
    onMode: (FeedMode) -> Unit,
    copy: com.onlygoodthings.app.i18n.OgtCopy,
) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        ModeChip(copy.feedForYou, selected = mode == FeedMode.HOME) { onMode(FeedMode.HOME) }
        ModeChip(copy.feedFollowing, selected = mode == FeedMode.FOLLOWING) { onMode(FeedMode.FOLLOWING) }
    }
}

@Composable
private fun FeedFamilyRow(
    family: FeedTopicFamily?,
    onFamily: (FeedTopicFamily?) -> Unit,
    copy: com.onlygoodthings.app.i18n.OgtCopy,
) {
    val options = listOf(
        null to copy.feedFilterAll,
        FeedTopicFamily.PETS to copy.feedFilterPets,
        FeedTopicFamily.COMMUNITY to copy.feedFilterCommunity,
        FeedTopicFamily.FOOD to copy.feedFilterFood,
        FeedTopicFamily.OCEAN to copy.feedFilterOcean,
        FeedTopicFamily.NEWS to copy.feedFilterNews,
    )
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        options.forEach { (value, label) ->
            ModeChip(label, selected = family == value) { onFamily(value) }
        }
    }
}

@Composable
private fun ModeChip(label: String, selected: Boolean, onClick: () -> Unit) {
    val fill = if (selected) OgtColors.ink else OgtColors.sand
    val ink = if (selected) Color.White else OgtColors.ink
    Box(
        Modifier.clip(CircleShape).background(fill).clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 8.dp),
    ) {
        Text(label, color = ink, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
    }
}

@Composable
private fun FeedStories(
    stories: List<FeedStoryItem>,
    seenIds: List<String>,
    onOpen: (String) -> Unit,
) {
    val mine = stories.first { it.mine }
    val others = stories.filter { !it.mine }
    var visibleOthers by remember { mutableStateOf(StoryRailPage.coerceAtMost(others.size)) }
    val railScroll = rememberScrollState()
    LaunchedEffect(railScroll.value, railScroll.maxValue, visibleOthers) {
        val nearEnd = railScroll.maxValue > 0 && railScroll.value >= railScroll.maxValue - 96
        if (nearEnd && visibleOthers < others.size) {
            visibleOthers = (visibleOthers + StoryRailPage).coerceAtMost(others.size)
        }
    }
    Row(
        Modifier.fillMaxWidth().horizontalScroll(railScroll).padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StoryCircle(mine, seen = mine.id in seenIds, onOpen = { onOpen(mine.id) })
        others.take(visibleOthers).forEach { item ->
            StoryCircle(item, seen = item.id in seenIds, onOpen = { onOpen(item.id) })
        }
    }
}

@Composable
private fun StoryCircle(item: FeedStoryItem, seen: Boolean, onOpen: () -> Unit) {
    Column(
        Modifier.width(68.dp).clickable(onClick = onOpen),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(contentAlignment = Alignment.Center) {
            if (item.mine) {
                Box(Modifier.size(64.dp).clip(CircleShape).background(OgtColors.sand), contentAlignment = Alignment.Center) {
                    CirclePhoto(item.avatar, 60.dp)
                }
                Box(
                    Modifier.align(Alignment.BottomEnd).offset(x = 2.dp, y = 2.dp)
                        .size(20.dp).clip(CircleShape).background(OgtColors.primary),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("＋", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            } else {
                val ring = if (seen) {
                    Brush.linearGradient(listOf(Color(0xFFD4D1D6), Color(0xFFD4D1D6)))
                } else {
                    Brush.linearGradient(listOf(OgtColors.ink, OgtColors.secondary))
                }
                Box(
                    Modifier.size(64.dp).clip(CircleShape).background(ring),
                    contentAlignment = Alignment.Center,
                ) {
                    Box(Modifier.size(58.dp).clip(CircleShape).background(Color.White), contentAlignment = Alignment.Center) {
                        CirclePhoto(item.avatar, 54.dp)
                    }
                }
            }
        }
        Text(item.label, fontSize = 11.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun StoryViewer(
    stories: List<FeedStoryItem>,
    startId: String,
    onSeen: (String) -> Unit,
    onClose: () -> Unit,
    onOpenAuthor: (String?) -> Unit,
) {
    val sequence = remember(startId, stories) {
        val mine = stories.first { it.mine }
        val others = stories.filter { !it.mine }
        if (startId == mine.id) listOf(mine) + others else others
    }
    val startIndex = sequence.indexOfFirst { it.id == startId }.coerceAtLeast(0)
    val pagerState = rememberPagerState(initialPage = startIndex) { sequence.size }
    val scope = rememberCoroutineScope()
    LaunchedEffect(pagerState.currentPage) {
        onSeen(sequence[pagerState.currentPage].id)
        delay(5200)
        if (pagerState.currentPage < sequence.lastIndex) {
            pagerState.animateScrollToPage(pagerState.currentPage + 1)
        } else {
            onClose()
        }
    }
    Box(Modifier.fillMaxSize().background(Color(0xFF111113))) {
        HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
            val item = sequence[page]
            Box(Modifier.fillMaxSize()) {
                Image(
                    painterResource(item.image),
                    contentDescription = item.label,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
                Box(
                    Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0x99000000), Color.Transparent, Color(0xB3000000)))),
                )
                // Las zonas de toque no cubren la barra superior: si no, se comen la X.
                Row(Modifier.fillMaxSize().padding(top = 132.dp)) {
                    Box(
                        Modifier.weight(1f).fillMaxHeight().pointerInput(page) {
                            detectTapGestures {
                                if (pagerState.currentPage > 0) {
                                    scope.launch { pagerState.animateScrollToPage(pagerState.currentPage - 1) }
                                } else {
                                    onClose()
                                }
                            }
                        },
                    )
                    Box(
                        Modifier.weight(2f).fillMaxHeight().pointerInput(page) {
                            detectTapGestures {
                                if (pagerState.currentPage < sequence.lastIndex) {
                                    scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) }
                                } else {
                                    onClose()
                                }
                            }
                        },
                    )
                }
                Column(Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 18.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        sequence.indices.forEach { index ->
                            Box(
                                Modifier.weight(1f).height(3.dp).clip(CircleShape)
                                    .background(if (index <= page) Color.White else Color.White.copy(alpha = 0.28f)),
                            )
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    Row(
                        Modifier.zIndex(3f).clickable { onOpenAuthor(item.userId) },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        CirclePhoto(item.avatar, 36.dp)
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(item.author, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                            Text(item.timeLabel, color = Color.White.copy(alpha = 0.8f), fontSize = 12.sp)
                        }
                        Spacer(Modifier.size(48.dp))
                    }
                    Spacer(Modifier.weight(1f))
                    Text(item.caption, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Medium)
                    Spacer(Modifier.height(28.dp))
                }
                Box(
                    Modifier
                        .align(Alignment.TopEnd)
                        .zIndex(2f)
                        .padding(top = 28.dp, end = 8.dp)
                        .size(48.dp)
                        .clip(CircleShape)
                        .clickable(onClick = onClose),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("✕", color = Color.White, fontSize = 20.sp)
                }
            }
        }
    }
}

@Composable
private fun NeighborPostCard(
    post: LocalSocialPost,
    profile: PostLook,
    media: List<LocalPostMedia>,
    listing: LocalAnimalListing?,
    kind: FeedCardKind,
    discovery: Boolean,
    comments: List<LocalComment>,
    onOpen: () -> Unit,
    onOpenAuthor: () -> Unit,
    onInvite: () -> Unit,
    onClap: () -> Unit,
    onHeart: () -> Unit = {},
    onPetsMap: () -> Unit,
    onAdopt: () -> Unit,
    onEditAdoption: () -> Unit,
    isAdoptionAuthor: Boolean,
    modifier: Modifier = Modifier,
) {
    Box(modifier) {
    when (kind) {
        FeedCardKind.LOST_PET -> LostPetFeedCard(
            post = post,
            profile = profile,
            media = media,
            listing = listing,
            sightings = listing?.let { LocalOgtDb.current.sightingsOf(it.id) }.orEmpty(),
            onOpen = onOpen,
            onOpenAuthor = onOpenAuthor,
        )
        FeedCardKind.ADOPTION -> AdoptionFeedCard(
            post = post,
            profile = profile,
            media = media,
            listing = listing,
            onOpen = onOpen,
            onOpenAuthor = onOpenAuthor,
            isAuthor = isAdoptionAuthor,
            onAdopt = onAdopt,
            onEdit = onEditAdoption,
            onClap = onClap,
        )
        FeedCardKind.TERNURA -> TernuraFeedCard(
            post = post,
            profile = profile,
            media = media,
            onOpen = onOpen,
            onClap = onClap,
        )
        FeedCardKind.HOMENAJE -> HomenajeFeedCard(
            post = post,
            profile = profile,
            media = media,
            onOpen = onOpen,
            onClap = onClap,
            onHeart = onHeart,
        )
        else -> StoryFeedCard(
            post = post,
            profile = profile,
            media = media,
            kind = kind,
            discovery = discovery,
            comments = comments,
            onOpen = onOpen,
            onOpenAuthor = onOpenAuthor,
            onInvite = onInvite,
            onClap = onClap,
        )
    }
    }
}

@Composable
private fun LostPetFeedCard(
    post: LocalSocialPost,
    profile: PostLook,
    media: List<LocalPostMedia>,
    listing: LocalAnimalListing?,
    sightings: List<com.onlygoodthings.shared.data.local.LocalSighting>,
    onOpen: () -> Unit,
    onOpenAuthor: () -> Unit,
) {
    val copy = LocalOgtCopy.current
    val session = LocalOgtSession.current
    val radius = listing?.alertRadiusM ?: 2000
    val lastSeen = listing?.lastSeenPlace?.ifBlank { null } ?: listing?.place?.ifBlank { post.place } ?: post.place
    val resolved = listing?.resolved == true
    FeedArticle(onOpen = onOpen) {
        PostHeader(
            name = profile.fullName,
            place = post.place,
            time = post.timeLabel,
            avatar = profile.avatar,
            verified = profile.verified,
            onOpenAuthor = onOpen,
        )
        Column(
            Modifier.padding(horizontal = 16.dp).padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OgtStitchIcon(Res.drawable.qs_search, copy.petsLost, tint = OgtColors.secondary)
                OgtPill(if (resolved) "Ya está en casa" else copy.petsLost, OgtColors.sunset, OgtColors.sunsetText)
            }
            OgtCaption("Última vista · $lastSeen · ${radius} m")
            listing?.marks?.takeIf { it.isNotBlank() }?.let { OgtCaption(it) }
            ListingPhotoStrip(media = media, fallback = profile.photo, onOpen = onOpen, height = 140.dp)
            if (listing?.latitude != null && listing.longitude != null) {
                LostAlertMap(
                    listing = listing,
                    sightings = sightings,
                    here = session.here(),
                    height = 148.dp,
                    onSelectSighting = { onOpen() },
                    interactive = false,
                )
            }
            Text(
                listing?.displayPetName()?.ifBlank { null } ?: post.tag,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
                color = OgtColors.ink,
            )
            OgtCaption(listing?.description ?: post.body)
            OgtPrimaryButton(if (resolved) "Ver ficha" else "Ver la búsqueda", onClick = onOpen)
        }
    }
}

@Composable
private fun AdoptionFeedCard(
    post: LocalSocialPost,
    profile: PostLook,
    media: List<LocalPostMedia>,
    listing: LocalAnimalListing?,
    onOpen: () -> Unit,
    onOpenAuthor: () -> Unit,
    isAuthor: Boolean,
    onAdopt: () -> Unit,
    onEdit: () -> Unit,
    onClap: () -> Unit,
) {
    val copy = LocalOgtCopy.current
    var thanks by remember(post.id) { mutableStateOf(post.impactCount) }
    var clapped by remember(post.id) { mutableStateOf(false) }
    FeedArticle(onOpen = onOpen) {
        PostHeader(
            name = profile.fullName,
            place = post.place,
            time = post.timeLabel,
            avatar = profile.avatar,
            verified = profile.verified,
            onOpenAuthor = onOpen,
        )
        Column(
            Modifier.padding(horizontal = 16.dp).padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OgtStitchIcon(Res.drawable.qs_paw, copy.petsAdoption, tint = OgtColors.secondary)
                OgtPill(copy.petsAdoption, OgtColors.sand, OgtColors.ink)
            }
            ListingPhotoStrip(media = media, fallback = profile.photo, onOpen = onOpen, height = 168.dp)
            PostActionRow(
                clapped = clapped,
                thanks = thanks,
                comments = post.commentCount,
                onClap = {
                    clapped = !clapped
                    thanks += if (clapped) 1 else -1
                    if (clapped) onClap()
                },
                onComments = onOpen,
            )
            Text(listing?.displayPetName()?.ifBlank { null } ?: post.tag, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = OgtColors.ink)
            listing?.let { row ->
                val bits = listOfNotNull(
                    row.ageLabel.takeIf { it.isNotBlank() },
                    when (row.sex) { "HEMBRA" -> "Hembra"; "MACHO" -> "Macho"; else -> null },
                    row.temperament.takeIf { it.isNotBlank() },
                )
                if (bits.isNotEmpty()) OgtCaption(bits.joinToString(" · "))
                if (row.vaccinated || row.sterilized) {
                    OgtCaption(
                        listOfNotNull(
                            if (row.vaccinated) "Vacunada" else null,
                            if (row.sterilized) "Castrada" else null,
                        ).joinToString(" · "),
                    )
                }
            }
            OgtCaption(listing?.description ?: post.body)
            listing?.homeNeeds?.takeIf { it.isNotBlank() }?.let { OgtCaption("Hogar · $it") }
            if (!post.place.isBlank()) OgtCaption(post.place)
            if (isAuthor) {
                OgtPrimaryButton(copy.petsEdit, onClick = onEdit)
            } else {
                OgtPrimaryButton(copy.petsWantAdopt, onClick = onOpen)
            }
        }
    }
}

@Composable
private fun TernuraFeedCard(
    post: LocalSocialPost,
    profile: PostLook,
    media: List<LocalPostMedia>,
    onOpen: () -> Unit,
    onClap: () -> Unit,
) {
    val copy = LocalOgtCopy.current
    val db = LocalOgtDb.current
    var thanks by remember(post.id) { mutableStateOf(post.impactCount) }
    var clapped by remember(post.id) { mutableStateOf(false) }
    val credit = db.creditName(post)
    FeedArticle(onOpen = onOpen) {
        PostHeader(
            name = profile.fullName,
            place = post.place,
            time = post.timeLabel,
            avatar = profile.avatar,
            verified = profile.verified,
            onOpenAuthor = onOpen,
        )
        Column(
            Modifier.padding(horizontal = 16.dp).padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OgtStitchIcon(Res.drawable.qs_pets, copy.feedTitleTernura, tint = OgtColors.secondary)
                OgtPill(post.tag, OgtColors.sand, OgtColors.secondary)
            }
            ListingPhotoStrip(media = media, fallback = profile.photo, onOpen = onOpen, height = 188.dp)
            PostActionRow(
                clapped = clapped,
                thanks = thanks,
                comments = post.commentCount,
                onClap = {
                    clapped = !clapped
                    thanks += if (clapped) 1 else -1
                    if (clapped) onClap()
                },
                onComments = onOpen,
            )
            Text(post.body, fontSize = 14.sp, lineHeight = 20.sp, color = OgtColors.ink)
            if (credit != profile.fullName || db.creditPending(post)) {
                OgtCaption(
                    if (db.creditPending(post)) "La historia es de $credit · Invitada" else "La historia es de $credit",
                )
            }
        }
    }
}

@Composable
private fun HomenajeFeedCard(
    post: LocalSocialPost,
    profile: PostLook,
    media: List<LocalPostMedia>,
    onOpen: () -> Unit,
    onClap: () -> Unit,
    onHeart: () -> Unit,
) {
    val copy = LocalOgtCopy.current
    val db = LocalOgtDb.current
    var thanks by remember(post.id) { mutableStateOf(post.impactCount) }
    var clapped by remember(post.id) { mutableStateOf(false) }
    var hearts by remember(post.id) { mutableStateOf(post.heartCount) }
    var hearted by remember(post.id) { mutableStateOf(false) }
    val honoree = db.creditName(post)
    FeedArticle(onOpen = onOpen) {
        PostHeader(
            name = profile.fullName,
            place = post.place,
            time = post.timeLabel,
            avatar = profile.avatar,
            verified = profile.verified,
            onOpenAuthor = onOpen,
        )
        Column(
            Modifier.padding(horizontal = 16.dp).padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OgtStitchIcon(Res.drawable.qs_invite, copy.feedTitleHomenaje, tint = OgtColors.secondary)
                OgtPill(post.tag, OgtColors.sand, OgtColors.ink)
            }
            Text(honoree, fontWeight = FontWeight.Bold, fontSize = 18.sp, color = OgtColors.ink)
            OgtCaption("Lo cuenta ${profile.fullName}")
            if (db.creditPending(post)) {
                OgtCaption("La familia puede reivindicar este crédito.")
            }
            ListingPhotoStrip(media = media, fallback = profile.photo, onOpen = onOpen, height = 168.dp)
            PostActionRow(
                clapped = clapped,
                thanks = thanks,
                comments = post.commentCount,
                onClap = {
                    clapped = !clapped
                    thanks += if (clapped) 1 else -1
                    if (clapped) onClap()
                },
                hearted = hearted,
                hearts = hearts,
                onHeart = {
                    hearted = !hearted
                    hearts += if (hearted) 1 else -1
                    if (hearted) onHeart()
                },
                onComments = onOpen,
            )
            Text(post.body, fontSize = 14.sp, lineHeight = 20.sp, color = OgtColors.ink)
        }
    }
}

@Composable
private fun ListingPhotoStrip(
    media: List<LocalPostMedia>,
    fallback: DrawableResource,
    onOpen: () -> Unit,
    height: androidx.compose.ui.unit.Dp,
) {
    val pageCount = media.size.coerceAtLeast(1)
    val pager = rememberPagerState { pageCount }
    Box(
        Modifier
            .fillMaxWidth()
            .height(height)
            .clip(RoundedCornerShape(16.dp)),
    ) {
        HorizontalPager(state = pager, modifier = Modifier.fillMaxSize(), key = { media.getOrNull(it)?.id ?: it }) { page ->
            val item = media.getOrNull(page)
            Box(Modifier.fillMaxSize().clickable(onClick = onOpen)) {
                if (item != null) {
                    OgtPostImage(
                        item = item,
                        fallback = fallback,
                        contentDescription = item.altText,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    Image(
                        painterResource(fallback),
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
                    )
                }
                if (item?.kind == MediaKind.VIDEO) {
                    Box(
                        Modifier.align(Alignment.Center).size(56.dp).clip(CircleShape).background(Color(0xB3000000)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("▶", color = Color.White, fontSize = 22.sp)
                    }
                }
            }
        }
        MediaPagerIndicator(
            pageIndex = pager.currentPage,
            pageCount = media.size,
        )
    }
}

@Composable
private fun StoryFeedCard(
    post: LocalSocialPost,
    profile: PostLook,
    media: List<LocalPostMedia>,
    kind: FeedCardKind,
    discovery: Boolean,
    comments: List<LocalComment>,
    onOpen: () -> Unit,
    onOpenAuthor: () -> Unit,
    onInvite: () -> Unit,
    onClap: () -> Unit,
) {
    var thanks by remember(post.id) { mutableStateOf(post.impactCount) }
    var clapped by remember(post.id) { mutableStateOf(false) }
    val petStory = kind == FeedCardKind.PET_STORY
    FeedArticle(onOpen = onOpen) {
        PostHeader(
            name = profile.fullName,
            place = post.place,
            time = post.timeLabel,
            avatar = profile.avatar,
            verified = profile.verified,
            onOpenAuthor = onOpen,
        )
        PostMediaPager(
            media = media,
            fallback = profile.photo,
            tag = post.tag,
            discovery = discovery,
            petStory = petStory,
            onOpen = onOpen,
        )
        PostActionRow(
            clapped = clapped,
            thanks = thanks,
            comments = post.commentCount,
            onClap = {
                clapped = !clapped
                thanks += if (clapped) 1 else -1
                if (clapped) onClap()
            },
            onComments = onOpen,
        )
        Column(Modifier.padding(horizontal = 16.dp, vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            val whenWhere = if (isGatheringPost(post.tag)) {
                gatheringWhenWhere(post.place, post.eventStartsAtEpochMs, currentEpochMs())
            } else {
                null
            }
            if (whenWhere != null) {
                Text(
                    whenWhere,
                    color = OgtColors.ink,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            Text(
                "${profile.fullName}  ${post.body}",
                fontSize = 14.sp,
                lineHeight = 20.sp,
                color = OgtColors.ink,
            )
            val credit = LocalOgtDb.current.creditName(post)
            val author = LocalOgtDb.current.authorName(post)
            if (credit != author || LocalOgtDb.current.creditPending(post)) {
                OgtCaption(
                    if (LocalOgtDb.current.creditPending(post)) "Lo hizo $credit · Invitada" else "Lo hizo $credit",
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                profile.hashtags.forEach { tag ->
                    Text(tag, color = OgtColors.primary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }
            }
            if (!post.sourceUrl.isNullOrBlank()) {
                Text("Resumen de noticia real · fuente en el detalle", color = OgtColors.muted, fontSize = 11.sp)
            }
        }
        if (comments.isNotEmpty()) {
            Column(
                Modifier
                    .padding(horizontal = 16.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0xFFF6F2F7))
                    .padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                comments.take(2).forEach { comment ->
                    val avatar = commentAvatar(comment.authorUserId)
                    val name = LocalOgtDb.current.userOrNull(comment.authorUserId)?.displayName ?: "Alguien de la comunidad"
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        CirclePhoto(avatar, 24.dp)
                        Text(
                            name,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 13.sp,
                            color = OgtColors.ink,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false),
                        )
                        Text(comment.timeLabel, color = OgtColors.muted, fontSize = 11.sp, maxLines = 1)
                        Text(
                            comment.body,
                            fontSize = 13.sp,
                            color = OgtColors.ink,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }
        if (canRsvpToGathering(post.tag, post.eventStartsAtEpochMs, currentEpochMs())) {
            val db = LocalOgtDb.current
            val me = LocalOgtSession.current.me()
            var going by remember(post.id) { mutableStateOf(false) }
            var told by remember(post.id) { mutableStateOf(0) }
            val whenWhere = gatheringWhenWhere(post.place, post.eventStartsAtEpochMs, currentEpochMs())
            Row(
                Modifier.fillMaxWidth().background(OgtColors.sand).padding(horizontal = 16.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    when {
                        going -> "Vas · ${whenWhere ?: post.place}"
                        whenWhere != null -> whenWhere
                        else -> "¿Vas a sumarte?"
                    },
                    color = OgtColors.ink,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f).padding(end = 8.dp),
                    maxLines = 2,
                )
                Text(
                    if (going) "Avisamos a $told →" else "Asistiré →",
                    color = OgtColors.secondary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    modifier = Modifier.clickable {
                        if (!going) {
                            told = db.notifyContactsImGoing(me, post)
                            going = true
                        }
                    },
                )
            }
        }
        if (profile.postId == OgtIds.PostArboles) {
            Row(
                Modifier.fillMaxWidth().background(Color(0x66EAE7EB)).padding(horizontal = 16.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("¿Quieres convocar en tu zona?", color = OgtColors.muted, fontSize = 12.sp)
                Text("Invitar amigos →", color = OgtColors.primary, fontWeight = FontWeight.Bold, fontSize = 12.sp, modifier = Modifier.clickable(onClick = onInvite))
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}

private fun commentAvatar(userId: String): DrawableResource = when (userId) {
    OgtIds.Roberto -> Res.drawable.feed_avatar_roberto
    OgtIds.Sofia, OgtIds.Camila -> Res.drawable.feed_avatar_sofia
    OgtIds.CarlosG, OgtIds.CarlosR, OgtIds.DiegoF -> Res.drawable.feed_avatar_carlos
    OgtIds.Mariana, OgtIds.Lucia, OgtIds.MarianaD -> Res.drawable.feed_avatar_mariana
    OgtIds.Lucas -> Res.drawable.feed_avatar_me
    else -> Res.drawable.feed_avatar_reply
}

@Composable
private fun FeedArticle(
    onOpen: () -> Unit,
    content: @Composable () -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .shadow(10.dp, RoundedCornerShape(16.dp), ambientColor = Color(0x0D09090B))
            .clip(RoundedCornerShape(16.dp))
            .background(Color.White)
            .clickable(onClick = onOpen),
    ) { content() }
}

@Composable
private fun PostHeader(
    name: String,
    place: String,
    time: String,
    avatar: DrawableResource,
    verified: Boolean,
    star: Boolean = false,
    onOpenAuthor: () -> Unit = {},
) {
    Row(
        Modifier.fillMaxWidth().padding(16.dp).clickable(onClick = onOpenAuthor),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(44.dp).clip(CircleShape).background(
                Brush.linearGradient(listOf(OgtColors.hairline, OgtColors.hairline)),
            ),
            contentAlignment = Alignment.Center,
        ) {
            CirclePhoto(avatar, 40.dp)
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(name, fontWeight = FontWeight.Bold, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (verified) Text("✓", color = OgtColors.primary, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                if (star) Text("★", color = OgtColors.secondary, fontSize = 13.sp)
            }
            Text("$place  •  $time", color = OgtColors.muted, fontSize = 11.sp)
        }
        Text("⋯", color = OgtColors.muted, fontSize = 20.sp, modifier = Modifier.padding(8.dp))
    }
}

@Composable
private fun PostMediaPager(
    media: List<LocalPostMedia>,
    fallback: DrawableResource,
    tag: String,
    discovery: Boolean,
    petStory: Boolean = false,
    onOpen: () -> Unit,
) {
    val items = media.ifEmpty { emptyList() }
    val pageCount = items.size.coerceAtLeast(1)
    val pagerState = rememberPagerState { pageCount }
    Box(Modifier.fillMaxWidth().aspectRatio(4f / 3f)) {
        HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize(), key = { items.getOrNull(it)?.id ?: it }) { page ->
            val item = items.getOrNull(page)
            Box(Modifier.fillMaxSize().clickable(onClick = onOpen)) {
                if (item != null) {
                    OgtPostImage(
                        item = item,
                        fallback = fallback,
                        contentDescription = item.altText ?: tag,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    Image(
                        painterResource(fallback),
                        contentDescription = tag,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
                    )
                }
                if (item?.kind == MediaKind.VIDEO) {
                    Box(
                        Modifier.align(Alignment.Center).size(56.dp).clip(CircleShape).background(Color(0xB3000000)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("▶", color = Color.White, fontSize = 22.sp)
                    }
                }
            }
        }
        OverlayChip(
            Modifier.align(Alignment.TopStart).padding(12.dp),
            tag,
            if (petStory) OgtColors.secondary else OgtColors.ink,
        )
        if (discovery) {
            OverlayChip(
                Modifier.align(if (items.size > 1) Alignment.BottomStart else Alignment.TopEnd).padding(12.dp),
                "Para vos",
                OgtColors.secondary,
            )
        }
        MediaPagerIndicator(
            pageIndex = pagerState.currentPage,
            pageCount = items.size,
        )
    }
}

@Composable
private fun OverlayChip(
    modifier: Modifier,
    text: String,
    ink: Color,
    fill: Color = Color.White.copy(alpha = 0.92f),
) {
    Box(modifier.clip(CircleShape).background(fill).padding(horizontal = 10.dp, vertical = 6.dp)) {
        Text(text, color = ink, fontSize = 11.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun CirclePhoto(image: DrawableResource, size: androidx.compose.ui.unit.Dp) {
    androidx.compose.foundation.Image(
        painterResource(image),
        contentDescription = null,
        modifier = Modifier.size(size).clip(CircleShape),
        contentScale = ContentScale.Crop,
    )
}
