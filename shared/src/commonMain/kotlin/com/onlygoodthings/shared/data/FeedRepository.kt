package com.onlygoodthings.shared.data

import com.onlygoodthings.shared.domain.FeedEventKind
import com.onlygoodthings.shared.domain.FeedMode
import com.onlygoodthings.shared.domain.FeedTopicFamily
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
    suspend fun addComment(postId: String, body: String, parentCommentId: String?): SocialComment
    suspend fun reportPost(postId: String, reason: String, details: String?)
    suspend fun recordFeedEvent(postId: String, kind: FeedEventKind, dwellMs: Int? = null)
}
