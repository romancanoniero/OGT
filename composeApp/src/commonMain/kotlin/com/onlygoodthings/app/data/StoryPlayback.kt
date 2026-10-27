package com.onlygoodthings.app.data

import com.onlygoodthings.shared.domain.*

/** One book per author. Media time (not download or page-turn time) advances its timeline. */
internal fun storyPageDuration(story:Story):Long =
    if(story.kind==MediaKind.IMAGE) StoryRules.PHOTO_MS else story.durationMs.coerceIn(1,StoryRules.MAX_VIDEO_MS)

internal data class StoryBookProgress(val elapsedMs:Long,val totalMs:Long,val page:Int,val pages:Int) {
    val fraction:Float get()=(elapsedMs.toDouble()/totalMs.coerceAtLeast(1)).coerceIn(0.0,1.0).toFloat()
}
internal fun storyBookProgress(queue:List<Story>,current:Story,elapsed:Long):StoryBookProgress {
    val pages=queue.filter { it.authorId==current.authorId }.sortedBy { it.createdAtEpochMs }
    val index=pages.indexOfFirst { it.id==current.id }.coerceAtLeast(0)
    return StoryBookProgress(pages.take(index).sumOf(::storyPageDuration)+elapsed.coerceIn(0,storyPageDuration(current)),
        pages.sumOf(::storyPageDuration),index+1,pages.size)
}
