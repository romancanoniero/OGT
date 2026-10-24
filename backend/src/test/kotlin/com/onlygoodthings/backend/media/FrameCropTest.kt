package com.onlygoodthings.backend.media

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.awt.Color
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.nio.file.Files
import javax.imageio.ImageIO

class FrameCropTest {
    @Test
    fun unRetratoCentradoSaleEnDieciseisNovenos() {
        val image = BufferedImage(100, 200, BufferedImage.TYPE_INT_RGB)
        val canvas = image.createGraphics()
        canvas.color = Color.RED
        canvas.fillRect(0, 0, 100, 72)
        canvas.color = Color.GREEN
        canvas.fillRect(0, 72, 100, 56)
        canvas.color = Color.BLUE
        canvas.fillRect(0, 128, 100, 72)
        canvas.dispose()
        val png = ByteArrayOutputStream()
        assertTrue(ImageIO.write(image, "png", png))
        val jpeg = FrameCrop.jpeg(png.toByteArray(), 0.5, 0.5, 1.0)
        assertEquals(0xFF.toByte(), jpeg[0])
        assertEquals(0xD8.toByte(), jpeg[1])
        val out = ImageIO.read(jpeg.inputStream())
        assertEquals(FrameCrop.OUT_WIDTH, out.width)
        assertEquals(FrameCrop.OUT_HEIGHT, out.height)
        val pixel = out.getRGB(out.width / 2, out.height / 2)
        val red = (pixel shr 16) and 0xFF
        val green = (pixel shr 8) and 0xFF
        val blue = pixel and 0xFF
        assertTrue(green > 200, "verde=$green")
        assertTrue(red < 40, "rojo=$red")
        assertTrue(blue < 40, "azul=$blue")
    }

    @Test
    fun elDiscoSoloLeeUnNombreLocal() {
        val root = Files.createTempDirectory("ogt-frame")
        val store = MediaStore(root, "http://127.0.0.1:19081")
        assertNull(store.readLocal("https://example.com/secret.jpg"))
        assertNull(store.readLocal("file:///etc/passwd"))
        assertNull(store.storedName("https://example.com/media/u/../passwd.jpg"))
        assertNull(store.storedName("/media/u/no-es-uuid.jpg"))
    }
}
