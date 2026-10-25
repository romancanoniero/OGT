package com.onlygoodthings.backend.media

import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.util.Random
import javax.imageio.IIOImage
import javax.imageio.ImageIO
import javax.imageio.ImageWriteParam
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ImageOptimizerOrientationTest {
    @Test
    fun jpegConExifGiraLosPixelesAunqueElResultadoSeaMasPesado() {
        val source = compressedJpegWithOrientation6(160, 90)
        val result = ImageOptimizer.optimize(source, "jpg")
        val decoded = ImageIO.read(result.bytes.inputStream())

        assertEquals("jpg", result.extension)
        assertEquals(90, decoded.width)
        assertEquals(160, decoded.height)
        assertTrue(result.bytes.isNotEmpty())
    }

    private fun compressedJpegWithOrientation6(width: Int, height: Int): ByteArray {
        val image = BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)
        val random = Random(42L)
        for (y in 0 until height) for (x in 0 until width) {
            image.setRGB(x, y, random.nextInt(0x1000000))
        }
        val original = ByteArrayOutputStream()
        val writer = ImageIO.getImageWritersByFormatName("jpg").next()
        try {
            ImageIO.createImageOutputStream(original).use { stream ->
                writer.output = stream
                val options = writer.defaultWriteParam
                options.compressionMode = ImageWriteParam.MODE_EXPLICIT
                options.compressionQuality = 0.04f
                writer.write(null, IIOImage(image, null, null), options)
            }
        } finally {
            writer.dispose()
        }

        // Exif TIFF IFD0: tag 0x0112, SHORT, count 1, orientation 6 (90 degrees).
        val exif = byteArrayOf(
            0x45, 0x78, 0x69, 0x66, 0, 0,
            0x49, 0x49, 0x2a, 0, 0x08, 0, 0, 0,
            0x01, 0, 0x12, 0x01, 0x03, 0,
            0x01, 0, 0, 0, 0x06, 0, 0, 0, 0, 0, 0
        )
        val jpeg = original.toByteArray()
        val output = ByteArrayOutputStream()
        output.write(jpeg, 0, 2) // JPEG SOI
        output.write(0xFF)
        output.write(0xE1) // APP1 Exif
        val length = exif.size + 2
        output.write(length shr 8)
        output.write(length and 0xFF)
        output.write(exif)
        output.write(jpeg, 2, jpeg.size - 2)
        return output.toByteArray()
    }
}
