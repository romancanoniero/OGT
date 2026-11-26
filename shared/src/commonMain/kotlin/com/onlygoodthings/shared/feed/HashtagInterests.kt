package com.onlygoodthings.shared.feed

import com.onlygoodthings.shared.domain.*
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.sqrt

/** Bounded evidence per post/action, with recency decay; no global popularity shortcut. */
internal class HashtagInterests(events: List<FeedEventSignal>, now: Long) {
    private val topics = mutableMapOf<String, Double>()
    private val authors = mutableMapOf<String, Double>()
    private var evidence = 0.0
    val hasEvidence: Boolean get() = evidence > 0.0

    init {
        events.groupBy { it.postId to it.kind }.values.forEach { repeated ->
            val event = repeated.maxBy { it.occurredAtEpochMs }
            val strength = when(event.kind) {
                FeedEventKind.CLAP -> 1.0
                FeedEventKind.HEART -> 1.2
                FeedEventKind.COMMENT -> 1.4
                FeedEventKind.SHARE -> 2.0
                FeedEventKind.PROFILE_TAP -> 0.5
                FeedEventKind.DWELL -> (event.dwellMs / 15_000.0).coerceIn(0.0,0.7)
                FeedEventKind.HIDE -> -2.0
                FeedEventKind.SKIP -> -0.3
                FeedEventKind.IMPRESSION -> 0.0
            }
            val ageDays = if(event.occurredAtEpochMs<=0) 0.0 else (now-event.occurredAtEpochMs).coerceAtLeast(0)/86_400_000.0
            val weight = strength * 2.0.pow(-ageDays/30.0)
            evidence += abs(weight)
            val tags = event.hashtags.ifEmpty { Hashtags.legacy(event.topic) }.distinctBy { it.interestKey }
            tags.forEach { tag ->
                val identities=setOf(tag.interestKey,"tag:${tag.key}")
                identities.forEach { identity -> topics[identity]=(topics[identity] ?: 0.0)+weight/sqrt(tags.size.toDouble()) }
            }
            event.authorUserId?.let { authors[it]=(authors[it] ?: 0.0)+weight }
        }
    }

    fun topic(post: FeedCandidate): Double {
        val tags = post.hashtags.ifEmpty { Hashtags.legacy(post.topic) }
        return tags.flatMap { listOfNotNull(topics[it.interestKey],topics["tag:${it.key}"]) }
            .map { it/(evidence+3.0) }.maxOrNull()?.coerceIn(-1.0,1.0) ?: 0.0
    }
    fun author(post: FeedCandidate): Double = ((authors[post.authorUserId] ?: 0.0)/(evidence+6.0)).coerceIn(-1.0,1.0)
}
