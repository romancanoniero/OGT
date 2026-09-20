package com.onlygoodthings.app.map

import androidx.compose.ui.graphics.ImageBitmap

/** Decodifica PNG/JPEG a [ImageBitmap] en cada plataforma. */
expect fun decodeTileBitmap(bytes: ByteArray): ImageBitmap?
