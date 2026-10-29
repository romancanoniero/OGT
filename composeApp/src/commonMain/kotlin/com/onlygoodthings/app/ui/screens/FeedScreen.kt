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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import com.onlygoodthings.app.data.LocalAuth
import com.onlygoodthings.app.data.LocalOgtDb
import com.onlygoodthings.app.data.LocalOgtSession
import com.onlygoodthings.app.data.backgroundFeedEvent
import com.onlygoodthings.app.data.optimisticClapPost
import com.onlygoodthings.app.data.optimisticDeletePost
import com.onlygoodthings.app.data.optimisticHeartPost
import com.onlygoodthings.app.data.optimisticHidePost
import com.onlygoodthings.app.data.optimisticReportPost
import com.onlygoodthings.app.media.OgtMediaCache
import com.onlygoodthings.app.resources.Res
import com.onlygoodthings.app.resources.avatar_anonymous
import com.onlygoodthings.app.resources.feed_avatar_me
import com.onlygoodthings.app.resources.feed_logo
import com.onlygoodthings.app.resources.feed_story_donacion
import com.onlygoodthings.app.resources.feed_story_me
import com.onlygoodthings.app.resources.qs_feed
import com.onlygoodthings.app.resources.qs_notifications
import com.onlygoodthings.app.theme.OgtColors
import com.onlygoodthings.app.i18n.LocalOgtCopy
import com.onlygoodthings.app.resources.qs_anecdote
import com.onlygoodthings.app.resources.qs_invite
import com.onlygoodthings.app.resources.qs_paw
import com.onlygoodthings.app.resources.qs_pets
import com.onlygoodthings.app.resources.qs_search
import com.onlygoodthings.app.ui.components.CircleIconButton
import com.onlygoodthings.app.ui.components.MediaPagerIndicator
import com.onlygoodthings.app.ui.components.OgtTopBar
import com.onlygoodthings.app.ui.components.OgtCaption
import com.onlygoodthings.app.ui.components.OgtPill
import com.onlygoodthings.app.ui.components.LostAlertMap
import com.onlygoodthings.app.ui.components.OgtPrimaryButton
import com.onlygoodthings.app.ui.components.OgtStitchIcon
import com.onlygoodthings.app.ui.components.neighborAvatarArt
import com.onlygoodthings.app.ui.components.PostActionRow
import com.onlygoodthings.app.ui.components.PostOverflowSheet
import com.onlygoodthings.app.ui.components.PostReportSheet
import com.onlygoodthings.app.ui.components.PostShareSheet
import com.onlygoodthings.shared.data.local.LocalAnimalListing
import com.onlygoodthings.shared.data.local.LocalComment
import com.onlygoodthings.shared.data.local.LocalPostMedia
import com.onlygoodthings.shared.domain.POST_FRAME_RATIO
import com.onlygoodthings.shared.domain.PostPlacement
import com.onlygoodthings.shared.data.local.LocalSocialPost
import com.onlygoodthings.shared.domain.AuthorKind
import com.onlygoodthings.shared.domain.FeedCardKind
import com.onlygoodthings.shared.domain.FeedEventKind
import com.onlygoodthings.shared.domain.FeedMode
import com.onlygoodthings.shared.domain.FeedTopicFamily
import com.onlygoodthings.shared.domain.MediaKind
import com.onlygoodthings.shared.domain.anecdoteShareText
import com.onlygoodthings.shared.domain.canEditSocialPost
import com.onlygoodthings.shared.domain.feedCardKind
import com.onlygoodthings.shared.domain.canRsvpToGathering
import com.onlygoodthings.shared.domain.gatheringWhenWhere
import com.onlygoodthings.shared.domain.isGatheringPost
import com.onlygoodthings.shared.domain.isHomenajePost
import com.onlygoodthings.shared.domain.postMetaLine
import com.onlygoodthings.shared.domain.postShareText
import com.onlygoodthings.shared.data.local.anecdoteParentId
import com.onlygoodthings.shared.data.local.isAnecdoteShare
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
    onEditPost: (String) -> Unit = {},
    onOpenRules: () -> Unit = {},
) {
    val db = LocalOgtDb.current
    val auth = LocalAuth.current
    val session = LocalOgtSession.current
    val me = session.me()
    val lostTick = db.lostEpoch
    val socialTick by db.socialTick.collectAsState()
    val feedTick by db.feedTick.collectAsState()
    val followed = remember { followedNeighbors() }
    var feedMode by remember { mutableStateOf(FeedMode.HOME) }
    var feedFamily by remember { mutableStateOf<FeedTopicFamily?>(null) }
    val copy = LocalOgtCopy.current
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    var page by remember { mutableStateOf(1) }
    var refreshTick by remember { mutableStateOf(0) }
    var refreshing by remember { mutableStateOf(false) }
    var pullPx by remember { mutableStateOf(0f) }
    var initialSyncComplete by remember(me.id) { mutableStateOf(false) }
    var feedPosts by remember(me.id) { mutableStateOf(emptyList<LocalSocialPost>()) }
    var lastRemoteOrder by remember { mutableStateOf<List<String>?>(null) }
    var incomingToast by remember { mutableStateOf(false) }
    var suppressIncomingToast by remember { mutableStateOf(false) }
    val seenPostIds = remember { mutableStateListOf<String>() }
    fun paintRiver(remoteOrder: List<String>? = lastRemoteOrder) {
        // La caché aún no validada no debe aparecer por un instante al iniciar.
        if (!initialSyncComplete) return
        feedPosts = db.composeVisibleRiver(me.id, feedMode, feedFamily, remoteOrder)
    }
    LaunchedEffect(feedMode, feedFamily) { page = 1 }
    LaunchedEffect(me.id, feedMode, feedFamily, feedTick, socialTick) {
        paintRiver()
    }
    LaunchedEffect(me.id, feedMode, feedFamily, refreshTick) {
        paintRiver()
        auth.ensureDevBearer()
        val remote = try {
            val serverId = Regex("[0-9a-fA-F]{8}(-[0-9a-fA-F]{4}){3}-[0-9a-fA-F]{12}")
            val cachedIds = db.feedPosts().map { it.id }.filter { serverId.matches(it) }
            cachedIds.chunked(100).forEach { ids ->
                // A failed check leaves the offline cache intact; never prune by page membership.
                try {
                    val visible = auth.feed.visiblePostIds(ids)
                    session.reconcileFeedVisibility(ids.toSet(), visible.toSet())
                } catch (cancel: CancellationException) {
                    throw cancel
                } catch (_: Exception) {
                    // Keep feed loading compatible with servers that predate cache validation.
                }
            }
            auth.feed.loadFeed(cursor = "0", pageSize = 40, mode = feedMode, family = feedFamily)
        } catch (cancel: CancellationException) {
            throw cancel
        } catch (_: Exception) {
            null
        }
        if (remote != null) {
            remote.forEach { db.upsertRemoteSocial(it, silent = true) }
            lastRemoteOrder = remote.map { it.id }
            session.persistSocialFeed()
        }
        // También libera la caché si falla la red: mantiene el uso sin conexión.
        initialSyncComplete = true
        paintRiver(lastRemoteOrder)
        if (remote != null) {
            val mediaUrls = remote.flatMap { post ->
                post.media.flatMap { listOfNotNull(it.url, it.posterUrl) } + post.mediaUrls
            }
            runCatching { OgtMediaCache.prefetch(mediaUrls) }
        }
        if (seenPostIds.isEmpty()) seenPostIds.addAll(db.feedPosts().map { it.id })
    }
    LaunchedEffect(feedTick) {
        session.persistSocialFeed()
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
    var sharePostId by remember { mutableStateOf<String?>(null) }
    var overflowPostId by remember { mutableStateOf<String?>(null) }
    var reportPostId by remember { mutableStateOf<String?>(null) }
    OgtTopBar(
        title = "",
        onNotifications = onNotifications,
        onProfile = onProfile,
        onMessages = onMessages,
        branded = true,
    )
    Box(Modifier.fillMaxSize().background(OgtColors.canvas)) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize().nestedScroll(pull),
            verticalArrangement = Arrangement.spacedBy(0.dp),
        ) {
            item(key = "chrome-stories") {
                StoriesRail()
            }
            item(key = "chrome-mode") {
                FeedModeRow(mode = feedMode, onMode = { feedMode = it }, copy = copy)
            }
            item(key = "chrome-family") {
                FeedFamilyRow(family = feedFamily, onFamily = { feedFamily = it }, copy = copy)
            }
            if (!initialSyncComplete || refreshing || pullPx > 16f) {
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
                val media = remember(post.id, socialTick) { db.mediaOf(post.id) }
                val kind = feedCardKind(post.tag, listing?.kind, post.sourceUrl)
                NeighborPostCard(
                    post = post.copy(commentCount = maxOf(post.commentCount, comments.size)),
                    profile = look,
                    media = media,
                    listing = listing,
                    kind = kind,
                    discovery = db.isDiscovery(me.id, post),
                    comments = comments,
                    onOpen = {
                        db.recordFeedEvent(me.id, post.id, FeedEventKind.IMPRESSION)
                        backgroundFeedEvent(auth.feed, scope, post.id, FeedEventKind.IMPRESSION)
                        onOpenPost(db.openFeedTarget(post))
                    },
                    onOpenAuthor = {
                        val authorId = post.authorUserId
                        if (authorId != null) {
                            db.recordFeedEvent(me.id, post.id, FeedEventKind.PROFILE_TAP)
                            backgroundFeedEvent(auth.feed, scope, post.id, FeedEventKind.PROFILE_TAP)
                            onOpenNeighbor(authorId)
                        }
                    },
                    onInvite = onInvite,
                    onShare = { sharePostId = post.id },
                    onOverflow = { overflowPostId = post.id },
                    onClap = {
                        optimisticClapPost(db, auth.feed, scope, me.id, post.id) { session.persistSocialFeed() }
                    },
                    onHeart = {
                        optimisticHeartPost(db, auth.feed, scope, me.id, post.id)
                    },
                    onPetsMap = onPetsMap,
                    onAdopt = onAdopt,
                    onEditAdoption = { onEditPost(post.id) },
                    isAdoptionAuthor = me.owns(post.authorUserId) || me.owns(listing?.reporterUserId),
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
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
        sharePostId?.let { postId ->
            val post = remember(postId, socialTick) { db.post(postId) } ?: return@let
            val listing = remember(postId, lostTick) { post.listingId?.let { id -> db.animals.firstOrNull { it.id == id } } }
            val honorLink = post.anecdoteParentId() ?: post.id
            PostShareSheet(
                shareText = if (post.isAnecdoteShare()) {
                    anecdoteShareText(db.creditName(post), post.body, honorLink)
                } else {
                    postShareText(
                        headline = listing?.title?.takeIf { it.isNotBlank() }
                            ?: db.creditName(post).takeIf { isHomenajePost(post.tag) }
                            ?: post.tag,
                        body = listing?.description?.takeIf { it.isNotBlank() } ?: post.body.takeIf { it.isNotBlank() },
                        place = gatheringWhenWhere(post.place, post.eventStartsAtEpochMs, currentEpochMs())
                            ?: post.place.takeIf { it.isNotBlank() },
                        postId = post.id,
                    )
                },
                onDismiss = { sharePostId = null },
                onRepost = {
                    db.recordFeedEvent(me.id, post.id, FeedEventKind.SHARE)
                    backgroundFeedEvent(auth.feed, scope, post.id, FeedEventKind.SHARE)
                },
                onInvite = {
                    db.recordFeedEvent(me.id, post.id, FeedEventKind.SHARE)
                    backgroundFeedEvent(auth.feed, scope, post.id, FeedEventKind.SHARE)
                    onInvite()
                },
                onExternal = {
                    db.recordFeedEvent(me.id, post.id, FeedEventKind.SHARE)
                    backgroundFeedEvent(auth.feed, scope, post.id, FeedEventKind.SHARE)
                },
            )
        }
        reportPostId?.let { postId ->
            val post = remember(postId, socialTick) { db.posts.firstOrNull { it.id == postId } }
            val listing = post?.listingId?.let { id -> db.animals.firstOrNull { it.id == id } }
            PostReportSheet(
                kind = post?.let { feedCardKind(it.tag, listing?.kind, it.sourceUrl) },
                onDismiss = { reportPostId = null },
                onSubmit = { reason, details ->
                    optimisticReportPost(db, auth.feed, scope, me.id, postId, reason, details) {
                        session.persistSocialFeed()
                        paintRiver()
                    }
                },
                onOpenRules = onOpenRules,
            )
        }
        overflowPostId?.let { postId ->
            val post = remember(postId, socialTick) { db.posts.firstOrNull { it.id == postId } } ?: return@let
            val listing = post.listingId?.let { id -> db.animals.firstOrNull { it.id == id } }
            val mine = db.isOwnPost(post, me.aliases())
            PostOverflowSheet(
                isAuthor = mine,
                blockUserId=post.authorUserId,
                canEdit = canEditSocialPost(mine, post.authorKind, post.tag, listing?.kind, post.sourceUrl),
                onDismiss = { overflowPostId = null },
                onHide = {
                    optimisticHidePost(db, auth.feed, scope, me.id, post.id) {
                        session.persistSocialFeed()
                        paintRiver()
                    }
                },
                onReport = { reportPostId = post.id },
                onEdit = { onEditPost(post.id) },
                onDelete = {
                    optimisticDeletePost(db, auth.feed, scope, me.id, post.id, persistFeed = {
                        session.persistSocialFeed()
                        paintRiver()
                    })
                },
            )
        }

    }
}

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

private fun followedNeighbors(): List<FollowedNeighbor> = emptyList()

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
    onShare: () -> Unit,
    onOverflow: () -> Unit = {},
    onClap: () -> Unit,
    onHeart: () -> Unit = {},
    onPetsMap: () -> Unit,
    onAdopt: () -> Unit,
    onEditAdoption: () -> Unit,
    isAdoptionAuthor: Boolean,
    modifier: Modifier = Modifier,
) {
    Column(modifier) {
    when (kind) {
        FeedCardKind.LOST_PET -> LostPetFeedCard(
            post = post,
            profile = profile,
            media = media,
            listing = listing,
            sightings = listing?.let { LocalOgtDb.current.sightingsOf(it.id) }.orEmpty(),
            onOpen = onOpen,
            onOpenAuthor = onOpenAuthor,
            onShare = onShare,
            onOverflow = onOverflow,
            onClap = onClap,
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
            onShare = onShare,
            onOverflow = onOverflow,
            onClap = onClap,
        )
        FeedCardKind.TERNURA -> TernuraFeedCard(
            post = post,
            profile = profile,
            media = media,
            onOpen = onOpen,
            onShare = onShare,
            onOverflow = onOverflow,
            onClap = onClap,
        )
        FeedCardKind.HOMENAJE -> HomenajeFeedCard(
            post = post,
            profile = profile,
            media = media,
            onOpen = onOpen,
            onShare = onShare,
            onOverflow = onOverflow,
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
            onShare = onShare,
            onOverflow = onOverflow,
            onClap = onClap,
        )
    }
    com.onlygoodthings.app.ui.components.AttributionLink(post.attributionUrl)
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
    onShare: () -> Unit,
    onOverflow: () -> Unit = {},
    onClap: () -> Unit,
) {
    val copy = LocalOgtCopy.current
    val session = LocalOgtSession.current
    var thanks by remember(post.id, post.impactCount) { mutableStateOf(post.impactCount) }
    var clapped by remember(post.id, post.viewerHasImpacted) { mutableStateOf(post.viewerHasImpacted) }
    val radius = listing?.alertRadiusM ?: 2000
    val lastSeen = listing?.lastSeenPlace?.ifBlank { null } ?: listing?.place?.ifBlank { post.place } ?: post.place
    val resolved = listing?.resolved == true
    FeedArticle(onOpen = onOpen) {
        PostHeader(
            name = profile.fullName,
            place = post.place,
            time = post.timeLabel,
            createdAtEpochMs = post.createdAtEpochMs,
            avatar = profile.avatar,
            authorUserId = post.authorUserId,
            verified = profile.verified,
            onOpenAuthor = onOpenAuthor,
            onOverflow = onOverflow,
        )
        Column(
            Modifier.padding(horizontal = 12.dp).padding(bottom = 10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OgtStitchIcon(Res.drawable.qs_search, copy.petsLost, tint = OgtColors.secondary)
                OgtPill(if (resolved) "Ya está en casa" else copy.petsLost, OgtColors.sunset, OgtColors.sunsetText)
                ImpactTruthPills(post)
            }
            OgtCaption("Última vista · $lastSeen · ${radius} m")
            listing?.marks?.takeIf { it.isNotBlank() }?.let { OgtCaption(it) }
            ListingPhotoStrip(media = media, fallback = profile.photo, onOpen = onOpen)
            PostActionRow(
                clapped = clapped,
                thanks = thanks,
                comments = post.commentCount,
                onClap = {
                    if (clapped) return@PostActionRow
                    clapped = true
                    thanks += 1
                    onClap()
                },
                onComments = onOpen,
                onShare = onShare,
            )
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
    onShare: () -> Unit,
    onOverflow: () -> Unit = {},
    onClap: () -> Unit,
) {
    val copy = LocalOgtCopy.current
    var thanks by remember(post.id, post.impactCount) { mutableStateOf(post.impactCount) }
    var clapped by remember(post.id, post.viewerHasImpacted) { mutableStateOf(post.viewerHasImpacted) }
    FeedArticle(onOpen = onOpen) {
        PostHeader(
            name = profile.fullName,
            place = post.place,
            time = post.timeLabel,
            createdAtEpochMs = post.createdAtEpochMs,
            avatar = profile.avatar,
            authorUserId = post.authorUserId,
            verified = profile.verified,
            onOpenAuthor = onOpenAuthor,
            onOverflow = onOverflow,
        )
        Column(
            Modifier.padding(horizontal = 12.dp).padding(bottom = 10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OgtStitchIcon(Res.drawable.qs_paw, copy.petsAdoption, tint = OgtColors.secondary)
                OgtPill(copy.petsAdoption, OgtColors.sand, OgtColors.ink)
                ImpactTruthPills(post)
            }
            ListingPhotoStrip(media = media, fallback = profile.photo, onOpen = onOpen)
            PostActionRow(
                clapped = clapped,
                thanks = thanks,
                comments = post.commentCount,
                onClap = {
                    if (clapped) return@PostActionRow
                    clapped = true
                    thanks += 1
                    onClap()
                },
                onComments = onOpen,
                onShare = onShare,
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
    onShare: () -> Unit,
    onOverflow: () -> Unit = {},
    onClap: () -> Unit,
) {
    val copy = LocalOgtCopy.current
    val db = LocalOgtDb.current
    var thanks by remember(post.id, post.impactCount) { mutableStateOf(post.impactCount) }
    var clapped by remember(post.id, post.viewerHasImpacted) { mutableStateOf(post.viewerHasImpacted) }
    val credit = db.creditName(post)
    FeedArticle(onOpen = onOpen) {
        PostHeader(
            name = profile.fullName,
            place = post.place,
            time = post.timeLabel,
            createdAtEpochMs = post.createdAtEpochMs,
            avatar = profile.avatar,
            authorUserId = post.authorUserId,
            verified = profile.verified,
            onOpenAuthor = onOpen,
            onOverflow = onOverflow,
        )
        Column(
            Modifier.padding(horizontal = 12.dp).padding(bottom = 10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OgtStitchIcon(Res.drawable.qs_pets, copy.feedTitleTernura, tint = OgtColors.secondary)
                OgtPill(post.tag, OgtColors.sand, OgtColors.secondary)
            }
            ImpactTruthPills(post)
            ListingPhotoStrip(media = media, fallback = profile.photo, onOpen = onOpen)
            PostActionRow(
                clapped = clapped,
                thanks = thanks,
                comments = post.commentCount,
                onClap = {
                    if (clapped) return@PostActionRow
                    clapped = true
                    thanks += 1
                    onClap()
                },
                onComments = onOpen,
                onShare = onShare,
            )
            Text(post.body, fontSize = 14.sp, lineHeight = 20.sp, color = OgtColors.ink, maxLines = 3, overflow = TextOverflow.Ellipsis)
            if (credit != profile.fullName) {
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
    onShare: () -> Unit,
    onOverflow: () -> Unit = {},
    onClap: () -> Unit,
    onHeart: () -> Unit,
) {
    val copy = LocalOgtCopy.current
    val db = LocalOgtDb.current
    var thanks by remember(post.id, post.impactCount) { mutableStateOf(post.impactCount) }
    var clapped by remember(post.id, post.viewerHasImpacted) { mutableStateOf(post.viewerHasImpacted) }
    var hearts by remember(post.id, post.heartCount) { mutableStateOf(post.heartCount) }
    var hearted by remember(post.id, post.viewerHasHearted) { mutableStateOf(post.viewerHasHearted) }
    val honoree = db.creditName(post)
    val moment = post.isAnecdoteShare()
    FeedArticle(onOpen = onOpen) {
        PostHeader(
            name = profile.fullName,
            place = post.place,
            time = post.timeLabel,
            createdAtEpochMs = post.createdAtEpochMs,
            avatar = profile.avatar,
            authorUserId = post.authorUserId,
            verified = profile.verified,
            onOpenAuthor = onOpen,
            onOverflow = onOverflow,
        )
        Column(
            Modifier.padding(horizontal = 12.dp).padding(bottom = 10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OgtStitchIcon(
                    if (moment) Res.drawable.qs_anecdote else Res.drawable.qs_invite,
                    if (moment) "Anécdota" else copy.feedTitleHomenaje,
                    tint = OgtColors.secondary,
                )
                OgtPill(if (moment) "Anécdota" else post.tag, OgtColors.sand, OgtColors.ink)
            }
            if (!moment) ImpactTruthPills(post)
            if (moment || honoree != profile.fullName) {
                Text(
                    if (moment) "Anécdota del homenaje a $honoree" else honoree,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = OgtColors.ink,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (db.creditPending(post)) {
                OgtCaption("La familia puede reivindicar este crédito.")
            }
            ListingPhotoStrip(media = media, fallback = profile.photo, onOpen = onOpen)
            PostActionRow(
                clapped = clapped,
                thanks = thanks,
                comments = post.commentCount,
                onClap = {
                    if (clapped) return@PostActionRow
                    clapped = true
                    thanks += 1
                    onClap()
                },
                hearted = hearted,
                hearts = hearts,
                onHeart = {
                    if (hearted) return@PostActionRow
                    hearted = true
                    hearts += 1
                    onHeart()
                },
                onComments = onOpen,
                onShare = onShare,
            )
            Text(post.body, fontSize = 14.sp, lineHeight = 20.sp, color = OgtColors.ink, maxLines = 3, overflow = TextOverflow.Ellipsis)
            if (moment) {
                OgtCaption("Se abre el homenaje completo")
            } else {
                val stories = db.anecdotesOf(post.id)
                if (stories.isNotEmpty()) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        OgtStitchIcon(Res.drawable.qs_anecdote, "Anécdotas", size = 16.dp, tint = OgtColors.secondary)
                        OgtCaption("${stories.size} anécdotas · se leen en el detalle")
                    }
                }
            }
        }
    }
}

@Composable
private fun ListingPhotoStrip(
    media: List<LocalPostMedia>,
    fallback: DrawableResource,
    onOpen: () -> Unit,
) {
    val pageCount = media.size.coerceAtLeast(1)
    val pager = rememberPagerState { pageCount }
    // Marco 16:9 de la grilla. La foto lo cubre y se recorta.
    Box(
        Modifier
            .ogtBleedHorizontal(12.dp)
            .aspectRatio(POST_FRAME_RATIO),
    ) {
        HorizontalPager(state = pager, modifier = Modifier.fillMaxSize(), key = { pageIndex ->
            val item = media.getOrNull(pageIndex)
            if (item == null) pageIndex else "${item.id}:${item.url}"
        }) { page ->
            val item = media.getOrNull(page)
            Box(Modifier.fillMaxSize().clickable(onClick = onOpen)) {
                if (item != null) {
                    OgtPostImage(
                        item = item,
                        fallback = fallback,
                        contentDescription = item.altText,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
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
    onShare: () -> Unit,
    onOverflow: () -> Unit = {},
    onClap: () -> Unit,
) {
    var thanks by remember(post.id, post.impactCount) { mutableStateOf(post.impactCount) }
    var clapped by remember(post.id, post.viewerHasImpacted) { mutableStateOf(post.viewerHasImpacted) }
    val petStory = kind == FeedCardKind.PET_STORY
    FeedArticle(onOpen = onOpen) {
        PostHeader(
            name = profile.fullName,
            place = post.place,
            time = post.timeLabel,
            createdAtEpochMs = post.createdAtEpochMs,
            avatar = profile.avatar,
            authorUserId = post.authorUserId,
            verified = profile.verified,
            onOpenAuthor = onOpenAuthor,
            onOverflow = onOverflow,
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
                if (clapped) return@PostActionRow
                clapped = true
                thanks += 1
                onClap()
            },
            onComments = onOpen,
            onShare = onShare,
        )
        Column(Modifier.padding(horizontal = 12.dp, vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            ImpactTruthPills(post)
            val whenWhere = if (isGatheringPost(post.tag)) {
                gatheringWhenWhere(post.place, post.eventStartsAtEpochMs, currentEpochMs())
            } else {
                null
            }
            if (whenWhere != null) {
                Text(
                    whenWhere,
                    color = OgtColors.ink,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                post.body,
                fontSize = 14.sp,
                lineHeight = 20.sp,
                color = OgtColors.ink,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
            val credit = LocalOgtDb.current.creditName(post)
            val author = LocalOgtDb.current.authorName(post)
            if (credit != author) {
                OgtCaption(
                    if (LocalOgtDb.current.creditPending(post)) "Lo hizo $credit · Invitada" else "Lo hizo $credit",
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                profile.hashtags.take(2).forEach { tag ->
                    Text(tag, color = OgtColors.primary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
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
        Spacer(Modifier.height(8.dp))
    }
}

private fun commentAvatar(userId: String): DrawableResource = neighborAvatarArt(userId)

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
    createdAtEpochMs: Long,
    avatar: DrawableResource,
    authorUserId: String? = null,
    verified: Boolean,
    star: Boolean = false,
    onOpenAuthor: () -> Unit = {},
    onOverflow: () -> Unit = {},
) {
    val db = LocalOgtDb.current
    val me = LocalOgtSession.current.me()
    val mine = authorUserId != null && me.owns(authorUserId)
    val socialTick by db.socialTick.collectAsState()
    // La foto vive en el usuario. Quien está en sesión no usa un drawable aparte.
    val face = remember(authorUserId, socialTick) {
        authorUserId?.let { db.userOrNull(it)?.photoUrl }?.trim().orEmpty()
    }
    val meta = postMetaLine(place, time, createdAtEpochMs, currentEpochMs())
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp).clickable(onClick = onOpenAuthor),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (mine) {
            OgtSessionAvatar(
                contentDescription = name,
                modifier = Modifier.size(36.dp).clip(CircleShape),
            )
        } else if (face.isNotBlank()) {
            OgtNeighborAvatar(
                userId = authorUserId.orEmpty(),
                photoUrl = face,
                contentDescription = name,
                modifier = Modifier.size(36.dp).clip(CircleShape),
            )
        } else {
            CirclePhoto(avatar, 36.dp)
        }
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(name, fontWeight = FontWeight.Bold, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (verified) Text("✓", color = OgtColors.primary, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                if (star) Text("★", color = OgtColors.secondary, fontSize = 13.sp)
            }
            if (meta.isNotBlank()) {
                Text(meta, color = OgtColors.muted, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        Text(
            "⋯",
            color = OgtColors.muted,
            fontSize = 20.sp,
            modifier = Modifier
                .clip(CircleShape)
                .clickable(onClick = onOverflow)
                .padding(8.dp),
        )
    }
}

@Composable
private fun ImpactTruthPills(post: LocalSocialPost) {
    val pills = buildList {
        if (post.achievedCount > 0) add("Se logró")
        if (post.placement == PostPlacement.PROMOTED) add("Promocionado")
        if (post.placement == PostPlacement.AD) add("Publicidad")
        if (post.empresaQueSuma) add("Empresa que suma")
    }
    if (pills.isEmpty()) return
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        pills.forEach { OgtPill(it, OgtColors.sand, OgtColors.secondary) }
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
    // Miniatura del feed: la grilla es 16:9 y la foto se recorta a ese marco.
    Box(Modifier.fillMaxWidth().aspectRatio(POST_FRAME_RATIO)) {
        HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize(), key = { pageIndex ->
            val item = items.getOrNull(pageIndex)
            if (item == null) pageIndex else "${item.id}:${item.url}"
        }) { page ->
            val item = items.getOrNull(page)
            Box(Modifier.fillMaxSize().clickable(onClick = onOpen)) {
                if (item != null) {
                    OgtPostImage(
                        item = item,
                        fallback = fallback,
                        contentDescription = item.altText ?: tag,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
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
