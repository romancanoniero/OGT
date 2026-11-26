package com.onlygoodthings.app.data

import com.onlygoodthings.shared.domain.Story

/** Plain time values never invalidate Compose. Only data or an actual deadline changes visible state. */
internal class StoryLeaseClock(private val uptime:()->Long,private val wallTime:()->Long) {
    private var verifiedAt:Long?=null
    private var serverAt=0L
    private var wallAt=0L
    private fun elapsed(at:Long)=maxOf(0,uptime()-at,wallTime()-wallAt)
    val now:Long get()=verifiedAt?.let { serverAt+elapsed(it) } ?: wallTime()
    val fresh:Boolean get()=verifiedAt?.let { elapsed(it) < LEASE_MS } ?: false
    fun verify(serverNow:Long) { serverAt=serverNow;wallAt=wallTime();verifiedAt=uptime() }
    fun clear() { verifiedAt=null }
    fun nextInvalidation(rows:List<Story>):Long? {
        val verified=verifiedAt ?: return null
        if(rows.isEmpty() || !fresh)return null
        val untilLease=LEASE_MS-elapsed(verified)
        val untilStory=(rows.minOf { it.expiresAtEpochMs }-now)
        return minOf(untilLease,untilStory).coerceAtLeast(1)
    }
    companion object { const val LEASE_MS=30_000L }
}

internal fun storyRetryDelay(failures:Int):Long = (5_000L shl failures.coerceIn(0,3)).coerceAtMost(40_000L)
