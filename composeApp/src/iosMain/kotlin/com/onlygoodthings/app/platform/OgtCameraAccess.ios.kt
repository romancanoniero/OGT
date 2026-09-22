package com.onlygoodthings.app.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import platform.AVFoundation.AVAuthorizationStatusAuthorized
import platform.AVFoundation.AVCaptureDevice
import platform.AVFoundation.AVMediaTypeVideo
import platform.AVFoundation.authorizationStatusForMediaType
import platform.AVFoundation.requestAccessForMediaType
import platform.Photos.PHAuthorizationStatusAuthorized
import platform.Photos.PHAuthorizationStatusLimited
import platform.Photos.PHPhotoLibrary
import platform.Photos.PHAccessLevelReadWrite

@Composable
actual fun rememberOgtCameraAccess(): OgtCameraAccess {
    fun read(): Boolean {
        val camera = AVCaptureDevice.authorizationStatusForMediaType(AVMediaTypeVideo) ==
            AVAuthorizationStatusAuthorized
        val photos = PHPhotoLibrary.authorizationStatusForAccessLevel(PHAccessLevelReadWrite).let {
            it == PHAuthorizationStatusAuthorized || it == PHAuthorizationStatusLimited
        }
        return camera && photos
    }
    var granted by remember { mutableStateOf(read()) }
    return OgtCameraAccess(
        granted = granted,
        request = {
            AVCaptureDevice.requestAccessForMediaType(AVMediaTypeVideo) { _ ->
                PHPhotoLibrary.requestAuthorizationForAccessLevel(PHAccessLevelReadWrite) { _ ->
                    granted = read()
                }
            }
        },
    )
}
