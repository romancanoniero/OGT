package com.onlygoodthings.app.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.launch
import com.onlygoodthings.app.data.LocalAuth
import com.onlygoodthings.app.data.LocalOgtDb
import com.onlygoodthings.app.data.LocalOgtSession
import com.onlygoodthings.app.data.optimisticClapPost
import com.onlygoodthings.app.data.optimisticDeletePost
import com.onlygoodthings.app.data.optimisticHeartPost
import com.onlygoodthings.app.data.optimisticHidePost
import com.onlygoodthings.app.data.optimisticReportPost
import com.onlygoodthings.app.data.optimisticThreadComment
import com.onlygoodthings.app.i18n.LocalOgtCopy
import com.onlygoodthings.app.ui.components.PostOverflowSheet
import com.onlygoodthings.app.ui.components.PostReportSheet
import com.onlygoodthings.app.ui.components.PostShareSheet
import com.onlygoodthings.app.resources.Res
import com.onlygoodthings.app.resources.feed_avatar_carlos
import com.onlygoodthings.app.resources.feed_avatar_mariana
import com.onlygoodthings.app.resources.feed_avatar_me
import com.onlygoodthings.app.resources.feed_avatar_reply
import com.onlygoodthings.app.resources.feed_avatar_roberto
import com.onlygoodthings.app.resources.feed_avatar_sofia
import com.onlygoodthings.app.resources.qs_clap
import com.onlygoodthings.app.resources.qs_close
import com.onlygoodthings.app.resources.qs_expand
import com.onlygoodthings.app.resources.qs_anecdote
import com.onlygoodthings.app.resources.qs_invite
import com.onlygoodthings.app.resources.qs_map
import com.onlygoodthings.app.resources.qs_paw
import com.onlygoodthings.app.resources.qs_pets
import com.onlygoodthings.app.resources.qs_send
import com.onlygoodthings.app.resources.feed_story_donacion
import com.onlygoodthings.app.theme.OgtColors
import com.onlygoodthings.app.theme.OgtDimens
import com.onlygoodthings.app.theme.OgtMotion
import com.onlygoodthings.app.theme.ogtOutlinedFieldColors
import com.onlygoodthings.app.ui.components.CircleIconButton
import com.onlygoodthings.app.ui.components.MediaPagerIndicator
import com.onlygoodthings.app.ui.components.LostAlertMap
import com.onlygoodthings.app.ui.components.OgtCaption
import com.onlygoodthings.app.ui.components.OgtPill
import com.onlygoodthings.app.ui.components.OgtPrimaryButton
import com.onlygoodthings.app.ui.components.OgtSectionTitle
import com.onlygoodthings.app.ui.components.ExpressionGlyph
import com.onlygoodthings.app.ui.components.OgtStitchIcon
import com.onlygoodthings.app.ui.components.OgtTopBar
import com.onlygoodthings.app.ui.components.PostActionRow
import com.onlygoodthings.app.ui.components.PostExtraAction
import com.onlygoodthings.app.ui.components.ScreenColumn
import com.onlygoodthings.app.ui.components.rememberOgtImeOnScroll
import com.onlygoodthings.shared.data.local.LocalAnimalListing
import com.onlygoodthings.shared.data.local.LocalSighting
import com.onlygoodthings.shared.data.local.LocalPostMedia
import com.onlygoodthings.shared.data.local.LocalSocialPost
import com.onlygoodthings.shared.data.local.OgtIds
import com.onlygoodthings.shared.domain.FeedCardKind
import com.onlygoodthings.shared.domain.FeedEventKind
import com.onlygoodthings.shared.domain.MediaKind
import com.onlygoodthings.shared.domain.anecdoteShareText
import com.onlygoodthings.shared.domain.canEditSocialPost
import com.onlygoodthings.shared.domain.canRsvpToGathering
import com.onlygoodthings.shared.domain.feedCardKind
import com.onlygoodthings.shared.domain.gatheringWhenWhere
import com.onlygoodthings.shared.domain.isGatheringPost
import com.onlygoodthings.shared.data.local.isAnecdoteShare
import com.onlygoodthings.shared.domain.isHomenajePost
import com.onlygoodthings.shared.domain.postShareText
import com.onlygoodthings.shared.realtime.OgtRealtime
import com.onlygoodthings.shared.realtime.OgtSdk
import com.onlygoodthings.shared.realtime.currentEpochMs
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource

