@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)
package com.onlygoodthings.app.platform
import androidx.compose.ui.graphics.ImageBitmap
import com.onlygoodthings.app.map.decodeTileBitmap
import kotlinx.cinterop.*
import platform.Foundation.NSData
import platform.Foundation.create
import platform.UIKit.*
import platform.CoreGraphics.CGRectMake
actual fun decodeStoryBitmap(bytes:ByteArray):ImageBitmap?=runCatching {
    if(bytes.isEmpty()) return@runCatching null
    val data=bytes.usePinned { NSData.create(bytes=it.addressOf(0),length=bytes.size.toULong()) }
    val image=UIImage(data=data)
    if(image.imageOrientation==UIImageOrientation.UIImageOrientationUp) return@runCatching decodeTileBitmap(bytes)
    UIGraphicsBeginImageContextWithOptions(image.size,false,image.scale)
    try {
        image.size.useContents { image.drawInRect(CGRectMake(0.0,0.0,width,height)) }
        val normalized=UIGraphicsGetImageFromCurrentImageContext() ?: return@runCatching null
        val encoded=UIImagePNGRepresentation(normalized) ?: return@runCatching null
        decodeTileBitmap(encoded.bytes?.readBytes(encoded.length.toInt()) ?: return@runCatching null)
    } finally { UIGraphicsEndImageContext() }
}.getOrNull()
