package com.onlygoodthings.backend.media

import com.onlygoodthings.shared.domain.MediaKind
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.awt.Color
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.nio.file.Files
import java.nio.file.Path
import javax.imageio.ImageIO

class MediaStoreTest {
    @TempDir lateinit var root: Path
    private fun store() = MediaStore(root, "http://localhost:19083")
    private fun picture(width: Int, height: Int, alpha: Boolean = false): ByteArray {
        val image = BufferedImage(width, height, if (alpha) BufferedImage.TYPE_INT_ARGB else BufferedImage.TYPE_INT_RGB)
        image.createGraphics().apply {
            color = Color(20, 120, 80, if (alpha) 120 else 255)
            fillRect(0, 0, width, height)
            dispose()
        }
        return ByteArrayOutputStream().also { ImageIO.write(image, "png", it) }.toByteArray()
    }
    @Test fun `large image is reduced and remains readable`() {
        val input = picture(3000, 2400)
        val store = store()
        val result = store.save(input, "photo.png", "image/png")
        val bytes = Files.readAllBytes(store.resolvePublic(result.filename)!!)
        val image = ImageIO.read(bytes.inputStream())
        assertTrue(bytes.size < input.size)
        assertTrue(image.width <= 2048 && image.height <= 2048)
        assertEquals(1.25, image.width.toDouble() / image.height, 0.01)
        assertEquals(MediaKind.IMAGE, result.kind)
    }
    @Test fun `transparent image remains transparent and is not enlarged`() {
        val input = picture(120, 80, true)
        val store = store()
        val saved = store.save(input, "avatar.png", "image/png")
        val bytes = Files.readAllBytes(store.resolvePublic(saved.filename)!!)
        val decoded = ImageIO.read(bytes.inputStream())
        assertEquals(120, decoded.width)
        assertEquals(80, decoded.height)
        assertEquals(120, decoded.getRGB(5, 5) ushr 24)
        assertTrue(bytes.size <= input.size)
    }
    @Test fun `invalid image never creates a public file`() {
        val store = store()
        assertThrows(IllegalArgumentException::class.java) { store.save("not a photo".toByteArray(), "fake.jpg", "image/jpeg") }
        Files.list(store.uploadsDir()).use { assertEquals(0, it.count()) }
    }
    @Test fun `image content must agree with declared type`() {
        assertThrows(IllegalArgumentException::class.java) { store().save(picture(20, 20), "fake.jpg", "image/jpeg") }
    }
    @Test fun `video and old public names keep existing behavior`() {
        val input = byteArrayOf(1, 2, 3, 4)
        val store = store()
        val saved = store.save(input, "clip.mp4", "video/mp4")
        assertEquals(MediaKind.VIDEO, saved.kind)
        assertArrayEquals(input, Files.readAllBytes(store.resolvePublic(saved.filename)!!))
        assertEquals("video/mp4", store.contentTypeOf(saved.filename))
        assertNull(store.resolvePublic("../../private.jpg"))
        assertNull(store.resolvePublic("missing.png"))
        assertTrue(MediaStore.isSafeName("00000000-0000-4000-8000-000000000000.webp"))
    }
    @Test fun `empty and oversized uploads are rejected`() {
        assertThrows(IllegalArgumentException::class.java) { store().save(byteArrayOf(), "x.png", "image/png") }
        assertThrows(IllegalArgumentException::class.java) { store().save(ByteArray(MediaStore.MAX_BYTES + 1), "x.png", "image/png") }
    }
    private fun fixture(name: String) = javaClass.getResourceAsStream("/images/$name")!!.use { it.readBytes() }
    @Test fun `webp upload remains readable with alpha and never grows`() {
        val input = fixture("transparent.webp")
        val store = store()
        val saved = store.save(input, "alpha.webp", "image/webp")
        val bytes = Files.readAllBytes(store.resolvePublic(saved.filename)!!)
        val decoded = ImageIO.read(bytes.inputStream())
        assertEquals(320, decoded.width)
        assertEquals(200, decoded.height)
        assertTrue(decoded.getRGB(0, 0) ushr 24 < 255)
        assertTrue(bytes.size <= input.size)
    }
    @Test fun `compressed camera photo respects EXIF rotation`() {
        val input = fixture("rotated.jpg")
        val store = store()
        val saved = store.save(input, "camera.jpg", "image/jpeg")
        val bytes = Files.readAllBytes(store.resolvePublic(saved.filename)!!)
        val decoded = ImageIO.read(bytes.inputStream())
        assertTrue(bytes.size < input.size)
        assertTrue(decoded.height > decoded.width)
        assertEquals(2048, decoded.height)
    }
    @Test fun `compressed pixel bomb is rejected before decoding`() {
        val input = fixture("oversized-pixels.png")
        assertTrue(input.size < MediaStore.MAX_BYTES)
        assertThrows(IllegalArgumentException::class.java) { store().save(input, "huge.png", "image/png") }
    }
    @Test fun `all EXIF orientations preserve every pixel`() {
        val image = BufferedImage(3, 2, BufferedImage.TYPE_INT_RGB)
        for (y in 0..1) for (x in 0..2) image.setRGB(x, y, (y * 3 + x + 1) * 100)
        val expectedTopLeft = mapOf(2 to 300, 3 to 600, 4 to 400, 5 to 100, 6 to 400, 7 to 600, 8 to 300)
        for (orientation in 2..8) {
            val result = ImageOptimizer.orient(image, orientation)
            assertEquals(expectedTopLeft[orientation], result.getRGB(0, 0) and 0xffffff)
            val values = (0 until result.height).flatMap { y -> (0 until result.width).map { x -> result.getRGB(x, y) and 0xffffff } }
            assertEquals((1..6).map { it * 100 }, values.sorted())
        }
    }
    @Test fun `animated webp is preserved instead of flattening its frames`() {
        val input = fixture("animated.webp")
        val store = store()
        val saved = store.save(input, "animation.webp", "image/webp")
        assertArrayEquals(input, Files.readAllBytes(store.resolvePublic(saved.filename)!!))
        assertEquals("image/webp", store.contentTypeOf(saved.filename))
    }
}
