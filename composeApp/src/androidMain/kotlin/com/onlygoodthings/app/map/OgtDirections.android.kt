package com.onlygoodthings.app.map

import android.content.Intent
import android.net.Uri
import com.onlygoodthings.app.AndroidAuthHost
import com.onlygoodthings.app.OgtApplication

actual fun openWalkingDirections(latitude: Double, longitude: Double) {
    val context = runCatching { AndroidAuthHost.requireActivity() }.getOrElse { OgtApplication.instance }
    val nav = Intent(Intent.ACTION_VIEW, Uri.parse("google.navigation:q=$latitude,$longitude&mode=w")).apply {
        setPackage("com.google.android.apps.maps")
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    val web = Intent(Intent.ACTION_VIEW, Uri.parse(walkingDirectionsWebUrl(latitude, longitude))).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    val launch = if (nav.resolveActivity(context.packageManager) != null) nav else web
    context.startActivity(launch)
}
