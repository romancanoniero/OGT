package com.onlygoodthings.app.platform

import com.onlygoodthings.shared.domain.MediaKind
import com.onlygoodthings.shared.domain.UploadedMedia
import kotlinx.coroutines.runBlocking
import kotlin.test.*

class MediaReorderingTest {
    private fun photo(id: String) = OgtPickedMedia(id, MediaKind.IMAGE, "/cache/$id.jpg", true)

    @Test fun aMovePreservesTheSameObjectsAndCanReachBothEnds() {
        val a=photo("a");val b=photo("b");val c=photo("c")
        val rows=mutableListOf(a,b,c)
        assertTrue(movePickedMedia(rows,"c",0));assertEquals(listOf(c,a,b),rows)
        assertSame(c,rows.first())
        assertTrue(movePickedMedia(rows,"c",99));assertEquals(listOf(a,b,c),rows)
        assertFalse(movePickedMedia(rows,"missing",0));assertEquals(listOf(a,b,c),rows)
        assertFalse(movePickedMedia(rows,"c",2))
    }

    @Test fun dropCoordinatesUseVisibleNeighborsAndPreserveUnseenItems() {
        val ids=listOf("a","b","c","d","e")
        val visible=listOf(MediaDropSlot("c",50f),MediaDropSlot("d",130f))
        assertEquals(1,mediaDropIndex(ids,"a",0f,visible))
        assertEquals(3,mediaDropIndex(ids,"a",200f,visible))
        assertEquals(2,mediaDropIndex(ids,"e",0f,visible))
        assertNull(mediaDropIndex(ids,"missing",0f,visible))
        assertNull(mediaDropIndex(ids,"a",Float.NaN,visible))
        assertEquals(0,mediaDropIndex(ids,"a",10f,emptyList()))
    }

    @Test fun anItemRemovedOrAddedDuringSelectionCannotDeleteAnotherFile() {
        val a=photo("a");val b=photo("b");val c=photo("c")
        val rows=mutableListOf(a,b)
        rows.remove(a);rows.add(c)
        assertFalse(movePickedMedia(rows,"a",0));assertEquals(listOf(b,c),rows)
        assertTrue(movePickedMedia(rows,"c",0));assertEquals(listOf(c,b),rows)
    }

    @Test fun uploadingTheNewOrderRetainsPreparedVideoAudioAndPoster() = runBlocking<Unit> {
        val muted=OgtPickedMedia("video",MediaKind.VIDEO,"/cache/silent.mp4",true,"/cache/poster.jpg",true,
            sourcePath="/original.mov",audioMuted=true,unmutedPath="/cache/audible.mp4")
        val rows=mutableListOf(photo("a"),muted,photo("c"))
        movePickedMedia(rows,"c",0)
        val reads=mutableListOf<String>()
        val result=uploadPostMedia(rows,"Post",read={reads+=it;byteArrayOf(1)},upload={name,_->
            UploadedMedia(name,if(name.endsWith("mp4"))MediaKind.VIDEO else MediaKind.IMAGE,"https://media.test/$name",name)
        })
        assertEquals(listOf("/cache/c.jpg","/cache/a.jpg","/cache/silent.mp4","/cache/poster.jpg"),reads)
        assertEquals(listOf(0,1,2),result.map{it.sortOrder})
        assertTrue(rows[2].audioMuted);assertSame(muted,rows[2])
        assertEquals("https://media.test/poster.jpg",result.last().posterUrl)
    }
}
