package com.onlygoodthings.app.platform

import kotlin.test.*

class VideoPreparationWatchdogTest {
    @Test fun unknownProgressCannotWaitForever() {
        val monitor=VideoPreparationWatchdog(0)
        monitor.observe("Leyendo",-1,0)
        assertNull(monitor.timeoutMessage(89_999))
        assertNotNull(monitor.timeoutMessage(90_000))
    }
    @Test fun realProgressAllowsPreparationLongerThanTwoMinutes() {
        val monitor=VideoPreparationWatchdog(0)
        monitor.observe("Comprimiendo",0,0)
        monitor.observe("Comprimiendo",200,60_000)
        monitor.observe("Comprimiendo",400,120_000)
        assertNull(monitor.timeoutMessage(180_000))
    }
    @Test fun repeatedOrRegressingValuesDoNotResetStallClock() {
        val monitor=VideoPreparationWatchdog(0)
        monitor.observe("Comprimiendo",300,0)
        monitor.observe("Comprimiendo",300,50_000)
        monitor.observe("Comprimiendo",200,80_000)
        assertNotNull(monitor.timeoutMessage(90_000))
    }
    @Test fun changingPhaseOrAttemptRepresentsActualNewWork() {
        val monitor=VideoPreparationWatchdog(0)
        monitor.observe("Intento 1",800,0)
        monitor.observe("Intento 2",0,80_000)
        assertNull(monitor.timeoutMessage(100_000))
        monitor.observe("Comprobando",-1,160_000)
        assertNull(monitor.timeoutMessage(200_000))
    }
    @Test fun overallLimitRemainsBoundedAcrossRetries() {
        val monitor=VideoPreparationWatchdog(0)
        for(time in 0L..600_000 step 30_000) monitor.observe("Intento $time",0,time)
        assertNotNull(monitor.timeoutMessage(600_000))
    }
    @Test fun backgroundSuspensionDoesNotBecomeAFalseStall() {
        val monitor=VideoPreparationWatchdog(0)
        monitor.observe("Comprimiendo",100,0)
        monitor.setPaused(true,30_000)
        assertNull(monitor.timeoutMessage(700_000))
        monitor.setPaused(false,700_000)
        assertNull(monitor.timeoutMessage(760_000))
        assertNotNull(monitor.timeoutMessage(790_000))
    }
}
