package com.onlygoodthings.app.platform

import androidx.compose.runtime.Composable

object VideoUploadRules {
    const val MAX_BYTES=8L*1024*1024
    const val MIN_CLIP_MS=250L
    /** Leave space for AAC audio and container overhead rather than truncate the selected clip. */
    fun videoBitrate(durationMs:Long):Int {
        require(durationMs>0)
        return ((MAX_BYTES*8*850/durationMs)-96_000).coerceIn(100_000,2_000_000).toInt()
    }
}

data class VideoTrimRange(val startMs:Long,val endMs:Long) {
    val durationMs:Long get()=endMs-startMs
    fun valid(sourceDurationMs:Long,maxDurationMs:Long?):Boolean =
        startMs>=0 && endMs<=sourceDurationMs && durationMs>=minOf(VideoUploadRules.MIN_CLIP_MS,sourceDurationMs,maxDurationMs ?: sourceDurationMs) &&
            (maxDurationMs==null || durationMs<=maxDurationMs)
}

class OgtVideoEditor(val edit:suspend (OgtPickedMedia,Long?)->OgtPickedMedia?)

@Composable
expect fun rememberOgtVideoEditor():OgtVideoEditor

/** Moving either handle preserves a valid range; the other follows at the duration ceiling. */
fun constrainVideoTrim(old:VideoTrimRange,startMs:Long,endMs:Long,sourceMs:Long,maxMs:Long?):VideoTrimRange {
    require(sourceMs>0 && (maxMs==null || maxMs>0))
    val minimum=minOf(VideoUploadRules.MIN_CLIP_MS,sourceMs,maxMs ?: sourceMs)
    val maximum=minOf(sourceMs,maxMs ?: sourceMs).coerceAtLeast(minimum)
    var start=startMs.coerceIn(0,sourceMs-minimum)
    var end=endMs.coerceIn(start+minimum,sourceMs)
    if(end-start>maximum) {
        if(startMs!=old.startMs) end=start+maximum
        else start=end-maximum
    }
    return VideoTrimRange(start,end)
}
