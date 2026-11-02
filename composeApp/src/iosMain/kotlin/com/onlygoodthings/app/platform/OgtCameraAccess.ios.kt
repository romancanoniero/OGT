package com.onlygoodthings.app.platform

import androidx.compose.runtime.*
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import platform.AVFoundation.*
import platform.Foundation.NSURL
import platform.UIKit.UIApplication
import platform.UIKit.UIApplicationOpenSettingsURLString

@Composable
actual fun rememberOgtCameraAccess(): OgtCameraAccess {
    fun read()=AVCaptureDevice.authorizationStatusForMediaType(AVMediaTypeVideo)==AVAuthorizationStatusAuthorized
    var granted by remember { mutableStateOf(read()) }
    val owner=LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val observer=LifecycleEventObserver { _,event -> if(event==Lifecycle.Event.ON_RESUME)granted=read() }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
    return OgtCameraAccess(granted) {
        when(AVCaptureDevice.authorizationStatusForMediaType(AVMediaTypeVideo)) {
            AVAuthorizationStatusNotDetermined -> AVCaptureDevice.requestAccessForMediaType(AVMediaTypeVideo) { _ ->
                com.onlygoodthings.app.notify.onMain { granted=read() }
            }
            AVAuthorizationStatusAuthorized -> granted=true
            else -> NSURL.URLWithString(UIApplicationOpenSettingsURLString)?.let { UIApplication.sharedApplication.openURL(it) }
        }
    }
}
