package com.onlygoodthings.app.platform

import androidx.compose.ui.graphics.ImageBitmap

/** Vista para encuadrar. La exportación lee el archivo original, no esta vista. */
expect fun loadFramePreview(path: String): ImageBitmap?

/**
 * Recorta el archivo original al 16:9 del feed y lo guarda en JPEG.
 * Devuelve el path del archivo nuevo.
 */
expect fun exportFramedJpeg(
    path: String,
    zoom: Float,
    panX: Float,
    panY: Float,
    rotationDeg: Float,
): String?

/**
 * Deja la foto lista para subir: lado largo hasta 2048 y JPEG de alta calidad.
 * Si ya entra en ese tamaño, no la vuelve a comprimir.
 */
expect fun compressPhotoForUpload(path: String): String?
