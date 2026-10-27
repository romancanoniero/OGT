package com.onlygoodthings.app.platform

import com.onlygoodthings.shared.domain.MediaKind
import kotlinx.coroutines.runBlocking
import kotlin.test.*

class VideoSoundTest {
    private val audible=OgtPickedMedia("v",MediaKind.VIDEO,"/prepared/trim.mp4",true,"/prepared/poster.jpg",true,"/original/long.mov")

    @Test fun muteKeepsTheAudibleSelectedSegmentForRestoring()=runBlocking {
        val seen=mutableListOf<String>()
        val silent=changeVideoSound(audible,true,inspect={seen+=it;PickedVideoInfo(30000,false)},removeAudio={
            assertEquals("/prepared/trim.mp4",it.path);"/prepared/silent.mp4"
        })
        assertEquals(listOf("/prepared/silent.mp4"),seen)
        assertTrue(silent.audioMuted)
        assertEquals(audible.path,silent.unmutedPath)
        assertEquals(audible.sourcePath,silent.sourcePath)
        assertEquals(audible.posterPath,silent.posterPath)
        assertEquals("/prepared/trim.mp4",audible.path)
    }
    @Test fun restoreUsesMatchingPreparedAudioRatherThanUntrimmedSource()=runBlocking {
        val muted=audible.copy(path="/prepared/silent.mp4",audioMuted=true,unmutedPath=audible.path)
        val restored=changeVideoSound(muted,false,inspect={assertEquals(audible.path,it);PickedVideoInfo(30000,true)},removeAudio={error("Must not encode")})
        assertEquals(audible,restored)
    }
    @Test fun anExportThatStillHasAudioCannotBecomeTheSelectedFile()=runBlocking {
        assertFailsWith<IllegalArgumentException> {
            changeVideoSound(audible,true,inspect={PickedVideoInfo(30000,true)},removeAudio={"/invalid.mp4"})
        }
        assertFalse(audible.audioMuted)
    }
    @Test fun unchangedSoundDoesNotStartWorkAndMissingBackupCannotRestoreOriginal()=runBlocking {
        assertEquals(audible,changeVideoSound(audible,false,inspect={error("Must not inspect")},removeAudio={error("Must not export")}))
        assertFailsWith<IllegalStateException> { changeVideoSound(audible.copy(audioMuted=true),false) }
        assertFailsWith<IllegalArgumentException> { changeVideoSound(audible.copy(preparedForUpload=false),true) }
        Unit
    }
    @Test fun durationUsesThePreparedFileWithoutLosingFractionalSeconds() {
        assertEquals("0:29,1",videoDurationLabel(29051))
        assertEquals("0:30",videoDurationLabel(30000))
        assertEquals("1:02,3",videoDurationLabel(62300))
    }
}