@Composable
fun PostDetailScreen(
    postId: String = OgtIds.PostTaller,
    onBack: () -> Unit,
    onPetsMap: () -> Unit = {},
    onAdopt: () -> Unit = {},
    onEdit: () -> Unit = {},
    onInvite: () -> Unit = {},
    onOpenLostChat: (String) -> Unit = {},
    onOpenRules: () -> Unit = {},
) {
    val db = LocalOgtDb.current
    val auth = LocalAuth.current
    val session = LocalOgtSession.current
    val me = session.me()
    val copy = LocalOgtCopy.current
    val socialTick by db.socialTick.collectAsState()
    val post = remember(postId, socialTick) { db.post(postId) }
    val author = db.userOrNull(post.authorUserId)
    val listing = remember(post.id, db.lostEpoch) {
        post.listingId?.let { id -> db.animals.firstOrNull { it.id == id } }
    }
    val sightings = remember(listing?.id, db.lostEpoch) {
        listing?.let { db.sightingsOf(it.id) }.orEmpty()
    }
    val here = LocalOgtSession.current.here()
    var sawOpen by remember { mutableStateOf(false) }
    var mediaPage by remember { mutableStateOf<Int?>(null) }
    var mapOpen by remember { mutableStateOf(false) }
    var report by remember { mutableStateOf<LocalSighting?>(null) }
    var sawNote by remember {
        mutableStateOf("Creo que lo vi cerca. Collar rojo, oreja caída.")
    }
    val kind = feedCardKind(post.tag, listing?.kind, post.sourceUrl)
    val media = db.mediaOf(post.id)
    var comment by remember { mutableStateOf("") }
    var commentRev by remember { mutableStateOf(0) }
    var clapRev by remember { mutableStateOf(0) }
    var commentsFocus by remember(post.id) { mutableStateOf(false) }
    var clapped by remember(post.id, post.viewerHasImpacted) { mutableStateOf(post.viewerHasImpacted) }
    var thanks by remember(post.id, post.impactCount) { mutableStateOf(post.impactCount) }
    var hearted by remember(post.id, post.viewerHasHearted) { mutableStateOf(post.viewerHasHearted) }
    var hearts by remember(post.id, post.heartCount) { mutableStateOf(post.heartCount) }
    var overflowOpen by remember { mutableStateOf(false) }
    var reportOpen by remember { mutableStateOf(false) }
    var shareOpen by remember { mutableStateOf(false) }
    var shareAnecdoteId by remember { mutableStateOf<String?>(null) }
    var editCommentId by remember { mutableStateOf<String?>(null) }
    var editCommentBody by remember { mutableStateOf("") }
    var going by remember(post.id) { mutableStateOf(false) }
    var told by remember(post.id) { mutableStateOf(0) }
    var reposted by remember(post.id) { mutableStateOf(false) }
    val myCommentClaps = remember(post.id) { mutableStateMapOf<String, Boolean>() }
    val pageScroll = rememberScrollState()
    val commentList = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val imeOnScroll = rememberOgtImeOnScroll()
    val hideImeOnScrollDown = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (available.y < -6f) imeOnScroll.onVerticalDelta(available.y)
                return Offset.Zero
            }
        }
    }
    var commentsAnchor by remember { mutableStateOf(0) }
    var pinToLatest by remember { mutableStateOf(false) }
    var clapSheetId by remember { mutableStateOf<String?>(null) }
    val comments = remember(post.id, commentRev, clapRev, socialTick) { db.commentsOf(post.id) }
    LaunchedEffect(post.id) {
        runCatching { auth.feed.loadComments(post.id) }
            .getOrNull()
            ?.forEach { if (db.applySocialComment(it)) commentRev += 1 }
        if (!OgtSdk.isStarted()) return@LaunchedEffect
        runCatching {
            OgtRealtime().observePostComments(post.id).collect { live ->
                if (db.applySocialComment(live)) commentRev += 1
            }
        }
    }
    val canRsvp = canRsvpToGathering(post.tag, post.eventStartsAtEpochMs, currentEpochMs())
    val extras = buildList {
        when (kind) {
            FeedCardKind.LOST_PET -> {
                add(PostExtraAction(Res.drawable.qs_map, copy.petsMapCta, onClick = onPetsMap))
            }
            FeedCardKind.ADOPTION -> Unit
            FeedCardKind.COMMUNITY -> {
                if (post.id == OgtIds.PostArboles) {
                    add(PostExtraAction(Res.drawable.qs_invite, "Invitar", onClick = onInvite))
                }
            }
            FeedCardKind.PET_STORY, FeedCardKind.NEWS, FeedCardKind.TERNURA -> Unit
            FeedCardKind.HOMENAJE -> {
                if (db.creditPending(post)) {
                    add(PostExtraAction(Res.drawable.qs_invite, "Invitar a la familia", onClick = onInvite))
                }
                if (!post.isAnecdoteShare()) {
                    add(
                        PostExtraAction(Res.drawable.qs_anecdote, "Sumar anécdota") {
                            scope.launch { pageScroll.animateScrollTo(pageScroll.maxValue) }
                        },
                    )
                }
            }
        }
    }
    val actions: @Composable () -> Unit = {
        PostActionRow(
            clapped = clapped,
            thanks = thanks,
            comments = comments.size,
            onClap = if (kind == FeedCardKind.LOST_PET) {
                null
            } else {
                {
                    if (!clapped) {
                        clapped = true
                        thanks += 1
                        optimisticClapPost(db, auth.feed, scope, me.id, post.id) {
                            session.persistSocialFeed()
                        }
                    }
                }
            },
            hearted = hearted,
            hearts = hearts,
            onHeart = if (kind == FeedCardKind.HOMENAJE) {
                {
                    if (!hearted) {
                        hearted = true
                        hearts += 1
                        optimisticHeartPost(db, auth.feed, scope, me.id, post.id)
                    }
                }
            } else {
                null
            },
            onComments = { scope.launch { pageScroll.animateScrollTo(commentsAnchor) } },
            onShare = { shareOpen = true },
            extras = extras,
            extrasVisible = !commentsFocus,
        )
        if (!commentsFocus && going) {
            val whenWhere = gatheringWhenWhere(post.place, post.eventStartsAtEpochMs, currentEpochMs())
                ?: post.place
            OgtCaption("Vas a ir · $whenWhere · avisamos a $told contactos.")
        }
        if (!commentsFocus && reposted) {
            OgtCaption("Lo compartiste en tu diario.")
        }
    }
    val shareBody = postShareText(
        headline = listing?.title?.takeIf { it.isNotBlank() }
            ?: db.creditName(post).takeIf { isHomenajePost(post.tag) }
            ?: post.tag,
        body = listing?.description?.takeIf { it.isNotBlank() } ?: post.body.takeIf { it.isNotBlank() },
        place = gatheringWhenWhere(post.place, post.eventStartsAtEpochMs, currentEpochMs())
            ?: post.place.takeIf { it.isNotBlank() },
        postId = post.id,
    )
    val collapseComments = rememberUpdatedState {
        commentsFocus = false
        scope.launch { pageScroll.scrollTo((commentsAnchor - 16).coerceAtLeast(0)) }
    }
    LaunchedEffect(pageScroll.value, commentsAnchor) {
        if (!commentsFocus && commentsAnchor > 0 && pageScroll.value > commentsAnchor + 48) {
            commentsFocus = true
        }
    }
    LaunchedEffect(commentsFocus) {
        if (commentsFocus && !pinToLatest) commentList.scrollToItem(0)
    }
    val restoreFromComments = remember(commentList) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                val atTop = commentList.firstVisibleItemIndex == 0 && commentList.firstVisibleItemScrollOffset == 0
                imeOnScroll.onVerticalDelta(available.y)
                if (atTop && available.y > 8f) {
                    collapseComments.value()
                }
                return Offset.Zero
            }
        }
    }
    Column(
        Modifier
            .fillMaxSize()
            .background(OgtColors.canvas)
            .then(
                if (commentsFocus) {
                    Modifier
                        .nestedScroll(imeOnScroll)
                        .pointerInput(Unit) {
                            awaitPointerEventScope {
                                while (true) {
                                    val event = awaitPointerEvent(PointerEventPass.Initial)
                                    val dy = event.changes.maxOfOrNull { change ->
                                        if (change.pressed) change.position.y - change.previousPosition.y else 0f
                                    } ?: 0f
                                    imeOnScroll.onVerticalDelta(dy)
                                }
                            }
                        }
                } else {
                    Modifier
                },
            ),
    ) {
        OgtTopBar(
            title = detailTitle(kind, copy, listing),
            onBack = onBack,
            onOverflow = { overflowOpen = true },
        )
        AnimatedContent(
            targetState = commentsFocus,
            modifier = Modifier.weight(1f).fillMaxWidth(),
            transitionSpec = {
                if (targetState) {
                    OgtMotion.enterUp togetherWith OgtMotion.exitUp
                } else {
                    OgtMotion.enterDown togetherWith OgtMotion.exitDown
                }.using(SizeTransform(clip = false))
            },
            label = "comments-focus",
        ) { focused ->
        if (!focused) {
            Column(
                Modifier
                    .fillMaxSize()
                    .nestedScroll(hideImeOnScrollDown)
                    .verticalScroll(pageScroll),
            ) {
                ScreenColumn {
                    when (kind) {
                        FeedCardKind.LOST_PET -> LostPetDetail(
                            post = post,
                            listing = listing,
                            authorName = db.authorName(post),
                            media = media,
                            actions = actions,
                            sightings = sightings,
                            isAuthor = me.owns(listing?.reporterUserId),
                            here = here,
                            onSaw = { sawOpen = true },
                            onFound = { listing?.let { db.resolveLostListing(me, it.id) } },
                            onOpenSighting = { row -> report = row },
                            onExpandMap = { mapOpen = true },
                            onOpenMedia = { mediaPage = it },
                        )
                        FeedCardKind.ADOPTION -> AdoptionDetail(
                            post = post,
                            listing = listing,
                            authorName = db.authorName(post),
                            level = author?.levelLabel,
                            media = media,
                            actions = actions,
                            isAuthor = me.owns(listing?.reporterUserId) || me.owns(post.authorUserId),
                            onAdopt = onAdopt,
                            onEdit = onEdit,
                            onOpenMedia = { mediaPage = it },
                        )
                        FeedCardKind.PET_STORY -> PetStoryDetail(
                            post = post,
                            authorName = db.authorName(post),
                            level = author?.levelLabel,
                            media = media,
                            actions = actions,
                            onOpenMedia = { mediaPage = it },
                        )
                        FeedCardKind.TERNURA -> TernuraDetail(
                            post = post,
                            authorName = db.authorName(post),
                            level = author?.levelLabel,
                            media = media,
                            actions = actions,
                            onOpenMedia = { mediaPage = it },
                        )
                        FeedCardKind.HOMENAJE -> HomenajeDetail(
                            post = post,
                            authorName = db.authorName(post),
                            level = author?.levelLabel,
                            media = media,
                            actions = actions,
                            onOpenMedia = { mediaPage = it },
                            onShareAnecdote = { shareAnecdoteId = it },
                        )
                        FeedCardKind.NEWS -> NewsDetail(
                            post = post,
                            authorName = db.authorName(post),
                            level = author?.levelLabel,
                            media = media,
                            actions = actions,
                            onOpenMedia = { mediaPage = it },
                        )
                        FeedCardKind.COMMUNITY -> CommunityDetail(
                            post = post,
                            authorName = db.authorName(post),
                            level = author?.levelLabel,
                            media = media,
                            actions = actions,
                            onOpenMedia = { mediaPage = it },
                            canRsvp = canRsvp,
                            going = going,
                            onRsvp = {
                                if (!going) {
                                    told = db.notifyContactsImGoing(me, post)
                                    going = true
                                }
                            },
                        )
                    }
                    Column(Modifier.onGloballyPositioned { commentsAnchor = it.positionInParent().y.toInt() }) {
                        OgtSectionTitle("Comentarios")
                        OgtCaption("${comments.size} · Más recientes")
                    }
                    comments.forEach { row ->
                        val who = db.userOrNull(row.authorUserId)
                        val mine = myCommentClaps[row.id] == true
                        CommentCard(
                            authorUserId = row.authorUserId,
                            author = who?.displayName ?: "Alguien de la comunidad",
                            meta = if (row.edited) "${row.timeLabel} · editado" else row.timeLabel,
                            body = row.body,
                            claps = row.clapCount,
                            clapped = mine,
                            onClap = {
                                if (!mine) {
                                    db.clapComment(me.id, row.id)
                                    myCommentClaps[row.id] = true
                                    clapRev += 1
                                }
                            },
                            onShowClappers = { clapSheetId = row.id },
                            canManage = me.owns(row.authorUserId),
                            onEdit = {
                                editCommentId = row.id
                                editCommentBody = row.body
                            },
                            onDelete = {
                                db.deleteComment(me.id, row.id)
                                commentRev += 1
                                scope.launch { runCatching { auth.feed.deleteComment(row.id) } }
                            },
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                }
            }
        } else {
            LaunchedEffect(comments.size, pinToLatest) {
                if (pinToLatest && comments.isNotEmpty()) {
                    commentList.animateScrollToItem(comments.size)
                    pinToLatest = false
                }
            }
            Column(Modifier.fillMaxSize()) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .clickable { collapseComments.value() }
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text("Ver publicación", color = OgtColors.muted, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                Text(
                    listing?.displayPetName()?.ifBlank { null } ?: db.authorName(post),
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = OgtColors.ink,
                )
                Text(
                    listing?.description?.ifBlank { null } ?: post.body,
                    color = OgtColors.charcoal,
                    fontSize = 14.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                actions()
            }
            LazyColumn(
                state = commentList,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .nestedScroll(restoreFromComments)
                    .padding(horizontal = 20.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                item {
                    OgtSectionTitle("Comentarios")
                    OgtCaption("${comments.size} · Más recientes")
                    Spacer(Modifier.height(6.dp))
                }
                items(comments, key = { it.id }) { row ->
                    val who = db.userOrNull(row.authorUserId)
                    val mine = myCommentClaps[row.id] == true
                    CommentCard(
                        authorUserId = row.authorUserId,
                        author = who?.displayName ?: "Alguien de la comunidad",
                        meta = if (row.edited) "${row.timeLabel} · editado" else row.timeLabel,
                        body = row.body,
                        claps = row.clapCount,
                        clapped = mine,
                        onClap = {
                            if (!mine) {
                                db.clapComment(me.id, row.id)
                                myCommentClaps[row.id] = true
                                clapRev += 1
                            }
                        },
                        onShowClappers = { clapSheetId = row.id },
                        canManage = me.owns(row.authorUserId),
                        onEdit = {
                            editCommentId = row.id
                            editCommentBody = row.body
                        },
                        onDelete = {
                            db.deleteComment(me.id, row.id)
                            commentRev += 1
                            scope.launch { runCatching { auth.feed.deleteComment(row.id) } }
                        },
                        modifier = Modifier.animateItem(),
                    )
                }
                item { Spacer(Modifier.height(8.dp)) }
            }
            }
        }
        }
        CommentComposer(
            value = comment,
            onValueChange = { comment = it },
            onSend = {
                val row = optimisticThreadComment(db, auth.feed, scope, me, post.id, comment)
                if (row != null) {
                    comment = ""
                    commentRev += 1
                    commentsFocus = true
                    pinToLatest = true
                }
            },
        )
    }
    clapSheetId?.let { id ->
        CommentReactionSheet(
            people = db.clappersOf(id),
            onDismiss = { clapSheetId = null },
        )
    }
    shareAnecdoteId?.let { anecdoteId ->
        val story = remember(anecdoteId, socialTick) { db.anecdotes.firstOrNull { it.id == anecdoteId } }
        if (story != null) {
            val honoree = db.creditName(post)
            PostShareSheet(
                title = "Compartir anécdota",
                shareText = anecdoteShareText(honoree, story.body, post.id),
                onDismiss = { shareAnecdoteId = null },
                onRepost = {
                    db.recordFeedEvent(me.id, post.id, FeedEventKind.SHARE)
                    scope.launch {
                        val remote = runCatching { auth.feed.repostAnecdote(story.id) }.getOrNull()
                        if (remote != null) db.upsertRemoteSocial(remote) else db.repostAnecdote(me, story.id)
                        reposted = true
                    }
                },
                onInvite = {
                    db.recordFeedEvent(me.id, post.id, FeedEventKind.SHARE)
                    onInvite()
                },
                onExternal = { db.recordFeedEvent(me.id, post.id, FeedEventKind.SHARE) },
            )
        }
    }
    editCommentId?.let { commentId ->
        Dialog(onDismissRequest = { editCommentId = null }) {
            Column(
                Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .background(OgtColors.surface)
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text("Editar comentario", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = OgtColors.ink)
                OutlinedTextField(
                    value = editCommentBody,
                    onValueChange = { editCommentBody = it },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3,
                    shape = RoundedCornerShape(OgtDimens.buttonRadius),
                    colors = ogtOutlinedFieldColors(),
                )
                OgtPrimaryButton("Guardar") {
                    val body = editCommentBody
                    db.editComment(me.id, commentId, body)
                    commentRev += 1
                    scope.launch { runCatching { auth.feed.editComment(commentId, body) } }
                    editCommentId = null
                }
            }
        }
    }
    if (reportOpen) {
        PostReportSheet(
            kind = kind,
            onDismiss = { reportOpen = false },
            onSubmit = { reason, details ->
                optimisticReportPost(db, auth.feed, scope, me.id, post.id, reason, details) {
                    session.persistSocialFeed()
                }
                onBack()
            },
            onOpenRules = onOpenRules,
        )
    }
    if (overflowOpen) {
        val mine = db.isOwnPost(post, me.aliases())
        PostOverflowSheet(
            isAuthor = mine,
            canEdit = canEditSocialPost(mine, post.authorKind, post.tag, listing?.kind, post.sourceUrl),
            onDismiss = { overflowOpen = false },
            onHide = {
                optimisticHidePost(db, auth.feed, scope, me.id, post.id) { session.persistSocialFeed() }
                onBack()
            },
            onReport = { reportOpen = true },
            onEdit = onEdit,
            onDelete = {
                optimisticDeletePost(
                    db,
                    auth.feed,
                    scope,
                    me.id,
                    post.id,
                    persistFeed = { session.persistSocialFeed() },
                    onRemoved = onBack,
                )
            },
        )
    }
    if (shareOpen) {
        PostShareSheet(
            shareText = shareBody,
            onDismiss = { shareOpen = false },
            onRepost = {
                db.recordFeedEvent(me.id, post.id, FeedEventKind.SHARE)
                reposted = true
            },
            onInvite = {
                db.recordFeedEvent(me.id, post.id, FeedEventKind.SHARE)
                onInvite()
            },
            onExternal = { db.recordFeedEvent(me.id, post.id, FeedEventKind.SHARE) },
        )
    }
    if (sawOpen && listing != null && !listing.resolved) {
        Dialog(onDismissRequest = { sawOpen = false }) {
            Column(
                Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .background(OgtColors.surface)
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text("Creo que lo vi", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = OgtColors.ink)
                OgtCaption("Se planta un pin en el mapa y se abre un hilo con ${author?.displayName ?: "quien publicó"}.")
                OutlinedTextField(
                    value = sawNote,
                    onValueChange = { sawNote = it },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3,
                    shape = RoundedCornerShape(OgtDimens.buttonRadius),
                    colors = ogtOutlinedFieldColors(),
                )
                OgtPrimaryButton("Marcar en el mapa") {
                    val originLat = listing.latitude ?: author?.latitude ?: 0.0
                    val originLng = listing.longitude ?: author?.longitude ?: 0.0
                    val lat = here?.latitude ?: (originLat + 0.0008)
                    val lng = here?.longitude ?: (originLng - 0.0006)
                    val row = db.addSighting(
                        viewer = me,
                        postId = post.id,
                        listingId = listing.id,
                        note = sawNote,
                        latitude = lat,
                        longitude = lng,
                    )
                    sawOpen = false
                    if (row != null) report = row
                }
            }
        }
    }
    mediaPage?.let { start ->
        PostMediaViewer(
            media = media,
            startIndex = start,
            tag = post.tag,
            onClose = { mediaPage = null },
        )
    }
    report?.let { row ->
        SightingReportDialog(sighting = row, onDismiss = { report = null })
    }
    if (mapOpen && listing != null) {
        Dialog(
            onDismissRequest = { mapOpen = false },
            properties = DialogProperties(usePlatformDefaultWidth = false),
        ) {
            Box(Modifier.fillMaxSize().background(OgtColors.canvas)) {
                LostAlertMap(
                    listing = listing,
                    sightings = sightings,
                    here = here,
                    height = 220.dp,
                    fill = true,
                    reportBadges = true,
                    interactive = true,
                    onSelectSighting = { report = it },
                )
                CircleIconButton(
                    art = Res.drawable.qs_close,
                    label = "Cerrar mapa",
                    onClick = { mapOpen = false },
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .windowInsetsPadding(WindowInsets.statusBars)
                        .padding(end = 12.dp, top = 8.dp),
                )
            }
        }
    }
}

@Composable
private fun LostPetDetail(
    post: LocalSocialPost,
    listing: LocalAnimalListing?,
    authorName: String,
    media: List<LocalPostMedia>,
    actions: @Composable () -> Unit,
    sightings: List<LocalSighting>,
    isAuthor: Boolean,
    here: com.onlygoodthings.shared.domain.GeoPoint?,
    onSaw: () -> Unit,
    onFound: () -> Unit,
    onOpenSighting: (LocalSighting) -> Unit,
    onExpandMap: () -> Unit,
    onOpenMedia: (Int) -> Unit,
) {
    val resolved = listing?.resolved == true
    val petName = listing?.displayPetName()?.ifBlank { null } ?: post.tag
    val story = listing?.description?.ifBlank { null } ?: post.body.takeIf { it.isNotBlank() }
    val lastSeen = listing?.lastSeenPlace?.ifBlank { null } ?: listing?.place?.ifBlank { post.place } ?: post.place
    val kindLine = lostSpeciesSize(listing)
    val marks = marksIfDistinct(listing?.marks, story)
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OgtPill(if (resolved) "Ya está en casa" else "Perdido", OgtColors.sunset, OgtColors.sunsetText)
        if (listing?.urgency == "HIGH" && !resolved) {
            OgtPill("Urgente", OgtColors.sunset, OgtColors.sunsetText)
        }
    }
    DetailMedia(media, petName, height = 220.dp, onOpen = onOpenMedia)
    Text(petName, fontWeight = FontWeight.Bold, fontSize = 22.sp, color = OgtColors.ink)
    if (kindLine != null) OgtCaption(kindLine)
    if (!story.isNullOrBlank()) {
        Text(story, fontSize = 16.sp, lineHeight = 22.sp, color = OgtColors.ink)
    }
    marks?.let { OgtCaption("Señas · $it") }
    if (lastSeen.isNotBlank()) {
        OgtCaption(listOfNotNull("Última vista en $lastSeen", post.timeLabel.takeIf { it.isNotBlank() }).joinToString(" · "))
    }
    listing?.let { row ->
        val radius = formatAlertRadius(row.alertRadiusM)
        val neighbors = row.neighborsAlerted.takeIf { it > 0 }?.let { "$it vecinos avisados" }
        OgtCaption(listOfNotNull("Radio $radius", neighbors).joinToString(" · "))
    }
    if (listing != null) {
        LostAlertMap(
            listing = listing,
            sightings = sightings,
            here = here,
            height = 220.dp,
            onSelectSighting = onOpenSighting,
            onExpand = onExpandMap,
        )
    }
    OgtCaption("Publicó $authorName")
    if (!resolved && isAuthor) {
        OgtPrimaryButton("Lo encontramos", onClick = onFound)
    } else if (!resolved && !isAuthor) {
        OgtPrimaryButton("Creo que lo vi", onClick = onSaw)
    }
    actions()
}

@Composable
private fun AdoptionDetail(
    post: LocalSocialPost,
    listing: LocalAnimalListing?,
    authorName: String,
    level: String?,
    media: List<LocalPostMedia>,
    actions: @Composable () -> Unit,
    isAuthor: Boolean,
    onAdopt: () -> Unit,
    onEdit: () -> Unit,
    onOpenMedia: (Int) -> Unit,
) {
    val copy = LocalOgtCopy.current
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OgtStitchIcon(Res.drawable.qs_paw, copy.petsAdoption, tint = OgtColors.secondary)
        OgtPill(copy.petsAdoption, OgtColors.sand, OgtColors.ink)
    }
    AuthorLine(authorName, level, post)
    DetailMedia(media, post.tag, height = 240.dp, onOpen = onOpenMedia)
    actions()
    if (isAuthor) {
        OgtPrimaryButton(copy.petsEdit, onClick = onEdit)
    } else {
        OgtPrimaryButton(copy.petsWantAdopt, onClick = onAdopt)
    }
    Text(listing?.displayPetName()?.ifBlank { null } ?: post.tag, fontWeight = FontWeight.Bold, fontSize = 20.sp)
    listing?.let { row ->
        val bits = listOfNotNull(
            row.ageLabel.takeIf { it.isNotBlank() },
            when (row.sex) { "HEMBRA" -> "Hembra"; "MACHO" -> "Macho"; else -> null },
            lostSpeciesSize(row),
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
}

@Composable
private fun PetStoryDetail(
    post: LocalSocialPost,
    authorName: String,
    level: String?,
    media: List<LocalPostMedia>,
    actions: @Composable () -> Unit,
    onOpenMedia: (Int) -> Unit,
) {
    OgtPill(post.tag, OgtColors.sand, OgtColors.secondary)
    AuthorLine(authorName, level, post)
    DetailMedia(media, post.tag, height = 220.dp, onOpen = onOpenMedia)
    actions()
    Text(post.body, fontSize = 16.sp, lineHeight = 22.sp, color = OgtColors.ink)
    if (!post.sourceUrl.isNullOrBlank()) {
        OgtCaption("Resumen de nota · ${post.sourceUrl}")
    }
}

@Composable
private fun TernuraDetail(
    post: LocalSocialPost,
    authorName: String,
    level: String?,
    media: List<LocalPostMedia>,
    actions: @Composable () -> Unit,
    onOpenMedia: (Int) -> Unit,
) {
    val copy = LocalOgtCopy.current
    val db = LocalOgtDb.current
    val credit = db.creditName(post)
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OgtStitchIcon(Res.drawable.qs_pets, copy.feedTitleTernura, tint = OgtColors.secondary)
        OgtPill(post.tag, OgtColors.sand, OgtColors.secondary)
    }
    AuthorLine(authorName, level, post, showCredit = false)
    DetailMedia(media, post.tag, height = 240.dp, onOpen = onOpenMedia)
    Text(post.body, fontSize = 16.sp, lineHeight = 22.sp, color = OgtColors.ink)
    if (credit != authorName || db.creditPending(post)) {
        OgtCaption(
            if (db.creditPending(post)) "La historia es de $credit · Invitada" else "La historia es de $credit",
        )
    }
    actions()
}

@Composable
private fun HomenajeDetail(
    post: LocalSocialPost,
    authorName: String,
    level: String?,
    media: List<LocalPostMedia>,
    actions: @Composable () -> Unit,
    onOpenMedia: (Int) -> Unit,
    onShareAnecdote: (String) -> Unit,
) {
    val copy = LocalOgtCopy.current
    val db = LocalOgtDb.current
    val auth = LocalAuth.current
    val me = LocalOgtSession.current.me()
    val scope = rememberCoroutineScope()
    val socialTick by db.socialTick.collectAsState()
    val honoree = db.creditName(post)
    var draft by remember(post.id) { mutableStateOf("") }
    var openComments by remember(post.id) { mutableStateOf<String?>(null) }
    var editAnecdoteId by remember { mutableStateOf<String?>(null) }
    var editAnecdoteBody by remember { mutableStateOf("") }
    val stories = remember(post.id, socialTick) { db.anecdotesOf(post.id) }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OgtStitchIcon(Res.drawable.qs_invite, copy.feedTitleHomenaje, tint = OgtColors.secondary)
        OgtPill(post.tag, OgtColors.sand, OgtColors.ink)
    }
    Text(honoree, fontWeight = FontWeight.Bold, fontSize = 22.sp, color = OgtColors.ink)
    OgtCaption(
        listOfNotNull(
            "Lo cuenta $authorName",
            level?.takeIf { it.isNotBlank() },
            post.place.takeIf { it.isNotBlank() },
            post.timeLabel.takeIf { it.isNotBlank() },
        ).joinToString(" · "),
    )
    if (db.creditPending(post)) {
        OgtCaption("La familia puede reivindicar este crédito.")
    }
    DetailMedia(media, post.tag, height = 220.dp, onOpen = onOpenMedia)
    Text(post.body, fontSize = 16.sp, lineHeight = 22.sp, color = OgtColors.ink)
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OgtStitchIcon(Res.drawable.qs_anecdote, "Anécdotas", tint = OgtColors.secondary)
        OgtSectionTitle("Anécdotas")
    }
    OgtCaption("${stories.size} · Hechos que lo recuerdan")
    stories.forEach { story ->
        HomageAnecdoteCard(
            story = story,
            mine = me.owns(story.authorUserId),
            commentsOpen = openComments == story.id,
            onToggleComments = { openComments = if (openComments == story.id) null else story.id },
            onClap = {
                db.clapAnecdote(me.id, story.id)
                scope.launch { runCatching { auth.feed.clapAnecdote(story.id) }.onSuccess { db.upsertAnecdote(it) } }
            },
            onHeart = {
                db.heartAnecdote(me.id, story.id)
                scope.launch { runCatching { auth.feed.heartAnecdote(story.id) }.onSuccess { db.upsertAnecdote(it) } }
            },
            onShare = { onShareAnecdote(story.id) },
            onEdit = {
                editAnecdoteId = story.id
                editAnecdoteBody = story.body
            },
            onDelete = {
                db.deleteAnecdote(me.id, story.id)
                scope.launch { runCatching { auth.feed.deleteAnecdote(story.id) } }
            },
            onAddComment = { text ->
                val row = db.addComment(me, post.id, text, anecdoteId = story.id)
                if (row != null) {
                    scope.launch {
                        runCatching { auth.feed.addComment(post.id, text, null, story.id) }
                            .onSuccess { remote -> db.replaceComment(row.id, remote) }
                    }
                }
            },
            onEditComment = { commentId, body ->
                db.editComment(me.id, commentId, body)
                scope.launch { runCatching { auth.feed.editComment(commentId, body) } }
            },
            onDeleteComment = { commentId ->
                db.deleteComment(me.id, commentId)
                scope.launch { runCatching { auth.feed.deleteComment(commentId) } }
            },
        )
    }
    OutlinedTextField(
        value = draft,
        onValueChange = { draft = it },
        modifier = Modifier.fillMaxWidth(),
        placeholder = { Text("Sumá una anécdota de $honoree", color = OgtColors.muted) },
        leadingIcon = {
            OgtStitchIcon(Res.drawable.qs_anecdote, "Sumar anécdota", tint = OgtColors.secondary)
        },
        minLines = 3,
        shape = RoundedCornerShape(OgtDimens.buttonRadius),
        colors = ogtOutlinedFieldColors(),
    )
    if (draft.isNotBlank()) {
        OgtPrimaryButton("Publicar anécdota") {
            val text = draft.trim()
            val local = db.addAnecdote(me, post.id, text)
            draft = ""
            if (local != null) {
                scope.launch {
                    runCatching { auth.feed.addAnecdote(post.id, text) }
                        .onSuccess { remote -> db.replaceAnecdote(local.id, remote) }
                }
            }
        }
    }
    actions()
    editAnecdoteId?.let { anecdoteId ->
        Dialog(onDismissRequest = { editAnecdoteId = null }) {
            Column(
                Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .background(OgtColors.surface)
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text("Editar anécdota", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = OgtColors.ink)
                OutlinedTextField(
                    value = editAnecdoteBody,
                    onValueChange = { editAnecdoteBody = it },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3,
                    shape = RoundedCornerShape(OgtDimens.buttonRadius),
                    colors = ogtOutlinedFieldColors(),
                )
                OgtPrimaryButton("Guardar") {
                    val body = editAnecdoteBody
                    db.editAnecdote(me.id, anecdoteId, body)
                    scope.launch { runCatching { auth.feed.editAnecdote(anecdoteId, body) } }
                    editAnecdoteId = null
                }
            }
        }
    }
}

