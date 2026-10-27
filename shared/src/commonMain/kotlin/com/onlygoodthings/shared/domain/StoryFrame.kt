package com.onlygoodthings.shared.domain

import kotlinx.serialization.Serializable
import kotlin.math.*

@Serializable enum class StoryFrameMode { FILL, FIT }

/** Offsets are fractions of the available overflow, independent of screen pixels. */
@Serializable data class StoryFrame(
    val mode: StoryFrameMode = StoryFrameMode.FILL,
    val zoom: Double = 1.0,
    val x: Double = 0.0,
    val y: Double = 0.0,
) {
    init {
        require(zoom.isFinite() && zoom in 1.0..6.0) { "Zoom de historia inválido" }
        require(x.isFinite() && y.isFinite() && x in -1.0..1.0 && y in -1.0..1.0) { "Posición de historia inválida" }
    }
}

data class StoryFrameLayout(val width: Double, val height: Double, val left: Double, val top: Double)
fun storyFrameLayout(width: Double, height: Double, mediaWidth: Double, mediaHeight: Double, frame: StoryFrame?): StoryFrameLayout {
    require(listOf(width,height,mediaWidth,mediaHeight).all { it.isFinite() && it > 0 })
    val setting=frame ?: StoryFrame(StoryFrameMode.FIT)
    val base=if(setting.mode==StoryFrameMode.FILL) max(width/mediaWidth,height/mediaHeight) else min(width/mediaWidth,height/mediaHeight)
    val w=mediaWidth*base*setting.zoom;val h=mediaHeight*base*setting.zoom
    return StoryFrameLayout(w,h,(width-w)/2+setting.x*max(0.0,(w-width)/2),(height-h)/2+setting.y*max(0.0,(h-height)/2))
}
