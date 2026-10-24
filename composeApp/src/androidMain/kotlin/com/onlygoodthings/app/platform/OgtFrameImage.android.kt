package com.onlygoodthings.app.platform

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.media.ExifInterface
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.onlygoodthings.app.OgtApplication
import com.onlygoodthings.shared.domain.PostFrame
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

actual fun loadFramePreview(path: String): ImageBitmap? {
    val file = localFilePath(path) ?: path
    val oriented = decodeOriented(file, maxEdge = 1600) ?: return null
    return oriented.asImageBitmap()
}

actual fun exportFramedJpeg(
    path: String,
    zoom: Float,
    panX: Float,
    panY: Float,
    rotationDeg: Float,
): String? {
    val file = localFilePath(path) ?: path
    val source = decodeOriented(file, maxEdge = 12_000) ?: return null
    val (outW, outH) = PostFrame.outputSize(source.width, source.height, rotationDeg.toDouble(), zoom.toDouble())
    val out = Bitmap.createBitmap(outW, outH, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(out)
    canvas.drawColor(Color.rgb(244, 245, 247))
    val scale = PostFrame.coverScale(
        source.width.toDouble(),
        source.height.toDouble(),
        rotationDeg.toDouble(),
        outW.toDouble(),
        outH.toDouble(),
    ) * zoom.toDouble().coerceIn(PostFrame.MIN_ZOOM, PostFrame.MAX_ZOOM)
    val matrix = Matrix()
    matrix.postTranslate(-source.width / 2f, -source.height / 2f)
    matrix.postScale(scale.toFloat(), scale.toFloat())
    matrix.postRotate(rotationDeg)
    matrix.postTranslate(outW / 2f + panX * outW, outH / 2f + panY * outH)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    canvas.drawBitmap(source, matrix, paint)
    val dest = cacheJpeg()
    FileOutputStream(dest).use { stream ->
        if (!out.compress(Bitmap.CompressFormat.JPEG, PostFrame.JPEG_QUALITY, stream)) return null
    }
    if (source !== out) source.recycle()
    out.recycle()
    return dest.absolutePath.takeIf { dest.length() > 0 }
}

actual fun compressPhotoForUpload(path: String): String? {
    val file = localFilePath(path) ?: path
    val edge = boundsEdge(file) ?: return null
    if (edge <= PostFrame.MAX_EDGE && isJpeg(file)) return file
    val source = decodeOriented(file, maxEdge = PostFrame.MAX_EDGE) ?: return null
    val dest = cacheJpeg()
    FileOutputStream(dest).use { stream ->
        if (!source.compress(Bitmap.CompressFormat.JPEG, PostFrame.JPEG_QUALITY, stream)) return null
    }
    source.recycle()
    return dest.absolutePath.takeIf { dest.length() > 0 }
}

private fun boundsEdge(path: String): Int? {
    val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(path, opts)
    if (opts.outWidth <= 0 || opts.outHeight <= 0) return null
    return max(opts.outWidth, opts.outHeight)
}

private fun isJpeg(path: String): Boolean {
    val bytes = File(path).inputStream().use { input ->
        ByteArray(3).also { input.read(it) }
    }
    return bytes.size >= 3 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xD8.toByte()
}

private fun decodeOriented(path: String, maxEdge: Int): Bitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(path, bounds)
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
    var sample = 1
    while (max(bounds.outWidth, bounds.outHeight) / sample > maxEdge) sample *= 2
    val opts = BitmapFactory.Options().apply { inSampleSize = sample }
    val decoded = BitmapFactory.decodeFile(path, opts) ?: return null
    val degrees = exifDegrees(path)
    if (degrees == 0) return decoded
    val matrix = Matrix().apply { postRotate(degrees.toFloat()) }
    val turned = Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true)
    if (turned !== decoded) decoded.recycle()
    if (max(turned.width, turned.height) <= maxEdge) return turned
    val scale = maxEdge.toFloat() / max(turned.width, turned.height)
    val w = max(1, (turned.width * scale).roundToInt())
    val h = max(1, (turned.height * scale).roundToInt())
    val fitted = Bitmap.createScaledBitmap(turned, w, h, true)
    if (fitted !== turned) turned.recycle()
    return fitted
}

private fun exifDegrees(path: String): Int {
    val orientation = runCatching {
        ExifInterface(path).getAttributeInt(
            ExifInterface.TAG_ORIENTATION,
            ExifInterface.ORIENTATION_UNDEFINED,
        )
    }.getOrDefault(ExifInterface.ORIENTATION_UNDEFINED)
    return when (orientation) {
        ExifInterface.ORIENTATION_ROTATE_90, ExifInterface.ORIENTATION_TRANSPOSE -> 90
        ExifInterface.ORIENTATION_ROTATE_180, ExifInterface.ORIENTATION_FLIP_VERTICAL -> 180
        ExifInterface.ORIENTATION_ROTATE_270, ExifInterface.ORIENTATION_TRANSVERSE -> 270
        else -> 0
    }
}

private fun cacheJpeg(): File {
    val dir = File(OgtApplication.instance.cacheDir, "ogt-media").apply { mkdirs() }
    return File(dir, "${UUID.randomUUID()}.jpg")
}
