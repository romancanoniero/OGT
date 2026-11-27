package com.onlygoodthings.app.platform

import androidx.compose.ui.graphics.ImageBitmap
import io.ktor.http.encodeURLParameter

/** Local sharing destinations only; this inventory is never uploaded. */
data class PostShareApp(val id:String,val label:String,val brand:String="app",val icon:ImageBitmap?=null)
expect suspend fun installedPostShareApps():List<PostShareApp>
expect fun sharePostWithApp(app:PostShareApp,text:String,url:String):Boolean

internal fun postShareAppUri(id:String,text:String,url:String):String? = when(id) {
    "whatsapp" -> "whatsapp://send?text=${text.encodeURLParameter()}"
    "telegram" -> "tg://msg_url?url=${url.encodeURLParameter()}&text=${text.replace(url, "").trim().encodeURLParameter()}"
    else -> null
}
