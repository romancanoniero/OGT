package com.onlygoodthings.app.map

actual object OgtBackgroundLocation {
    actual fun start() {
        if (IosLocationRuntime.permission().isGranted()) {
            IosLocationRuntime.start(
                background = IosLocationRuntime.permission() == LocationPermissionState.GRANTED_ALWAYS,
            )
        }
    }

    actual fun stop() {
        IosLocationRuntime.stop()
    }
}
