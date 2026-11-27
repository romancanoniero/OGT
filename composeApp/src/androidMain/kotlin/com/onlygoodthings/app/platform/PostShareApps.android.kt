package com.onlygoodthings.app.platform

import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.compose.ui.graphics.asImageBitmap
import com.onlygoodthings.app.AndroidAuthHost
import com.onlygoodthings.app.OgtApplication
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private fun shareContext():android.content.Context=runCatching<android.content.Context> { AndroidAuthHost.requireActivity() }.getOrElse { OgtApplication.instance }
actual suspend fun installedPostShareApps():List<PostShareApp> = withContext(Dispatchers.Default) {
    val context=shareContext();val packages=context.packageManager
    val intent=Intent(Intent.ACTION_SEND).apply { type="text/plain" }
    packages.queryIntentActivities(intent,PackageManager.MATCH_DEFAULT_ONLY)
        .filter { it.activityInfo.exported && it.activityInfo.enabled && it.activityInfo.packageName!=context.packageName }
        .distinctBy { it.activityInfo.packageName }.map { resolved ->
            val info=resolved.activityInfo
            val bitmap=runCatching {
                val drawable=info.applicationInfo.loadIcon(packages)
                Bitmap.createBitmap(128,128,Bitmap.Config.ARGB_8888).also { image ->
                    drawable.setBounds(0,0,128,128);drawable.draw(Canvas(image))
                }.asImageBitmap()
            }.getOrNull()
            PostShareApp(ComponentName(info.packageName,info.name).flattenToString(),info.applicationInfo.loadLabel(packages).toString(),icon=bitmap)
        }
}
actual fun sharePostWithApp(app:PostShareApp,text:String,url:String):Boolean = runCatching {
    val component=ComponentName.unflattenFromString(app.id) ?: return false
    val intent=Intent(Intent.ACTION_SEND).apply { type="text/plain";putExtra(Intent.EXTRA_TEXT,text);this.component=component;addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
    shareContext().startActivity(intent);true
}.getOrDefault(false)