@Composable
private fun HomageAnecdoteCard(
    story: com.onlygoodthings.shared.data.local.LocalAnecdote,
    mine: Boolean,
    commentsOpen: Boolean,
    onToggleComments: () -> Unit,
    onClap: () -> Unit,
    onHeart: () -> Unit,
    onShare: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onAddComment: (String) -> Unit,
    onEditComment: (String, String) -> Unit,
    onDeleteComment: (String) -> Unit,
) {
    val db = LocalOgtDb.current
    val me = LocalOgtSession.current.me()
    val socialTick by db.socialTick.collectAsState()
    val replies = remember(story.id, socialTick) { db.commentsOf(story.postId, story.id) }
    var reply by remember(story.id) { mutableStateOf("") }
    var editCommentId by remember { mutableStateOf<String?>(null) }
    var editCommentBody by remember { mutableStateOf("") }
    val well = RoundedCornerShape(16.dp)
    Column(
        Modifier
            .fillMaxWidth()
            .clip(well)
            .background(OgtColors.surface)
            .border(1.dp, OgtColors.hairline, well)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OgtStitchIcon(Res.drawable.qs_anecdote, "Anécdota", size = 16.dp, tint = OgtColors.secondary)
            Text(story.authorName, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = OgtColors.ink)
        }
        Text(story.body, fontSize = 15.sp, lineHeight = 21.sp, color = OgtColors.ink)
        if (!story.sourceUrl.isNullOrBlank()) {
            OgtCaption("Fuente en el detalle")
        }
        if (mine) {
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(
                    "Editar",
                    color = OgtColors.secondary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.clickable(onClick = onEdit),
                )
                Text(
                    "Borrar",
                    color = OgtColors.muted,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.clickable(onClick = onDelete),
                )
            }
        }
        PostActionRow(
            clapped = story.viewerHasImpacted,
            thanks = story.impactCount,
            comments = story.commentCount.coerceAtLeast(replies.size),
            onClap = onClap,
            hearted = story.viewerHasHearted,
            hearts = story.heartCount,
            onHeart = onHeart,
            onComments = onToggleComments,
            onShare = onShare,
        )
        if (commentsOpen) {
            if (replies.isEmpty()) {
                OgtCaption("Todavía no hay comentarios en esta anécdota.")
            }
            replies.forEach { row ->
                val who = db.userOrNull(row.authorUserId)
                CommentCard(
                    authorUserId = row.authorUserId,
                    author = who?.displayName ?: "Alguien de la comunidad",
                    meta = if (row.edited) "${row.timeLabel} · editado" else row.timeLabel,
                    body = row.body,
                    claps = row.clapCount,
                    clapped = false,
                    onClap = { db.clapComment(me.id, row.id) },
                    onShowClappers = {},
                    canManage = me.owns(row.authorUserId),
                    onEdit = {
                        editCommentId = row.id
                        editCommentBody = row.body
                    },
                    onDelete = { onDeleteComment(row.id) },
                )
            }
            OutlinedTextField(
                value = reply,
                onValueChange = { reply = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Comentá esta anécdota", color = OgtColors.muted) },
                minLines = 2,
                shape = RoundedCornerShape(OgtDimens.buttonRadius),
                colors = ogtOutlinedFieldColors(),
            )
            if (reply.isNotBlank()) {
                OgtPrimaryButton("Comentar") {
                    onAddComment(reply.trim())
                    reply = ""
                }
            }
        }
    }
    editCommentId?.let { commentId ->
        Dialog(onDismissRequest = { editCommentId = null }) {
            Column(
                Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .background(OgtColors.surface)
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text("Editar comentario", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = OgtColors.ink)
                OutlinedTextField(
                    value = editCommentBody,
                    onValueChange = { editCommentBody = it },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3,
                    shape = RoundedCornerShape(OgtDimens.buttonRadius),
                    colors = ogtOutlinedFieldColors(),
                )
                OgtPrimaryButton("Guardar") {
                    onEditComment(commentId, editCommentBody)
                    editCommentId = null
                }
            }
        }
    }
}

