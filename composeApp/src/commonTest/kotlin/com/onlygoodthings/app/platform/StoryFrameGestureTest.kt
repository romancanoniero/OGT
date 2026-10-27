package com.onlygoodthings.app.platform

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.IntSize
import com.onlygoodthings.app.ui.components.transformStoryFrame
import com.onlygoodthings.app.ui.components.storyGestureMotion
import androidx.compose.ui.input.pointer.*
import com.onlygoodthings.shared.domain.*
import kotlin.test.*

class StoryFrameGestureTest {
    private val viewport=IntSize(360,640)
    private val media=IntSize(1080,1920)
    private fun layout(frame:StoryFrame)=storyFrameLayout(360.0,640.0,1080.0,1920.0,frame)

    @Test fun pinchKeepsTheSourcePointUnderTheFingers() {
        val old=StoryFrame();val point=Offset(120f,200f)
        val next=transformStoryFrame(old,viewport,media,point,Offset.Zero,2f)
        val a=layout(old);val b=layout(next)
        assertEquals((point.x-a.left)/a.width,(point.x-b.left)/b.width,0.00001)
        assertEquals((point.y-a.top)/a.height,(point.y-b.top)/b.height,0.00001)
        assertEquals(2.0,next.zoom)
    }
    @Test fun successivePanEventsAccumulateAndStayWithinTheCanvas() {
        var frame=StoryFrame(zoom=2.0)
        repeat(20) { frame=transformStoryFrame(frame,viewport,media,Offset(180f,320f),Offset(2f,0f),1f) }
        assertEquals(-140.0,layout(frame).left,0.0001)
        assertEquals(0.0,frame.y)
        frame=transformStoryFrame(frame,viewport,media,Offset.Zero,Offset(10000f,-10000f),1f)
        val g=layout(frame)
        assertEquals(1.0,frame.x);assertEquals(-1.0,frame.y)
        assertTrue(g.left<=0 && g.top<=0 && g.left+g.width>=360 && g.top+g.height>=640)
    }
    @Test fun verticalVideoFillsTheWholeWidthWithoutChangingTheSharedFrame() {
        val tall=IntSize(1080,2340)
        val initial=StoryFrame()
        val g=storyFrameLayout(360.0,640.0,1080.0,2340.0,initial)
        assertEquals(360.0,g.width,0.0001);assertEquals(0.0,g.left,0.0001)
        assertTrue(g.height>640)
        val next=transformStoryFrame(initial,viewport,tall,Offset(180f,320f),Offset(0f,30f),1f)
        assertTrue(next.y>0);assertEquals(StoryFrameMode.FILL,next.mode)
    }
    @Test fun limitsAndInvalidInputNeverProduceAnInvalidDraft() {
        val old=StoryFrame(zoom=2.0)
        assertEquals(6.0,transformStoryFrame(old,viewport,media,Offset.Zero,Offset.Zero,100f).zoom)
        assertEquals(1.0,transformStoryFrame(old,viewport,media,Offset.Zero,Offset.Zero,.01f).zoom)
        assertEquals(old,transformStoryFrame(old,IntSize.Zero,media,Offset.Zero,Offset.Zero,2f))
        assertEquals(old,transformStoryFrame(old,viewport,media,Offset(Float.NaN,0f),Offset.Zero,2f))
    }
    private fun released(id:Long,before:Offset,after:Offset)=PointerInputChange(
        id=PointerId(id),uptimeMillis=10,position=after,pressed=false,
        previousUptimeMillis=0,previousPosition=before,previousPressed=true,isInitiallyConsumed=false)

    @Test fun theFinalCoalescedDragIsNotLostWhenTheFingerLifts() {
        val motion=storyGestureMotion(PointerEvent(listOf(released(1,Offset(100f,100f),Offset(160f,130f)))))
        assertEquals(Offset(60f,30f),motion.pan);assertEquals(1f,motion.zoom)
        val frame=transformStoryFrame(StoryFrame(zoom=2.0),viewport,media,motion.centroid,motion.pan,motion.zoom)
        assertTrue(frame.x>0);assertTrue(frame.y>0)
    }
    @Test fun aCoalescedTwoFingerPinchPreservesItsFinalScale() {
        val motion=storyGestureMotion(PointerEvent(listOf(
            released(1,Offset(100f,100f),Offset(50f,100f)),released(2,Offset(200f,100f),Offset(250f,100f)))))
        assertEquals(2f,motion.zoom);assertEquals(Offset.Zero,motion.pan)
    }
    @Test fun aLegacyCompleteVideoCanBeEnlargedToCoverTheSides() {
        val old=StoryFrame(StoryFrameMode.FIT)
        val next=transformStoryFrame(old,viewport,IntSize(1080,2340),Offset(180f,320f),Offset.Zero,1.3f)
        val g=storyFrameLayout(360.0,640.0,1080.0,2340.0,next)
        assertTrue(g.width>=360 && g.height>=640);assertTrue(g.left<=0 && g.top<=0)
    }

}
