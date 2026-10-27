package com.onlygoodthings.app.platform
import androidx.compose.ui.graphics.ImageBitmap
/** Decode the displayed orientation so local framing matches the server-normalized photo. */
expect fun decodeStoryBitmap(bytes:ByteArray):ImageBitmap?
