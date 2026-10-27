package com.onlygoodthings.app.ui.components

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculateCentroidSize
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEvent
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntSize
import com.onlygoodthings.shared.domain.StoryFrame
import com.onlygoodthings.shared.domain.storyFrameLayout
import kotlin.math.abs
import kotlin.math.max

/** Preserve the source point under the fingers, and clamp movement to real overflow. */
internal fun transformStoryFrame(old:StoryFrame, viewport:IntSize, media:IntSize, centroid:Offset, pan:Offset, zoom:Float):StoryFrame {
    if(viewport.width<=0 || viewport.height<=0 || media.width<=0 || media.height<=0 ||
        !centroid.x.isFinite() || !centroid.y.isFinite() || !pan.x.isFinite() || !pan.y.isFinite() || !zoom.isFinite() || zoom<=0) return old
    val before=storyFrameLayout(viewport.width.toDouble(),viewport.height.toDouble(),media.width.toDouble(),media.height.toDouble(),old)
    val z=(old.zoom*zoom).coerceIn(1.0,6.0)
    val after=storyFrameLayout(viewport.width.toDouble(),viewport.height.toDouble(),media.width.toDouble(),media.height.toDouble(),old.copy(zoom=z,x=0.0,y=0.0))
    val ratio=z/old.zoom
    val left=centroid.x+(before.left-centroid.x)*ratio+pan.x
    val top=centroid.y+(before.top-centroid.y)*ratio+pan.y
    val dx=max(0.0,(after.width-viewport.width)/2);val dy=max(0.0,(after.height-viewport.height)/2)
    return old.copy(zoom=z,x=if(dx>0) ((left-after.left)/dx).coerceIn(-1.0,1.0) else 0.0,
        y=if(dy>0) ((top-after.top)/dy).coerceIn(-1.0,1.0) else 0.0)
}

internal data class StoryGestureMotion(val centroid:Offset,val pan:Offset,val zoom:Float,val span:Float)

/** A release may carry the final coalesced positions even without an intermediate move. */
internal fun storyGestureMotion(event:PointerEvent):StoryGestureMotion {
    val contacts=event.changes.filter { it.previousPressed }
    if(event.changes.none { it.pressed } && contacts.isNotEmpty()) {
        val before=contacts.fold(Offset.Zero) { sum,c->sum+c.previousPosition } / contacts.size.toFloat()
        val after=contacts.fold(Offset.Zero) { sum,c->sum+c.position } / contacts.size.toFloat()
        val oldSpan=contacts.sumOf { (it.previousPosition-before).getDistance().toDouble() }.toFloat()/contacts.size
        val newSpan=contacts.sumOf { (it.position-after).getDistance().toDouble() }.toFloat()/contacts.size
        val zoom=if(oldSpan>0) newSpan/oldSpan else 1f
        return StoryGestureMotion(before,after-before,zoom,oldSpan)
    }
    return StoryGestureMotion(event.calculateCentroid(useCurrent=false),event.calculatePan(),event.calculateZoom(),event.calculateCentroidSize(useCurrent=false))
}

/** Capture media gestures before the surrounding form scroll or native video view. */
internal fun Modifier.storyFrameGestures(key:Any,enabled:Boolean,onActive:(Boolean)->Unit,onTransform:(Offset,Offset,Float)->Unit,onEnd:()->Unit):Modifier =
    if(!enabled) this else pointerInput(key) {
        awaitEachGesture {
            awaitFirstDown(requireUnconsumed=false,pass=PointerEventPass.Initial)
            onActive(true)
            try {
                var pastSlop=false
                var accumulatedZoom=1f
                var accumulatedPan=Offset.Zero
                do {
                    val event=awaitPointerEvent(PointerEventPass.Initial)
                    val motion=storyGestureMotion(event)
                    val zoom=motion.zoom;val pan=motion.pan
                    if(!pastSlop) {
                        accumulatedZoom*=zoom;accumulatedPan+=pan
                        val zoomMotion=abs(1-accumulatedZoom)*motion.span
                        pastSlop=zoomMotion>viewConfiguration.touchSlop || accumulatedPan.getDistance()>viewConfiguration.touchSlop
                    }
                    if(pastSlop) onTransform(motion.centroid,pan,zoom)
                    // The image is a canvas, so dragging it must not scroll the form.
                    event.changes.forEach { it.consume() }
                } while(event.changes.any { it.pressed })
            } finally {
                try { onEnd() } finally { onActive(false) }
            }
        }
    }
