package com.onlygoodthings.backend.media

import com.drew.imaging.ImageMetadataReader
import com.drew.metadata.exif.ExifIFD0Directory
import com.onlygoodthings.shared.domain.postFrameSourceRect
import java.awt.Color
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import javax.imageio.IIOImage
import javax.imageio.ImageIO
import javax.imageio.ImageWriteParam
import javax.imageio.stream.MemoryCacheImageOutputStream

/** Recorta la foto al marco 16:9 que eligió el operador. El resultado es un JPEG nuevo. */
object FrameCrop {
    const val OUT_WIDTH = 1280
    const val OUT_HEIGHT = 720

    fun jpeg(bytes: ByteArray, frameX: Double, frameY: Double, frameZoom: Double): ByteArray {
        require(bytes.isNotEmpty()) { "La imagen no se puede leer" }
        val source = ImageIO.read(bytes.inputStream()) ?: throw IllegalArgumentException("La imagen no se puede leer")
        require(source.width > 0 && source.height > 0 && source.width.toLong() * source.height <= ImageOptimizer.MAX_PIXELS) {
            "La imagen supera el límite de 40 megapíxeles"
        }
        val orientation = runCatching {
            ImageMetadataReader.readMetadata(bytes.inputStream())
                .getFirstDirectoryOfType(ExifIFD0Directory::class.java)
                ?.getInteger(ExifIFD0Directory.TAG_ORIENTATION) ?: 1
        }.getOrDefault(1)
        val oriented = ImageOptimizer.orient(source, orientation)
        val rect = postFrameSourceRect(
            imageWidth = oriented.width.toFloat(),
            imageHeight = oriented.height.toFloat(),
            frameX = frameX.toFloat(),
            frameY = frameY.toFloat(),
            frameZoom = frameZoom.toFloat(),
        ) ?: throw IllegalArgumentException("El encuadre no entra en el marco")
        val out = BufferedImage(OUT_WIDTH, OUT_HEIGHT, BufferedImage.TYPE_INT_RGB)
        val graphics = out.createGraphics()
        try {
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC)
            graphics.color = Color.BLACK
            graphics.fillRect(0, 0, OUT_WIDTH, OUT_HEIGHT)
            val scaleX = OUT_WIDTH / rect.width.toDouble()
            val scaleY = OUT_HEIGHT / rect.height.toDouble()
            graphics.translate(-rect.x * scaleX, -rect.y * scaleY)
            graphics.scale(scaleX, scaleY)
            graphics.drawImage(oriented, 0, 0, null)
        } finally {
            graphics.dispose()
        }
        return encodeJpeg(out)
    }

    private fun encodeJpeg(image: BufferedImage): ByteArray {
        val writer = ImageIO.getImageWritersByFormatName("jpg").next()
        val bytes = ByteArrayOutputStream()
        try {
            MemoryCacheImageOutputStream(bytes).use { output ->
                writer.output = output
                val params = writer.defaultWriteParam
                params.compressionMode = ImageWriteParam.MODE_EXPLICIT
                params.compressionQuality = 0.84f
                writer.write(null, IIOImage(image, null, null), params)
            }
            return bytes.toByteArray()
        } finally {
            writer.dispose()
        }
    }
}
