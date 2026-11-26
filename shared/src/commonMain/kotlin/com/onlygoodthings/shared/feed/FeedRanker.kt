package com.onlygoodthings.shared.feed

import com.onlygoodthings.shared.domain.FeedEventKind
import com.onlygoodthings.shared.domain.FeedMode
import com.onlygoodthings.shared.domain.FeedShowReason
import com.onlygoodthings.shared.domain.FeedTopicFamily
import com.onlygoodthings.shared.domain.GeoMath
import com.onlygoodthings.shared.domain.GeoPoint
import com.onlygoodthings.shared.domain.PostPlacement
import kotlin.math.ln
import kotlin.math.pow

/**
 * Candidato ya hidratado. El inventario lo arma el backend o el mock;
 * acá solo se puntúa y se pagina.
 */
data class FeedCandidate(
    val postId: String,
    val authorKey: String,
    val authorUserId: String?,
    val protagonistUserId: String?,
    val topic: String,
    val createdAtEpochMs: Long,
    val impactCount: Int,
    val commentCount: Int,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val sourceUrl: String? = null,
    val achievedCount: Int = 0,
    val trustScore: Double = 0.0,
    val urgencyRank: Int = 0,
    val placement: PostPlacement = PostPlacement.ORGANIC,
    val needTagId: String? = null,
    val hashtags: List<com.onlygoodthings.shared.domain.PostHashtag> = emptyList(),
) {
    fun families(): Set<FeedTopicFamily> = feedTopicFamilies(topic,sourceUrl,hashtags)
    fun family(): FeedTopicFamily = families().firstOrNull { it != FeedTopicFamily.OTHER } ?: FeedTopicFamily.OTHER
}

data class FeedEventSignal(
    val postId: String,
    val authorUserId: String?,
    val topic: String,
    val kind: FeedEventKind,
    val hashtags: List<com.onlygoodthings.shared.domain.PostHashtag> = emptyList(),
    val occurredAtEpochMs: Long = 0L,
    val dwellMs: Int = 0,
)

data class ViewerContext(
    val userId: String,
    val followedIds: Set<String>,
    val events: List<FeedEventSignal> = emptyList(),
    val latitude: Double? = null,
    val longitude: Double? = null,
    val offeredTagIds: Set<String> = emptySet(),
)

data class RankedFeedItem(
    val postId: String,
    val score: Double,
    val discovery: Boolean,
    val reason: FeedShowReason = FeedShowReason.AFFINITY,
    val family: FeedTopicFamily = FeedTopicFamily.OTHER,
    val createdAtEpochMs: Long = 0L,
)

/**
 * HOME learns topic/author affinity; FOLLOWING remains strictly chronological.
 * Moderation, graph eligibility and hidden posts are applied before personalization.
 */
object FeedRanker {
    const val MAX_SAME_AUTHOR_STREAK = 3
    const val RECENCY_HALFLIFE_HOURS = 36.0

    private const val W_GRAPH = 2.4
    private const val W_CLAP = 1.2
    private const val W_COMMENT = 1.2
    private const val W_PROFILE = 1.0
    private const val W_SHARE = 1.0
    private const val W_RECENCY = 1.2
    private const val W_TOPIC = 0.8
    private const val W_PROOF = 0.3
    private const val W_GEO = 0.9
    private const val W_ACHIEVED = 3.2
    private const val W_TRUST = 1.6
    private const val W_URGENCY = 1.4
    private const val SKIP_PENALTY = 0.35
    private const val HIDE_PENALTY = 8.0

    private val PositiveKinds = setOf(
        FeedEventKind.CLAP,
        FeedEventKind.HEART,
        FeedEventKind.COMMENT,
        FeedEventKind.PROFILE_TAP,
        FeedEventKind.SHARE,
        FeedEventKind.DWELL,
    )

    fun rank(
        candidates: List<FeedCandidate>,
        viewer: ViewerContext,
        mode: FeedMode,
        nowEpochMs: Long,
        offset: Int = 0,
        limit: Int = 20,
        family: FeedTopicFamily? = null,
    ): List<RankedFeedItem> = rank(
        candidates,
        viewer,
        FeedDecisionQuery(mode, family),
        nowEpochMs,
        offset,
        limit,
    )

