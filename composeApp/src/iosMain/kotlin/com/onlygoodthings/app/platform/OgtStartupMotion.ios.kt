package com.onlygoodthings.app.platform

actual fun reduceStartupMotion(): Boolean = platform.UIKit.UIAccessibilityIsReduceMotionEnabled()
