package com.onlygoodthings.shared.domain

import kotlin.test.*
import kotlinx.serialization.json.Json

class StoryFrameTest {
    @Test fun fillsLandscapeWithoutStretchingOrEmptyMargins() {
        val g=storyFrameLayout(360.0,640.0,1920.0,1080.0,StoryFrame())
        assertEquals(640.0,g.height,.001);assertTrue(g.width>360);assertEquals(1920.0/1080,g.width/g.height,.0001)
        assertEquals((360-g.width)/2,g.left,.001);assertEquals(0.0,g.top,.001)
    }
    @Test fun completeAndLegacyModesPreserveTheWholeImage() {
        val g=storyFrameLayout(360.0,640.0,1920.0,1080.0,null)
        assertEquals(360.0,g.width,.001);assertTrue(g.height<640);assertEquals(0.0,g.left,.001)
        assertEquals(g,storyFrameLayout(360.0,640.0,1920.0,1080.0,StoryFrame(StoryFrameMode.FIT)))
    }
    @Test fun normalizedFrameIsIdenticalAtWebAndMobileSizes() {
        val f=StoryFrame(StoryFrameMode.FILL,2.3,-.7,.6)
        val a=storyFrameLayout(360.0,640.0,1080.0,1920.0,f)
        val b=storyFrameLayout(720.0,1280.0,1080.0,1920.0,f)
        listOf(a.width to b.width,a.height to b.height,a.left to b.left,a.top to b.top).forEach { (a,b)->assertEquals(a*2,b,.0001) }
    }
    @Test fun draggingToEitherExtremeNeverExposesEmptyPixelsInFillMode() {
        for(x in listOf(-1.0,0.0,1.0)) for(y in listOf(-1.0,0.0,1.0)) for(zoom in listOf(1.0,6.0)) {
            val g=storyFrameLayout(360.0,640.0,500.0,1500.0,StoryFrame(zoom=zoom,x=x,y=y))
            assertTrue(g.left<=.0001 && g.top<=.0001);assertTrue(g.left+g.width>=359.999 && g.top+g.height>=639.999)
        }
    }
    @Test fun invalidValuesAreRejectedAndSerializationPreservesTheCrop() {
        listOf(Double.NaN,Double.POSITIVE_INFINITY,0.0,6.1).forEach { assertFailsWith<IllegalArgumentException> { StoryFrame(zoom=it) } }
        assertFailsWith<IllegalArgumentException> { StoryFrame(x=1.01) }
        assertFailsWith<IllegalArgumentException> { StoryFrame(y=Double.NaN) }
        val frame=StoryFrame(StoryFrameMode.FILL,2.0,-.3,.9)
        assertEquals(frame,Json.decodeFromString<StoryFrame>(Json.encodeToString(StoryFrame.serializer(),frame)))
        val legacy=Json.decodeFromString<StoryDraft>("""{"id":"old","ownerId":"a","path":"/old.png","kind":"IMAGE"}""")
        assertNull(legacy.frame)
    }
}
