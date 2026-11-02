package com.onlygoodthings.app.platform

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

@Composable
actual fun rememberOgtCameraAccess(): OgtCameraAccess {
    val context=LocalContext.current
    fun read()=ContextCompat.checkSelfPermission(context,Manifest.permission.CAMERA)==PackageManager.PERMISSION_GRANTED
    var granted by remember { mutableStateOf(read()) }
    var asked by remember { mutableStateOf(false) }
    val launcher=rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted=read() }
    val owner=LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val observer=LifecycleEventObserver { _,event -> if(event==Lifecycle.Event.ON_RESUME)granted=read() }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
    return OgtCameraAccess(granted) {
        if(!read()) {
            val activity=runCatching { com.onlygoodthings.app.AndroidAuthHost.requireActivity() }.getOrNull()
            if(asked && activity!=null && !ActivityCompat.shouldShowRequestPermissionRationale(activity,Manifest.permission.CAMERA)) {
                context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,Uri.fromParts("package",context.packageName,null)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            } else { asked=true;launcher.launch(Manifest.permission.CAMERA) }
        }
    }
}
