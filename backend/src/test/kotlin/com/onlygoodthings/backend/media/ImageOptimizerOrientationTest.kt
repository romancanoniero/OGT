package com.onlygoodthings.backend.media

import com.drew.imaging.ImageMetadataReader
import com.drew.metadata.exif.ExifIFD0Directory
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
        // Use an EXIF segment from our known-good rotated camera fixture.
        // Hand-crafted TIFF headers were accepted by some readers but ignored
        // by the metadata extractor used in production.
        val camera = javaClass.getResourceAsStream("/images/rotated.jpg")!!.use { it.readBytes() }
        val exif = exifSegment(camera)
        val source = compressedJpegWithExif(160, 90, exif)
        val orientation = ImageMetadataReader.readMetadata(source.inputStream())
            .getFirstDirectoryOfType(ExifIFD0Directory::class.java)
            ?.getInteger(ExifIFD0Directory.TAG_ORIENTATION)
        assertTrue(orientation == 6 || orientation == 8, "La muestra debe tener orientacion EXIF lateral")

        val result = ImageOptimizer.optimize(source, "jpg")
        val decoded = ImageIO.read(result.bytes.inputStream())
        assertEquals("jpg", result.extension)
        assertEquals(90, decoded.width)
        assertEquals(160, decoded.height)
        assertTrue(result.bytes.isNotEmpty())
    }

    private fun exifSegment(jpeg: ByteArray): ByteArray {
        require(jpeg.size > 4 && (jpeg[0].toInt() and 255) == 255 && (jpeg[1].toInt() and 255) == 216)
        var offset = 2
        while (offset + 6 < jpeg.size) {
            require((jpeg[offset].toInt() and 255) == 255) { "JPEG invalido" }
            val marker = jpeg[offset + 1].toInt() and 255
            if (marker == 218 || marker == 217) break // start of scan / end
            val length = ((jpeg[offset + 2].toInt() and 255) shl 8) or (jpeg[offset + 3].toInt() and 255)
            require(length >= 2 && offset + length + 2 <= jpeg.size)
            if (marker == 225 && length >= 8 &&
                String(jpeg, offset + 4, 6, Charsets.US_ASCII) == "Exif\u0000\u0000") {
                return jpeg.copyOfRange(offset, offset + length + 2)
            }
            offset += length + 2
        }
        error("La foto de referencia no contiene EXIF APP1")
    }

    private fun compressedJpegWithExif(width: Int, height: Int, exif: ByteArray): ByteArray {
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
        val jpeg = original.toByteArray()
        val output = ByteArrayOutputStream()
        output.write(jpeg, 0, 2)
        output.write(exif)
        output.write(jpeg, 2, jpeg.size - 2)
        return output.toByteArray()
    }
}
