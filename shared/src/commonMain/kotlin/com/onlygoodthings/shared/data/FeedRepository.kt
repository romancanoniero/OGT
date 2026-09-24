package com.onlygoodthings.shared.data

import com.onlygoodthings.shared.domain.FeedEventKind
import com.onlygoodthings.shared.domain.FeedMode
import com.onlygoodthings.shared.domain.FeedTopicFamily
import com.onlygoodthings.shared.domain.SocialAnecdote
import com.onlygoodthings.shared.domain.SocialComment
import com.onlygoodthings.shared.domain.SocialPost

interface FeedRepository {
    suspend fun loadFeed(
        cursor: String?,
        pageSize: Int = 20,
        mode: FeedMode = FeedMode.HOME,
        family: FeedTopicFamily? = null,
    ): List<SocialPost>
    suspend fun clapPost(postId: String): SocialPost
    suspend fun loadComments(postId: String): List<SocialComment>
    suspend fun addComment(
        postId: String,
        body: String,
        parentCommentId: String?,
        anecdoteId: String? = null,
    ): SocialComment
    suspend fun editComment(commentId: String, body: String): SocialComment
    suspend fun deleteComment(commentId: String)
    suspend fun addAnecdote(postId: String, body: String, sourceUrl: String? = null): SocialAnecdote
    suspend fun editAnecdote(anecdoteId: String, body: String): SocialAnecdote
    suspend fun deleteAnecdote(anecdoteId: String)
    suspend fun clapAnecdote(anecdoteId: String): SocialAnecdote
    suspend fun heartAnecdote(anecdoteId: String): SocialAnecdote
    suspend fun repostAnecdote(anecdoteId: String): SocialPost
    suspend fun editPost(postId: String, body: String, topic: String? = null): SocialPost
    suspend fun deletePost(postId: String)
    suspend fun reportPost(postId: String, reason: String, details: String?)
    suspend fun recordFeedEvent(postId: String, kind: FeedEventKind, dwellMs: Int? = null)
    suspend fun publishPost(
        body: String,
        topic: String,
        media: List<com.onlygoodthings.shared.domain.PostMediaItem>,
        latitude: Double? = null,
        longitude: Double? = null,
        protagonistUserId: String? = null,
        participantUserIds: List<String> = emptyList(),
        honoreeName: String? = null,
    ): SocialPost
    suspend fun follow(userId: String)
    suspend fun unfollow(userId: String)
}
