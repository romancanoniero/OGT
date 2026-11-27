package com.onlygoodthings.shared.feed

import com.onlygoodthings.shared.domain.*
import kotlin.test.*

class DiffusionFeedTest {
    private val viewer=ViewerContext("viewer",setOf("friend"))
    private fun candidate(id:String,time:Long)=FeedCandidate(id,"author-$id","author-$id",null,"Comunidad",time,0,0)
    @Test fun followedDiffusionAdmitsTheOriginalAndOrdersByItsNewActivity() {
        val old=candidate("old",1).copy(diffusedBy=listOf(PostDiffusion("friend","Amiga",100)))
        val recent=candidate("recent",50).copy(authorUserId="friend")
        val result=FeedRanker.rank(listOf(recent,old),viewer,FeedMode.FOLLOWING,101)
        assertEquals(listOf("old","recent"),result.map { it.postId })
        assertEquals(1,result.first().createdAtEpochMs) // The original publication date and author remain intact.
        assertEquals(FeedShowReason.GRAPH,result.first().reason)
        assertFalse(result.first().discovery)
    }
    @Test fun unrelatedDiffusionsCannotEnterFollowingAndHiddenOriginalsStayHidden() {
        val old=candidate("old",1).copy(diffusedBy=listOf(PostDiffusion("stranger","Otra",100)))
        assertTrue(FeedRanker.rank(listOf(old),viewer,FeedMode.FOLLOWING,101).isEmpty())
        val followed=old.copy(diffusedBy=listOf(PostDiffusion("friend","Amiga",100)))
        val hidden=viewer.copy(events=listOf(FeedEventSignal("old","author-old","Comunidad",FeedEventKind.HIDE)))
        assertTrue(FeedRanker.rank(listOf(followed),hidden,FeedMode.FOLLOWING,101).isEmpty())
        assertTrue(FeedRanker.rank(listOf(followed.copy(placement=PostPlacement.AD)),viewer,FeedMode.FOLLOWING,101).isEmpty())
    }
}
