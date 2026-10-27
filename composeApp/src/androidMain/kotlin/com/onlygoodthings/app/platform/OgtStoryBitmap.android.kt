package com.onlygoodthings.app.platform
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
actual fun decodeStoryBitmap(bytes:ByteArray):ImageBitmap?=runCatching {
    val bitmap=BitmapFactory.decodeByteArray(bytes,0,bytes.size) ?: return@runCatching null
    val orientation=runCatching { ExifInterface(bytes.inputStream()).getAttributeInt(ExifInterface.TAG_ORIENTATION,1) }.getOrDefault(1)
    if(orientation==1) return@runCatching bitmap.asImageBitmap()
    val matrix=Matrix().apply { when(orientation) {
        2->setScale(-1f,1f);3->setRotate(180f);4->{setRotate(180f);postScale(-1f,1f)}
        5->{setRotate(90f);postScale(-1f,1f)};6->setRotate(90f)
        7->{setRotate(-90f);postScale(-1f,1f)};8->setRotate(-90f)
    } }
    val oriented=Bitmap.createBitmap(bitmap,0,0,bitmap.width,bitmap.height,matrix,true)
    if(oriented!==bitmap) bitmap.recycle()
    oriented.asImageBitmap()
}.getOrNull()
