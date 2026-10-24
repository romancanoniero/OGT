package com.onlygoodthings.app.platform

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import com.onlygoodthings.shared.domain.PostFrame
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.useContents
import kotlinx.cinterop.usePinned
import org.jetbrains.skia.Image
import platform.CoreGraphics.CGContextFillRect
import platform.CoreGraphics.CGContextRestoreGState
import platform.CoreGraphics.CGContextRotateCTM
import platform.CoreGraphics.CGContextSaveGState
import platform.CoreGraphics.CGContextScaleCTM
import platform.CoreGraphics.CGContextSetFillColorWithColor
import platform.CoreGraphics.CGContextTranslateCTM
import platform.CoreGraphics.CGRectMake
import platform.CoreGraphics.CGSizeMake
import platform.Foundation.NSCachesDirectory
import platform.Foundation.NSData
import platform.Foundation.NSFileManager
import platform.Foundation.create
import platform.Foundation.NSSearchPathForDirectoriesInDomains
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSUUID
import platform.Foundation.NSUserDomainMask
import platform.Foundation.writeToFile
import platform.UIKit.UIColor
import platform.UIKit.UIGraphicsBeginImageContextWithOptions
import platform.UIKit.UIGraphicsEndImageContext
import platform.UIKit.UIGraphicsGetCurrentContext
import platform.UIKit.UIGraphicsGetImageFromCurrentImageContext
import platform.UIKit.UIImage
import platform.UIKit.UIImageJPEGRepresentation
import platform.posix.memcpy
import kotlin.math.PI
import kotlin.math.max
import kotlin.math.min

@OptIn(ExperimentalForeignApi::class)
actual fun loadFramePreview(path: String): ImageBitmap? {
    val file = localFilePath(path) ?: path
    val image = UIImage.imageWithContentsOfFile(file) ?: return null
    val (px, py) = pixelSize(image)
    val edge = max(px, py)
    val fit = min(1.0, 1600.0 / edge)
    val bytes = renderJpeg(image, px * fit, py * fit, quality = PostFrame.JPEG_QUALITY / 100.0) ?: return null
    return runCatching { Image.makeFromEncoded(bytes).toComposeImageBitmap() }.getOrNull()
}

@OptIn(ExperimentalForeignApi::class)
actual fun exportFramedJpeg(
    path: String,
    zoom: Float,
    panX: Float,
    panY: Float,
    rotationDeg: Float,
): String? {
    val file = localFilePath(path) ?: path
    val image = UIImage.imageWithContentsOfFile(file) ?: return null
    val (px, py) = pixelSize(image)
    val (outW, outH) = PostFrame.outputSize(px.toInt(), py.toInt(), rotationDeg.toDouble(), zoom.toDouble())
    val scale = PostFrame.coverScale(px, py, rotationDeg.toDouble(), outW.toDouble(), outH.toDouble()) *
        zoom.toDouble().coerceIn(PostFrame.MIN_ZOOM, PostFrame.MAX_ZOOM)
    UIGraphicsBeginImageContextWithOptions(CGSizeMake(outW.toDouble(), outH.toDouble()), true, 1.0)
    try {
        val ctx = UIGraphicsGetCurrentContext() ?: return null
        val sand = UIColor.colorWithRed(244.0 / 255.0, 245.0 / 255.0, 247.0 / 255.0, 1.0)
        CGContextSetFillColorWithColor(ctx, sand.CGColor)
        CGContextFillRect(ctx, CGRectMake(0.0, 0.0, outW.toDouble(), outH.toDouble()))
        CGContextSaveGState(ctx)
        CGContextTranslateCTM(ctx, outW / 2.0 + panX * outW, outH / 2.0 + panY * outH)
        CGContextRotateCTM(ctx, rotationDeg * PI / 180.0)
        CGContextScaleCTM(ctx, scale, scale)
        image.drawInRect(CGRectMake(-px / 2.0, -py / 2.0, px, py))
        CGContextRestoreGState(ctx)
        val rendered = UIGraphicsGetImageFromCurrentImageContext() ?: return null
        val data = UIImageJPEGRepresentation(rendered, PostFrame.JPEG_QUALITY / 100.0) ?: return null
        val dest = "${frameCacheDir()}/${NSUUID().UUIDString}.jpg"
        return dest.takeIf { data.writeToFile(dest, atomically = true) }
    } finally {
        UIGraphicsEndImageContext()
    }
}

@OptIn(ExperimentalForeignApi::class)
actual fun compressPhotoForUpload(path: String): String? {
    val file = localFilePath(path) ?: path
    val image = UIImage.imageWithContentsOfFile(file) ?: return null
    val (px, py) = pixelSize(image)
    val edge = max(px, py)
    if (edge <= PostFrame.MAX_EDGE && file.endsWith(".jpg", ignoreCase = true)) return file
    val fit = min(1.0, PostFrame.MAX_EDGE / edge)
    val data = renderJpeg(image, px * fit, py * fit, PostFrame.JPEG_QUALITY / 100.0) ?: return null
    val dest = "${frameCacheDir()}/${NSUUID().UUIDString}.jpg"
    val ns = data.toNsData() ?: return null
    return dest.takeIf { ns.writeToFile(dest, atomically = true) }
}

@OptIn(ExperimentalForeignApi::class)
private fun pixelSize(image: UIImage): Pair<Double, Double> {
    val w = image.size.useContents { width } * image.scale
    val h = image.size.useContents { height } * image.scale
    return w to h
}

@OptIn(ExperimentalForeignApi::class)
private fun renderJpeg(image: UIImage, width: Double, height: Double, quality: Double): ByteArray? {
    val w = max(1.0, width)
    val h = max(1.0, height)
    UIGraphicsBeginImageContextWithOptions(CGSizeMake(w, h), true, 1.0)
    try {
        image.drawInRect(CGRectMake(0.0, 0.0, w, h))
        val rendered = UIGraphicsGetImageFromCurrentImageContext() ?: return null
        val data = UIImageJPEGRepresentation(rendered, quality) ?: return null
        return data.toByteArray()
    } finally {
        UIGraphicsEndImageContext()
    }
}

@OptIn(ExperimentalForeignApi::class)
private fun NSData.toByteArray(): ByteArray {
    val size = length.toInt()
    if (size <= 0) return ByteArray(0)
    val out = ByteArray(size)
    out.usePinned { pinned -> memcpy(pinned.addressOf(0), bytes, length) }
    return out
}

@OptIn(ExperimentalForeignApi::class)
private fun ByteArray.toNsData(): NSData? {
    if (isEmpty()) return null
    return usePinned { pinned ->
        NSData.create(bytes = pinned.addressOf(0), length = size.toULong())
    }
}

@OptIn(ExperimentalForeignApi::class)
private fun frameCacheDir(): String {
    val root = (NSSearchPathForDirectoriesInDomains(NSCachesDirectory, NSUserDomainMask, true).firstOrNull() as? String)
        ?: NSTemporaryDirectory()
    val dir = "${root.trimEnd('/')}/ogt-media"
    NSFileManager.defaultManager.createDirectoryAtPath(dir, true, null, null)
    return dir
}
