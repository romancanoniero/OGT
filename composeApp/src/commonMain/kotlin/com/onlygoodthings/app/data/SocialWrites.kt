package com.onlygoodthings.app.data

import com.onlygoodthings.shared.data.FeedRepository
import com.onlygoodthings.shared.data.local.LocalComment
import com.onlygoodthings.shared.data.local.LocalUser
import com.onlygoodthings.shared.data.local.OgtLocalDatabase
import com.onlygoodthings.shared.domain.FeedEventKind
import com.onlygoodthings.shared.domain.SocialComment
import com.onlygoodthings.shared.realtime.OgtRealtime
import com.onlygoodthings.shared.realtime.OgtSdk
import com.onlygoodthings.shared.realtime.currentEpochMs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Writes locales al toque; REST y sockets reconcilian o revierten.
 */
fun optimisticClapPost(
    db: OgtLocalDatabase,
    feed: FeedRepository,
    scope: CoroutineScope,
    viewerId: String,
    postId: String,
    persistFeed: () -> Unit = {},
) {
    val before = db.snapshotPost(postId) ?: return
    if (before.viewerHasImpacted) return
    db.clapPost(viewerId, postId) ?: return
    scope.launch {
        val remote = runCatching { feed.clapPost(postId) }
        remote.onSuccess { db.upsertRemoteSocial(it) }
            .onFailure { db.restorePost(before) }
        if (remote.isSuccess && OgtSdk.isStarted()) {
            db.socialCountersOf(postId)?.let { runCatching { OgtRealtime().pushSocialCounters(it) } }
        }
        persistFeed()
    }
}

fun optimisticHeartPost(
    db: OgtLocalDatabase,
    feed: FeedRepository,
    scope: CoroutineScope,
    viewerId: String,
    postId: String,
) {
    val before = db.snapshotPost(postId) ?: return
    if (before.viewerHasHearted) return
    db.heartPost(viewerId, postId) ?: return
    scope.launch {
        runCatching { feed.recordFeedEvent(postId, FeedEventKind.HEART) }
            .onFailure { db.restorePost(before) }
    }
}

fun optimisticToggleFollow(
    db: OgtLocalDatabase,
    feed: FeedRepository,
    scope: CoroutineScope,
    viewerId: String,
    followedId: String,
    persistFeed: () -> Unit = {},
) {
    val wasFollowing = db.isFollowing(viewerId, followedId)
    db.toggleFollow(viewerId, followedId)
    val nowFollowing = db.isFollowing(viewerId, followedId)
    scope.launch {
        val result = if (nowFollowing) {
            runCatching { feed.follow(followedId) }
        } else {
            runCatching { feed.unfollow(followedId) }
        }
        result.onFailure {
            if (db.isFollowing(viewerId, followedId) != wasFollowing) {
                db.toggleFollow(viewerId, followedId)
            }
        }
        persistFeed()
    }
}

fun optimisticThreadComment(
    db: OgtLocalDatabase,
    feed: FeedRepository,
    scope: CoroutineScope,
    viewer: LocalUser,
    postId: String,
    body: String,
): LocalComment? {
    val row = db.addComment(viewer, postId, body) ?: return null
    scope.launch {
        val remote = runCatching { feed.addComment(postId, row.body, row.parentCommentId, null) }
        remote.onSuccess { db.replaceComment(row.id, it) }
        val published = remote.getOrNull()
        if (OgtSdk.isStarted()) {
            val sockets = OgtRealtime()
            db.socialCountersOf(postId)?.let { runCatching { sockets.pushSocialCounters(it) } }
            val live = published ?: SocialComment(
                id = row.id,
                postId = row.postId,
                authorUserId = row.authorUserId,
                authorName = viewer.displayName,
                parentCommentId = row.parentCommentId,
                body = row.body,
                createdAtEpochMs = currentEpochMs(),
            )
            runCatching { sockets.pushSocialComment(live) }
        }
    }
    return row
}

fun optimisticEditPost(
    db: OgtLocalDatabase,
    feed: FeedRepository,
    scope: CoroutineScope,
    viewerId: String,
    postId: String,
    body: String,
    topic: String? = null,
    persistFeed: () -> Unit = {},
) {
    val before = db.snapshotPost(postId) ?: return
    val edited = db.editOwnPost(viewerId, postId, body, topic) ?: return
    persistFeed()
    scope.launch {
        val remote = runCatching { feed.editPost(postId, edited.body, edited.tag) }
        remote.onSuccess { db.upsertRemoteSocial(it) }
            .onFailure { db.restorePost(before) }
        persistFeed()
    }
}

fun optimisticDeletePost(
    db: OgtLocalDatabase,
    feed: FeedRepository,
    scope: CoroutineScope,
    viewerId: String,
    postId: String,
    persistFeed: () -> Unit = {},
    onRemoved: () -> Unit = {},
) {
    val before = db.snapshotPost(postId) ?: return
    db.removeOwnPost(viewerId, postId) ?: return
    persistFeed()
    onRemoved()
    scope.launch {
        runCatching { feed.deletePost(postId) }
            .onFailure {
                db.restorePost(before)
                persistFeed()
            }
    }
}

fun optimisticHidePost(
    db: OgtLocalDatabase,
    feed: FeedRepository,
    scope: CoroutineScope,
    viewerId: String,
    postId: String,
    persistFeed: () -> Unit = {},
) {
    db.recordFeedEvent(viewerId, postId, FeedEventKind.HIDE)
    persistFeed()
    backgroundFeedEvent(feed, scope, postId, FeedEventKind.HIDE)
}

fun optimisticReportPost(
    db: OgtLocalDatabase,
    feed: FeedRepository,
    scope: CoroutineScope,
    viewerId: String,
    postId: String,
    reason: String,
    details: String? = null,
    persistFeed: () -> Unit = {},
) {
    db.recordFeedEvent(viewerId, postId, FeedEventKind.HIDE)
    persistFeed()
    scope.launch { runCatching { feed.reportPost(postId, reason, details) } }
}

fun backgroundFeedEvent(
    feed: FeedRepository,
    scope: CoroutineScope,
    postId: String,
    kind: FeedEventKind,
) {
    scope.launch { runCatching { feed.recordFeedEvent(postId, kind) } }
}
