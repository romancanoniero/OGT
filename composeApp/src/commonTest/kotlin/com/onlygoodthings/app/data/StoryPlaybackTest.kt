package com.onlygoodthings.app.data

import com.onlygoodthings.shared.domain.*
import kotlin.test.*

internal fun playbackStory(id:String,author:String="a",kind:MediaKind=MediaKind.IMAGE,duration:Long=5000,created:Long=1,expires:Long=Long.MAX_VALUE)=
    Story(id,author,author,caption="",audience=StoryAudience.COMMUNITY,kind=kind,mediaUrl="https://example.test/$id",durationMs=duration,createdAtEpochMs=created,expiresAtEpochMs=expires)
class StoryPlaybackTest {
 @Test fun oneBarCombinesVideoTimesAndFiveSecondsPerPhotoInChronologicalOrder() {
    val a=playbackStory("1",created=1);val b=playbackStory("2",kind=MediaKind.VIDEO,duration=12_000,created=2);val c=playbackStory("3",created=3)
    val queue=listOf(c,playbackStory("other",author="b",duration=99_999),b,a)
    val progress=storyBookProgress(queue,b,6000)
    assertEquals(22_000,progress.totalMs);assertEquals(11_000,progress.elapsedMs);assertEquals(.5f,progress.fraction);assertEquals(2,progress.page);assertEquals(3,progress.pages)
    assertEquals(1f,storyBookProgress(queue,c,9000).fraction)
    assertEquals(0f,storyBookProgress(queue,a,-20).fraction)
 }
 @Test fun timeRestartsForTheNextAuthorAndPhotosKeepTheServerRule() {
    val a=playbackStory("1",kind=MediaKind.VIDEO,duration=10_000);val b=playbackStory("2",author="b",duration=123)
    val p=storyBookProgress(listOf(a,b),b,0)
    assertEquals(5000,p.totalMs);assertEquals(0f,p.fraction);assertEquals(1,p.pages)
 }
 @Test fun cacheEvictsExpiredRevokedAndLeastRecentlyUsedEntriesWithinBudget() {
    fun entry(id:String,bytes:Int,used:Long,expires:Long=9999)=CachedStoryMedia(id,id,expires,bytes,used,id)
    val old=entry("old",4,1);val recent=entry("recent",7,3);val small=entry("small",2,2)
    val keep=retainedStoryMedia(listOf(old,recent,small,entry("expired",1,9,100),entry("revoked",1,10)),setOf("old","recent","small","expired"),100,10)
    assertEquals(listOf(recent,small),keep)
 }
}
