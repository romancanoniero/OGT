package com.onlygoodthings.app.platform
import com.onlygoodthings.shared.domain.StoryRules
import kotlin.test.*
class VideoTrimTest {
    @Test fun storyAndPublicationByteLimitsStayConsistent() { assertEquals(StoryRules.MAX_BYTES.toLong(),VideoUploadRules.MAX_BYTES) }
    @Test fun draggingEndMovesStartAtTheCeiling() {
        assertEquals(VideoTrimRange(20_000,50_000),constrainVideoTrim(VideoTrimRange(0,30_000),0,50_000,90_000,30_000))
    }
    @Test fun draggingStartMovesEndAtTheCeiling() {
        assertEquals(VideoTrimRange(10_000,40_000),constrainVideoTrim(VideoTrimRange(20_000,50_000),10_000,50_000,90_000,30_000))
    }
    @Test fun everyHandlePositionRemainsValidAndOriginalDurationIsNotChanged() {
        for(start in -1000L..65000L step 1000) for(end in -1000L..65000L step 1000) {
            val clipped=constrainVideoTrim(VideoTrimRange(0,30_000),start,end,60_000,30_000)
            assertTrue(clipped.valid(60_000,30_000),"$start, $end => $clipped")
        }
    }
    @Test fun shortVideosAndUnlimitedPostDurationRemainSupported() {
        assertEquals(VideoTrimRange(0,100),constrainVideoTrim(VideoTrimRange(0,100),0,100,100,30_000))
        assertTrue(constrainVideoTrim(VideoTrimRange(0,90_000),0,90_000,90_000,null).valid(90_000,null))
        assertFalse(VideoTrimRange(0,30_001).valid(90_000,30_000))
    }
    @Test fun compressionReservesSpaceForAudioAndContainer() {
        for(ms in listOf(1000L,10000L,30000L)) {
            val bps=VideoUploadRules.videoBitrate(ms)
            assertTrue((bps+96_000L)*ms/8000<VideoUploadRules.MAX_BYTES)
        }
    }
}