    fun rank(
        candidates: List<FeedCandidate>,
        viewer: ViewerContext,
        query: FeedDecisionQuery,
        nowEpochMs: Long,
        offset: Int = 0,
        limit: Int = 20,
    ): List<RankedFeedItem> {
        val profile = FeedInterestProfile.from(viewer.events)
        val hiddenPosts = viewer.events.filter { it.kind == FeedEventKind.HIDE }.map { it.postId }.toSet()
        var visible = candidates.filter { it.postId !in hiddenPosts && it.placement != PostPlacement.AD }
        if (query.family != null) {
            visible = visible.filter { query.family in it.families() }
        }
        if (query.mode == FeedMode.FOLLOWING) {
            visible = visible.filter { inGraph(it, viewer) }
        }
        val interests = HashtagInterests(viewer.events,nowEpochMs)
        val personalized = query.mode == FeedMode.HOME && interests.hasEvidence
        val items = visible.map { post ->
            val fromGraph = inGraph(post,viewer)
            val canHelp = query.mode==FeedMode.HOME && skillMatch(post,viewer) && fromGraph
            val affinity = interests.topic(post)
            val value = if(personalized) {
                5.0*affinity + 0.6*interests.author(post) + 2.0*recency(post.createdAtEpochMs,nowEpochMs) +
                    (if(canHelp)8.0 else 0.0) + (if(fromGraph)0.15 else 0.0)
            } else post.createdAtEpochMs.toDouble()
            RankedFeedItem(post.postId,value,query.mode==FeedMode.HOME && !fromGraph,
                reason=when {
                    post.placement==PostPlacement.PROMOTED -> FeedShowReason.PROMOTED
                    canHelp -> FeedShowReason.SKILL_MATCH
                    query.family!=null -> FeedShowReason.FILTER
                    query.mode==FeedMode.FOLLOWING || fromGraph -> FeedShowReason.GRAPH
                    personalized && affinity>0 -> FeedShowReason.AFFINITY
                    personalized -> FeedShowReason.EXPLORE
                    else -> reasonFor(query,profile,post,fromGraph)
                },family=post.family(),createdAtEpochMs=post.createdAtEpochMs) to post
        }.sortedWith(compareByDescending<Pair<RankedFeedItem,FeedCandidate>> {
            query.mode==FeedMode.HOME && skillMatch(it.second,viewer) && inGraph(it.second,viewer)
        }.thenByDescending { it.first.score }.thenByDescending { it.second.createdAtEpochMs }.thenBy { it.first.postId })
        val ordered = if(personalized) {
            val waiting=items.toMutableList()
            val result=mutableListOf<Pair<RankedFeedItem,FeedCandidate>>()
            while(waiting.isNotEmpty()) {
                val previous=result.takeLast(MAX_SAME_AUTHOR_STREAK).map { it.second.authorKey }
                val repeated=previous.takeIf { it.size==MAX_SAME_AUTHOR_STREAK && it.distinct().size==1 }?.first()
                val explore=(result.size+1)%5==0
                val pick=waiting.indexOfFirst { (_,post) ->
                    (repeated==null || post.authorKey!=repeated) && (!explore || interests.topic(post)<=0)
                }.takeIf { it>=0 } ?: waiting.indexOfFirst { repeated==null || it.second.authorKey!=repeated }.takeIf { it>=0 } ?: 0
                result+=waiting.removeAt(pick)
            }
            result
        } else items
        return ordered.drop(offset.coerceAtLeast(0)).take(limit.coerceIn(0,200)).map { it.first }
    }

    internal fun score(
        post: FeedCandidate,
        viewer: ViewerContext,
        nowEpochMs: Long,
        fromGraph: Boolean,
    ): Double = score(post, viewer, FeedInterestProfile.from(viewer.events), nowEpochMs, fromGraph)

    internal fun score(
        post: FeedCandidate,
        viewer: ViewerContext,
        profile: FeedInterestProfile,
        nowEpochMs: Long,
        fromGraph: Boolean,
    ): Double {
        val topicP = topicAffinity(viewer.events, post.topic, profile, post.family())
        val personP = maxOf(
            personAffinity(viewer.events, post.authorUserId),
            personAffinity(viewer.events, post.protagonistUserId),
        )
        val proof = socialProof(post.impactCount, post.commentCount)
        val pClap = (0.55 * personP + 0.45 * topicP).coerceIn(0.0, 1.0)
        val pComment = (0.40 * personP + 0.60 * topicP).coerceIn(0.0, 1.0)
        val pProfile = personP
        val pShare = (0.50 * topicP + 0.50 * proof).coerceIn(0.0, 1.0)
        if (post.placement != PostPlacement.ORGANIC) {
            return W_RECENCY * recency(post.createdAtEpochMs, nowEpochMs) * 0.45
        }
        val skipCount = viewer.events.count { it.postId == post.postId && it.kind == FeedEventKind.SKIP }
        val hide = if (viewer.events.any { it.postId == post.postId && it.kind == FeedEventKind.HIDE }) HIDE_PENALTY else 0.0
        return (if (fromGraph) W_GRAPH else 0.0) +
            W_CLAP * pClap +
            W_COMMENT * pComment +
            W_PROFILE * pProfile +
            W_SHARE * pShare +
            W_RECENCY * recency(post.createdAtEpochMs, nowEpochMs) +
            W_TOPIC * topicP +
            W_PROOF * proof +
            W_GEO * geoBoost(viewer, post) +
            W_ACHIEVED * achieved(post.achievedCount) +
            W_TRUST * post.trustScore.coerceIn(0.0, 1.0) +
            W_URGENCY * (post.urgencyRank.coerceIn(0, 3) / 3.0) -
            SKIP_PENALTY * skipCount -
            hide
    }

