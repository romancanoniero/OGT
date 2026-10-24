package com.onlygoodthings.backend.media

import com.drew.imaging.ImageMetadataReader
import com.drew.metadata.exif.ExifIFD0Directory
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import javax.imageio.IIOImage
import javax.imageio.ImageIO
import javax.imageio.ImageWriteParam
import javax.imageio.stream.MemoryCacheImageInputStream
import javax.imageio.stream.MemoryCacheImageOutputStream
import kotlin.math.max
import kotlin.math.roundToInt

/** Shared by every API image upload; clients cannot bypass it. */
object ImageOptimizer {
    const val MAX_EDGE = 2048
    const val MAX_PIXELS = 40_000_000L
    data class Result(val bytes: ByteArray, val extension: String)

    // Bound decoded-image memory under concurrent uploads. Videos never take this lock.
    @Synchronized
    fun optimize(bytes: ByteArray, extension: String): Result {
        try {
            MemoryCacheImageInputStream(bytes.inputStream()).use { input ->
                val readers = ImageIO.getImageReaders(input)
                require(readers.hasNext()) { "La imagen no se puede leer" }
                val reader = readers.next()
                try {
                    reader.input = input
                    val actual = reader.formatName.lowercase().let { if (it == "jpeg") "jpg" else it }
                    require(actual == extension) { "El contenido no coincide con el formato de imagen" }
                    val width = reader.getWidth(0)
                    val height = reader.getHeight(0)
                    require(width > 0 && height > 0 && width.toLong() * height <= MAX_PIXELS) {
                        "La imagen supera el límite de 40 megapíxeles"
                    }
                    // Re-encoding just frame zero would destroy existing animations.
                    // Keep animated PNG/WebP intact until a sequence-aware encoder is available.
                    if (isAnimated(bytes, extension)) return Result(bytes, extension)
                    val source = reader.read(0) ?: throw IllegalArgumentException("La imagen no se puede leer")
                    val orientation = runCatching {
                        ImageMetadataReader.readMetadata(bytes.inputStream())
                            .getFirstDirectoryOfType(ExifIFD0Directory::class.java)
                            ?.getInteger(ExifIFD0Directory.TAG_ORIENTATION) ?: 1
                    }.getOrDefault(1)
                    val oriented = orient(source, orientation)
                    val scale = minOf(1.0, MAX_EDGE.toDouble() / max(oriented.width, oriented.height))
                    val resized = BufferedImage(
                        max(1, (oriented.width * scale).roundToInt()),
                        max(1, (oriented.height * scale).roundToInt()),
                        if (oriented.colorModel.hasAlpha()) BufferedImage.TYPE_INT_ARGB else BufferedImage.TYPE_INT_RGB,
                    )
                    resized.createGraphics().apply {
                        setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC)
                        drawImage(oriented, 0, 0, resized.width, resized.height, null)
                        dispose()
                    }
                    // PNG stays lossless; JPEG stays photographic. WebP may already be smaller.
                    val target = if (extension == "png" || resized.colorModel.hasAlpha()) "png" else "jpg"
                    val encoded = encode(resized, target)
                    val changed = scale < 0.999 || orientation != 1 || target != extension
                    return if (changed || encoded.size < bytes.size) Result(encoded, target) else Result(bytes, extension)
                } finally { reader.dispose() }
            }
        } catch (e: IllegalArgumentException) {
            throw e
        } catch (e: Exception) {
            throw IllegalArgumentException("La imagen está dañada o no se puede procesar", e)
        }
    }

    private fun encode(image: BufferedImage, extension: String): ByteArray {
        val writer = ImageIO.getImageWritersByFormatName(extension).next()
        val bytes = ByteArrayOutputStream()
        try {
            MemoryCacheImageOutputStream(bytes).use { output ->
                writer.output = output
                val params = writer.defaultWriteParam
                if (extension == "jpg") {
                    params.compressionMode = ImageWriteParam.MODE_EXPLICIT
                    // 0.92 conserva el detalle. Más abajo se nota en bordes y piel.
                    params.compressionQuality = 0.92f
                }
                writer.write(null, IIOImage(image, null, null), params)
            }
            return bytes.toByteArray()
        } finally { writer.dispose() }
    }

    internal fun orient(source: BufferedImage, orientation: Int): BufferedImage {
        if (orientation !in 2..8) return source
        val swap = orientation >= 5
        val result = BufferedImage(if (swap) source.height else source.width, if (swap) source.width else source.height,
            if (source.colorModel.hasAlpha()) BufferedImage.TYPE_INT_ARGB else BufferedImage.TYPE_INT_RGB)
        for (y in 0 until source.height) for (x in 0 until source.width) {
            val (dx, dy) = when (orientation) {
                2 -> source.width - 1 - x to y
                3 -> source.width - 1 - x to source.height - 1 - y
                4 -> x to source.height - 1 - y
                5 -> y to x
                6 -> source.height - 1 - y to x
                7 -> source.height - 1 - y to source.width - 1 - x
                else -> y to source.width - 1 - x
            }
            result.setRGB(dx, dy, source.getRGB(x, y))
        }
        return result
    }

    private fun isAnimated(bytes: ByteArray, extension: String): Boolean {
        var offset = if (extension == "png") 8 else if (extension == "webp") 12 else return false
        while (offset + 8 <= bytes.size) {
            val length = if (extension == "png") (0..3).fold(0L) { n, i -> (n shl 8) or (bytes[offset + i].toLong() and 255) }
                else (0..3).fold(0L) { n, i -> n or ((bytes[offset + 4 + i].toLong() and 255) shl (8 * i)) }
            val typeOffset = if (extension == "png") offset + 4 else offset
            val type = String(bytes, typeOffset, 4, Charsets.US_ASCII)
            require(length <= bytes.size - offset - 8) { "Estructura de imagen inválida" }
            if (type == "acTL" || type == "ANIM") return true
            val next = offset.toLong() + 8 + length + if (extension == "png") 4 else (length % 2)
            require(next <= bytes.size) { "Estructura de imagen inválida" }
            offset = next.toInt()
        }
        return false
    }
}
