package com.onlygoodthings.app.platform

actual fun reduceStartupMotion(): Boolean = !android.animation.ValueAnimator.areAnimatorsEnabled()
