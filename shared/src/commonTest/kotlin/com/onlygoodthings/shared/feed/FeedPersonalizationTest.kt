package com.onlygoodthings.shared.feed

import com.onlygoodthings.shared.domain.*
import kotlin.test.*

class FeedPersonalizationTest {
    private val now=1_800_000_000_000L
    private val astronomy=Hashtags.free("#Astronomía")
    private val cooking=Hashtags.free("#Cocina")
    private fun post(id:String,tag:PostHashtag,hours:Int=1,author:String=id)=FeedCandidate(id,author,author,null,tag.label,now-hours*3_600_000L,0,0,hashtags=listOf(tag))
    private fun likes(tag:PostHashtag)= (1..6).map { FeedEventSignal("seen-$it","someone",tag.label,FeedEventKind.CLAP,listOf(tag),now) }
    private fun rank(events:List<FeedEventSignal>,mode:FeedMode=FeedMode.HOME)=FeedRanker.rank(
        listOf(post("stars",astronomy,20),post("food",cooking)),ViewerContext("me",setOf("stars","food"),events),mode,now)

    @Test fun customHashtagsDifferentiateInterestsEvenWithinTheSameOtherFamily() {
        val stars=rank(likes(astronomy));val food=rank(likes(cooking))
        assertEquals(listOf("food","stars"),stars.map { it.postId })
        assertEquals(listOf("food","stars"),food.map { it.postId })
        assertTrue(stars.first { it.postId=="stars" }.score > stars.first { it.postId=="food" }.score)
        assertTrue(food.first { it.postId=="food" }.score > food.first { it.postId=="stars" }.score)
    }
    @Test fun followingIgnoresInterestsAndIsChronological() {
        assertEquals(listOf("food","stars"),rank(likes(astronomy),FeedMode.FOLLOWING).map { it.postId })
    }
    @Test fun renamedCrmHashtagKeepsItsInterestIdentity() {
        val old=PostHashtag("ciencia","Ciencia","crm-science")
        val renamed=PostHashtag("astronomia","Astronomía","crm-science")
        val ranked=FeedRanker.rank(listOf(post("stars",renamed,20),post("food",cooking)),ViewerContext("me",emptySet(),likes(old)),FeedMode.HOME,now)
        assertEquals("food",ranked.first().postId)
        assertTrue(ranked.first { it.postId=="stars" }.score > ranked.first { it.postId=="food" }.score)
    }
    @Test fun aFreeHashtagCanBecomeOfficialWithoutLosingItsAudience() {
        val official=astronomy.copy(categoryId="crm-science")
        val ranked=FeedRanker.rank(listOf(post("stars",official,20),post("food",cooking)),ViewerContext("me",emptySet(),likes(astronomy)),FeedMode.HOME,now)
        assertEquals("food",ranked.first().postId)
        assertTrue(ranked.first { it.postId=="stars" }.score > ranked.first { it.postId=="food" }.score)
    }
    @Test fun repeatedEventsForOnePostDoNotInflatePreference() {
        assertEquals(rank(likes(astronomy)),rank(likes(astronomy).flatMap { event -> List(50) { event } }))
    }
    @Test fun impressionsAndUnmeasuredDwellDoNotCreateAnInterest() {
        val events=listOf(FeedEventSignal("seen","someone","Astronomía",FeedEventKind.IMPRESSION,listOf(astronomy)),FeedEventSignal("seen","someone","Astronomía",FeedEventKind.DWELL,listOf(astronomy)))
        assertEquals("food",rank(events).first().postId)
    }
    @Test fun hideExcludesThePostAndReducesAffinityWithoutHidingTheEntireTopic() {
        val events=likes(astronomy)+FeedEventSignal("stars","stars","Astronomía",FeedEventKind.HIDE,listOf(astronomy),now)
        assertTrue(rank(events).none { it.postId=="stars" })
        assertEquals("food",rank(List(4) { FeedEventSignal("hidden-$it","someone","Astronomía",FeedEventKind.HIDE,listOf(astronomy),now) }).first().postId)
    }
    @Test fun personalFeedIncludesExplorationAndLimitsRepeatedAuthors() {
        val candidates=(1..10).map { post("stars-$it",astronomy,author="same") }+post("new-topic",cooking,author="other")
        val items=FeedRanker.rank(candidates,ViewerContext("me",emptySet(),likes(astronomy)),FeedMode.HOME,now)
        assertTrue(items.take(5).any { it.postId=="new-topic" })
        assertEquals(candidates.size,items.map { it.postId }.distinct().size)
    }
    @Test fun filteringCanMatchASecondaryHashtag() {
        val candidate=post("mix",astronomy).copy(hashtags=listOf(astronomy,PostHashtag("perros","Perros")))
        assertEquals(listOf("mix"),FeedRanker.rank(listOf(candidate),ViewerContext("me",emptySet()),FeedMode.HOME,now,family=FeedTopicFamily.PETS).map { it.postId })
    }
    @Test fun interestsDecayAndStayOwnedByTheViewer() {
        val recent=rank(likes(astronomy)).first { it.postId=="stars" }.score
        val old=rank(likes(astronomy).map { it.copy(occurredAtEpochMs=now-90*86_400_000L) }).first { it.postId=="stars" }.score
        assertTrue(recent>old)
        assertEquals("food",rank(emptyList()).first().postId)
    }

    @Test fun chronologicalPaginationDoesNotSkipOrRepeatPostsWithStrongOlderInterests() {
        val candidates=(1..45).map { post("post-$it",if(it%2==0) astronomy else cooking,it) }
        val viewer=ViewerContext("me",emptySet(),likes(astronomy))
        val first=FeedRanker.rank(candidates,viewer,FeedMode.HOME,now,0,20)
        val second=FeedRanker.rank(candidates,viewer,FeedMode.HOME,now,20,20)
        assertEquals((1..40).map { "post-$it" },(first+second).map { it.postId })
        assertEquals(40,(first+second).map { it.postId }.distinct().size)
    }
}