@Composable
private fun NewsDetail(
    post: LocalSocialPost,
    authorName: String,
    level: String?,
    media: List<LocalPostMedia>,
    actions: @Composable () -> Unit,
    onOpenMedia: (Int) -> Unit,
) {
    OgtPill(post.tag)
    AuthorLine(authorName, level, post)
    DetailMedia(media, post.tag, height = 220.dp, onOpen = onOpenMedia)
    actions()
    Text(post.body, fontSize = 16.sp, lineHeight = 22.sp, color = OgtColors.ink)
    if (!post.sourceUrl.isNullOrBlank()) {
        OgtCaption("Fuente: ${post.sourceUrl}")
    }
}

@Composable
private fun CommunityDetail(
    post: LocalSocialPost,
    authorName: String,
    level: String?,
    media: List<LocalPostMedia>,
    actions: @Composable () -> Unit,
    onOpenMedia: (Int) -> Unit,
    canRsvp: Boolean = false,
    going: Boolean = false,
    onRsvp: () -> Unit = {},
) {
    val gathering = isGatheringPost(post.tag)
    val whenWhere = gatheringWhenWhere(post.place, post.eventStartsAtEpochMs, currentEpochMs())
    OgtPill(post.tag)
    AuthorLine(authorName, level, post, showPlace = !gathering)
    DetailMedia(media, post.tag, height = 220.dp, onOpen = onOpenMedia)
    if (whenWhere != null) {
        OgtCaption("Cuándo y dónde")
        Text(whenWhere, fontWeight = FontWeight.Bold, fontSize = 18.sp, color = OgtColors.ink)
    } else if (post.place.isNotBlank()) {
        OgtCaption(post.place)
    }
    Text(post.body, fontSize = 16.sp, lineHeight = 22.sp, color = OgtColors.ink)
    if (canRsvp && !going) {
        OgtPrimaryButton("Asistiré", onClick = onRsvp)
    } else if (going) {
        OgtCaption("Ya anotamos que vas.")
    }
    actions()
    Text("#${post.tag.replace(" ", "")}", color = OgtColors.primary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
}

@Composable
private fun AuthorLine(
    authorName: String,
    level: String?,
    post: LocalSocialPost,
    showPlace: Boolean = true,
    showCredit: Boolean = true,
) {
    val db = LocalOgtDb.current
    Text(authorName, fontWeight = FontWeight.Bold, fontSize = 20.sp)
    OgtCaption(
        listOfNotNull(
            level?.takeIf { it.isNotBlank() },
            post.place.takeIf { showPlace && it.isNotBlank() },
            post.timeLabel.takeIf { it.isNotBlank() },
        ).joinToString(" · "),
    )
    if (!showCredit) return
    val credit = db.creditName(post)
    if (credit != authorName || db.creditPending(post)) {
        OgtCaption(if (db.creditPending(post)) "Lo hizo $credit · Invitada" else "Lo hizo $credit")
    }
}

@Composable
private fun DetailMedia(
    media: List<LocalPostMedia>,
    tag: String,
    height: androidx.compose.ui.unit.Dp,
    onOpen: (Int) -> Unit,
) {
    if (media.isEmpty()) {
        Box(Modifier.fillMaxWidth().height(height).clip(RoundedCornerShape(16.dp)).background(OgtColors.sand))
        return
    }
    val pager = rememberPagerState { media.size }
    Box(Modifier.fillMaxWidth().height(height).clip(RoundedCornerShape(16.dp))) {
        HorizontalPager(state = pager, modifier = Modifier.fillMaxSize(), key = { media[it].id }) { page ->
            val item = media[page]
            Box(
                Modifier
                    .fillMaxSize()
                    .clickable { onOpen(page) },
            ) {
                OgtPostImage(
                    item = item,
                    fallback = Res.drawable.feed_story_donacion,
                    contentDescription = item.altText ?: tag,
                    modifier = Modifier.fillMaxSize(),
                )
                if (item.kind == MediaKind.VIDEO) {
                    Box(
                        Modifier
                            .align(Alignment.Center)
                            .size(52.dp)
                            .clip(CircleShape)
                            .background(OgtColors.ink.copy(alpha = 0.55f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        OgtStitchIcon(Res.drawable.qs_expand, "Reproducir", tint = OgtColors.onPrimary)
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

private fun lostSpeciesSize(listing: LocalAnimalListing?): String? {
    if (listing == null) return null
    val species = animalSpeciesLabel(listing.species) ?: return animalSizeLabel(listing.size)
    val size = animalSizeLabel(listing.size)
    return listOfNotNull(species, size).joinToString(" ")
}

private fun formatAlertRadius(meters: Int): String =
    if (meters >= 1000) "${meters / 1000} km" else "$meters m"

/** Oculta señas si ya están dichas en la descripción. */
private fun marksIfDistinct(marks: String?, story: String?): String? {
    val raw = marks?.trim()?.takeIf { it.isNotBlank() } ?: return null
    val body = story.orEmpty().lowercase()
    if (body.isBlank()) return raw
    val tokens = raw.lowercase().split(',', '·', '.').map { it.trim() }.filter { it.length >= 6 }
    if (tokens.isEmpty()) return raw
    val already = tokens.count { token -> body.contains(token) }
    return if (already * 2 >= tokens.size) null else raw
}

private fun detailTitle(
    kind: FeedCardKind,
    copy: com.onlygoodthings.app.i18n.OgtCopy,
    listing: LocalAnimalListing?,
): String {
    val pet = listing?.displayPetName()?.ifBlank { null }
    return when (kind) {
        FeedCardKind.LOST_PET -> pet ?: copy.petsLost
        FeedCardKind.ADOPTION -> pet ?: copy.petsAdoption
        FeedCardKind.PET_STORY -> copy.feedFilterPets
        FeedCardKind.TERNURA -> copy.feedTitleTernura
        FeedCardKind.HOMENAJE -> copy.feedTitleHomenaje
        FeedCardKind.NEWS -> copy.feedFilterNews
        FeedCardKind.COMMUNITY -> copy.feedFilterCommunity
    }
}

@Composable
private fun CommentCard(
    authorUserId: String,
    author: String,
    meta: String,
    body: String,
    claps: Int,
    clapped: Boolean,
    onClap: () -> Unit,
    onShowClappers: () -> Unit,
    modifier: Modifier = Modifier,
    canManage: Boolean = false,
    onEdit: () -> Unit = {},
    onDelete: () -> Unit = {},
) {
    val well = RoundedCornerShape(16.dp)
    Row(
        modifier
            .fillMaxWidth()
            .clip(well)
            .background(OgtColors.sand)
            .border(1.dp, OgtColors.hairline, well)
            .padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Image(
            painter = painterResource(commentAuthorAvatar(authorUserId)),
            contentDescription = author,
            modifier = Modifier.size(36.dp).clip(CircleShape).border(1.dp, OgtColors.hairline, CircleShape),
            contentScale = ContentScale.Crop,
        )
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    author,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp,
                    color = OgtColors.ink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                Text(meta, color = OgtColors.muted, fontSize = 12.sp, maxLines = 1)
            }
            Text(body, color = OgtColors.charcoal, fontSize = 15.sp, lineHeight = 21.sp)
            if (canManage) {
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text(
                        "Editar",
                        color = OgtColors.secondary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.clickable(onClick = onEdit),
                    )
                    Text(
                        "Borrar",
                        color = OgtColors.muted,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.clickable(onClick = onDelete),
                    )
                }
            }
        }
        ExpressionGlyph(
            art = Res.drawable.qs_clap,
            label = "Aplaudir comentario",
            selected = clapped,
            count = claps,
            burstOnSelect = true,
            onClick = onClap,
            onCountClick = onShowClappers,
        )
    }
}

@Composable
private fun CommentComposer(
    value: String,
    onValueChange: (String) -> Unit,
    onSend: () -> Unit,
) {
    val canSend = value.isNotBlank()
    val sendTint by animateColorAsState(
        targetValue = if (canSend) OgtColors.secondary else OgtColors.muted,
        animationSpec = OgtMotion.color,
        label = "send-tint",
    )
    val sendWell by animateColorAsState(
        targetValue = if (canSend) OgtColors.secondary.copy(alpha = 0.12f) else Color.Transparent,
        animationSpec = OgtMotion.color,
        label = "send-well",
    )
    val sendScale by animateFloatAsState(
        targetValue = if (canSend) 1f else 0.92f,
        animationSpec = OgtMotion.press,
        label = "send-scale",
    )
    Box(
        Modifier
            .fillMaxWidth()
            .background(OgtColors.canvas)
            .border(width = 1.dp, color = OgtColors.hairline)
            .windowInsetsPadding(WindowInsets.navigationBars.union(WindowInsets.ime))
            .padding(horizontal = 20.dp, vertical = 10.dp),
    ) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth().heightIn(min = 76.dp),
            placeholder = { Text("Escribí un comentario", color = OgtColors.muted) },
            shape = RoundedCornerShape(OgtDimens.buttonRadius),
            colors = ogtOutlinedFieldColors(),
            minLines = 2,
        )
        Box(
            Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 6.dp, bottom = 6.dp)
                .graphicsLayer {
                    scaleX = sendScale
                    scaleY = sendScale
                }
                .size(36.dp)
                .clip(CircleShape)
                .background(sendWell)
                .then(
                    if (canSend) Modifier.clickable(onClick = onSend) else Modifier,
                ),
            contentAlignment = Alignment.Center,
        ) {
            OgtStitchIcon(
                Res.drawable.qs_send,
                "Enviar",
                tint = sendTint,
            )
        }
    }
}

private fun commentAuthorAvatar(userId: String): DrawableResource = when (userId) {
    OgtIds.Roberto -> Res.drawable.feed_avatar_roberto
    OgtIds.Sofia, OgtIds.Camila -> Res.drawable.feed_avatar_sofia
    OgtIds.CarlosG, OgtIds.CarlosR, OgtIds.DiegoF -> Res.drawable.feed_avatar_carlos
    OgtIds.Mariana, OgtIds.Lucia, OgtIds.MarianaD, OgtIds.Valeria, OgtIds.ValeriaP -> Res.drawable.feed_avatar_mariana
    OgtIds.Lucas -> Res.drawable.feed_avatar_me
    else -> Res.drawable.feed_avatar_reply
}
