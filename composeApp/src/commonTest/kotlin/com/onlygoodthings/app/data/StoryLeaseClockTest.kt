package com.onlygoodthings.app.data

import kotlin.test.*

class StoryLeaseClockTest {
    private var tick=0L
    private var wall=1_000L
    private val clock=StoryLeaseClock({tick},{wall})

    @Test fun verificationExpiresAtThirtySecondsWithoutAnObservableTicker() {
        assertFalse(clock.fresh)
        clock.verify(100_000)
        val row=playbackStory("a",expires=200_000)
        assertEquals(30_000L,clock.nextInvalidation(listOf(row)))
        tick=29_999
        assertTrue(clock.fresh)
        assertEquals(1L,clock.nextInvalidation(listOf(row)))
        tick=30_000
        assertFalse(clock.fresh)
        assertNull(clock.nextInvalidation(listOf(row)))
    }
    @Test fun nextWakeupIsTheEarliestStoryExpiryOrPrivacyDeadline() {
        clock.verify(100_000)
        val early=playbackStory("a",expires=105_000)
        val late=playbackStory("b",expires=180_000)
        assertEquals(5_000L,clock.nextInvalidation(listOf(late,early)))
        tick=5_000
        assertEquals(listOf(late),com.onlygoodthings.shared.domain.activeStories(listOf(early,late),clock.now))
        assertEquals(25_000L,clock.nextInvalidation(listOf(late)))
        assertNull(clock.nextInvalidation(emptyList()))
    }
    @Test fun deviceClockMovingBackCannotExtendVisibilityAndSleepStillExpiresLease() {
        clock.verify(100_000)
        tick=10_000;wall=-90_000
        assertEquals(110_000L,clock.now)
        tick=30_000
        assertFalse(clock.fresh)
        tick=0;wall=1_000;clock.verify(200_000)
        wall+=31_000 // Wall time also bounds visibility while a monotonic clock is suspended.
        assertFalse(clock.fresh)
        assertEquals(231_000L,clock.now)
    }
    @Test fun revalidationRenewsLeaseAndAccountClearCannotReuseIt() {
        clock.verify(100_000);tick=20_000;clock.verify(120_000);tick=35_000
        assertTrue(clock.fresh)
        assertEquals(135_000L,clock.now)
        clock.clear()
        assertFalse(clock.fresh)
        assertNull(clock.nextInvalidation(listOf(playbackStory("a"))))
    }
    @Test fun networkRetryBacksOffWithinABoundedDelay() {
        assertEquals(listOf(5_000L,10_000L,20_000L,40_000L,40_000L),(0..4).map(::storyRetryDelay))
        assertEquals(40_000L,storyRetryDelay(Int.MAX_VALUE))
    }
}