    private fun skillMatch(post: FeedCandidate, viewer: ViewerContext): Boolean {
        val tag = post.needTagId ?: return false
        return tag in viewer.offeredTagIds
    }

    private fun inGraph(post: FeedCandidate, viewer: ViewerContext): Boolean {
        val mine = setOfNotNull(post.authorUserId, post.protagonistUserId)
        return viewer.userId in mine || mine.any { it in viewer.followedIds }
    }

    private fun topicAffinity(
        events: List<FeedEventSignal>,
        topic: String,
        profile: FeedInterestProfile,
        family: FeedTopicFamily,
    ): Double {
        val familyW = if (profile.weights.isEmpty()) 0.20 else profile.weight(family)
        if (topic.isBlank()) return familyW.coerceIn(0.05, 1.0)
        val relevant = events.filter { it.kind in PositiveKinds }
        if (relevant.isEmpty()) return (0.55 * familyW + 0.45 * 0.20).coerceIn(0.05, 1.0)
        val exact = relevant.count { it.topic.equals(topic, ignoreCase = true) }.toDouble() / relevant.size
        return (0.55 * familyW + 0.45 * exact).coerceIn(0.0, 1.0)
    }

    private fun reasonFor(
        query: FeedDecisionQuery,
        profile: FeedInterestProfile,
        post: FeedCandidate,
        fromGraph: Boolean,
    ): FeedShowReason {
        if (post.placement == PostPlacement.PROMOTED) return FeedShowReason.PROMOTED
        if (query.family != null) return FeedShowReason.FILTER
        if (fromGraph) return FeedShowReason.GRAPH
        if (profile.chosen.isNotEmpty() && !profile.isChosen(post.family())) return FeedShowReason.EXPLORE
        return FeedShowReason.AFFINITY
    }

    private data class Scored(
        val item: RankedFeedItem,
        val authorKey: String,
        val family: FeedTopicFamily,
        val createdAtEpochMs: Long,
    )

    private fun personAffinity(events: List<FeedEventSignal>, personId: String?): Double {
        if (personId.isNullOrBlank()) return 0.0
        val withPerson = events.filter { it.authorUserId == personId }
        if (withPerson.isEmpty()) return 0.0
        val positive = withPerson.count { it.kind in PositiveKinds }
        return (positive.toDouble() / withPerson.size).coerceIn(0.0, 1.0)
    }

    private fun recency(createdAtEpochMs: Long, nowEpochMs: Long): Double {
        val ageHours = ((nowEpochMs - createdAtEpochMs).coerceAtLeast(0L)) / 3_600_000.0
        return 2.0.pow(-ageHours / RECENCY_HALFLIFE_HOURS)
    }

    private fun achieved(count: Int): Double =
        ln(1.0 + count.coerceAtLeast(0)) / ln(1.0 + 10.0)

    private fun socialProof(impact: Int, comments: Int): Double {
        val impactN = ln(1.0 + impact.coerceAtLeast(0)) / ln(1.0 + 250.0)
        val commentN = ln(1.0 + comments.coerceAtLeast(0)) / ln(1.0 + 40.0)
        return (0.7 * impactN + 0.3 * commentN).coerceIn(0.0, 1.0)
    }

    private fun geoBoost(viewer: ViewerContext, post: FeedCandidate): Double {
        val vLat = viewer.latitude ?: return 0.0
        val vLng = viewer.longitude ?: return 0.0
        val pLat = post.latitude ?: return 0.0
        val pLng = post.longitude ?: return 0.0
        val km = GeoMath.haversineMeters(GeoPoint(vLat, vLng), GeoPoint(pLat, pLng)) / 1000.0
        return when {
            km <= 5.0 -> 1.0
            km <= 50.0 -> 0.5
            km <= 400.0 -> 0.2
            else -> 0.0
        }
    }

    private fun diversify(scored: List<Pair<RankedFeedItem, String>>): List<RankedFeedItem> {
        val out = mutableListOf<RankedFeedItem>()
        val waiting = scored.toMutableList()
        while (waiting.isNotEmpty()) {
            val lastKeys = out.takeLast(MAX_SAME_AUTHOR_STREAK).map { item ->
                scored.first { it.first.postId == item.postId }.second
            }
            val chosen = if (lastKeys.size == MAX_SAME_AUTHOR_STREAK && lastKeys.distinct().size == 1) {
                val same = lastKeys.first()
                val alt = waiting.indexOfFirst { it.second != same }
                if (alt >= 0) waiting.removeAt(alt) else waiting.removeAt(0)
            } else {
                waiting.removeAt(0)
            }
            out += chosen.first
        }
        return out
    }
}
