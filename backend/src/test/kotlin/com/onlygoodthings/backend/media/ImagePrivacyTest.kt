package com.onlygoodthings.backend.media

import com.drew.imaging.ImageMetadataReader
import com.drew.metadata.exif.GpsDirectory
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.imageio.IIOImage
import javax.imageio.ImageIO
import javax.imageio.ImageWriteParam
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class ImagePrivacyTest {
    @Test fun webpDoesNotPromoteTrailingBytesIntoVisibleImageContent() {
        val source=javaClass.getResourceAsStream("/images/animated.webp")!!.readBytes()
        val payload="Hidden frame outside the RIFF container".toByteArray()
        val suffix="ANMF".toByteArray()+ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(payload.size).array()+payload+(if(payload.size%2==1)byteArrayOf(0)else byteArrayOf())
        assertArrayEquals(source,ImagePrivacy.strip(source+suffix,"webp"))
    }
    private fun gpsExif():ByteArray = ByteBuffer.allocate(92).order(ByteOrder.LITTLE_ENDIAN).apply {
        put('I'.code.toByte());put('I'.code.toByte());putShort(42);putInt(8);putShort(2)
        putShort(0x112);putShort(3);putInt(1);putShort(1);putShort(0)
        putShort(0x8825.toShort());putShort(4);putInt(1);putInt(38);putInt(0)
        putShort(2)
        putShort(1);putShort(2);putInt(2);put('N'.code.toByte());put(0);putShort(0)
        putShort(2);putShort(5);putInt(3);putInt(68);putInt(0)
        putInt(34);putInt(1);putInt(35);putInt(1);putInt(22);putInt(1)
    }.array()

    @Test fun anAlreadySmallJpegLosesGpsEvenWhenRecompressionWouldMakeItLarger() {
        val image=BufferedImage(96,64,BufferedImage.TYPE_INT_RGB)
        val random=java.util.Random(42)
        for(y in 0 until image.height)for(x in 0 until image.width)image.setRGB(x,y,random.nextInt(0x1000000))
        val compressed=ByteArrayOutputStream()
        val writer=ImageIO.getImageWritersByFormatName("jpg").next()
        try { ImageIO.createImageOutputStream(compressed).use { output ->
            writer.output=output
            val params=writer.defaultWriteParam.apply { compressionMode=ImageWriteParam.MODE_EXPLICIT;compressionQuality=0.02f }
            writer.write(null,IIOImage(image,null,null),params)
        } } finally { writer.dispose() }
        val payload="Exif\u0000\u0000".toByteArray()+gpsExif()
        val segment=byteArrayOf(0xff.toByte(),0xe1.toByte())+ByteBuffer.allocate(2).putShort((payload.size+2).toShort()).array()+payload
        val original=compressed.toByteArray()
        val source=original.copyOfRange(0,2)+segment+original.copyOfRange(2,original.size)
        assertNotNull(ImageMetadataReader.readMetadata(source.inputStream()).getFirstDirectoryOfType(GpsDirectory::class.java))
        val result=ImageOptimizer.optimize(source,"jpg")
        assertNull(ImageMetadataReader.readMetadata(result.bytes.inputStream()).getFirstDirectoryOfType(GpsDirectory::class.java))
        assertArrayEquals(original,result.bytes,"The smaller JPEG pixel stream is retained without its private APP1 segment")
        assertEquals(96,ImageIO.read(result.bytes.inputStream()).width)
    }

    private fun pngChunk(type:String,data:ByteArray):ByteArray {
        val name=type.toByteArray(Charsets.US_ASCII)
        val crc=java.util.zip.CRC32().apply { update(name);update(data) }
        return ByteBuffer.allocate(4).putInt(data.size).array()+name+data+ByteBuffer.allocate(4).putInt(crc.value.toInt()).array()
    }
    @Test fun sanitizingPreservesOnlyTheOrientationNeededToDisplayAnimatedContainersCorrectly() {
        val exif=gpsExif().also { ByteBuffer.wrap(it,18,2).order(ByteOrder.LITTLE_ENDIAN).putShort(6) }
        val png=ByteArrayOutputStream().also { ImageIO.write(BufferedImage(2,3,BufferedImage.TYPE_INT_RGB),"png",it) }.toByteArray()
        val firstChunkEnd=8+12+13
        val pngTagged=png.copyOfRange(0,firstChunkEnd)+pngChunk("eXIf",exif)+png.copyOfRange(firstChunkEnd,png.size)
        val webp=javaClass.getResourceAsStream("/images/animated.webp")!!.readBytes()
        val payload="Exif\u0000\u0000".toByteArray()+exif
        val taggedWebp=webp+"EXIF".toByteArray()+ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(payload.size).array()+payload
        ByteBuffer.wrap(taggedWebp,4,4).order(ByteOrder.LITTLE_ENDIAN).putInt(taggedWebp.size-8)
        taggedWebp[20]=(taggedWebp[20].toInt() or 8).toByte()
        for((extension,tagged)in listOf("png" to pngTagged,"webp" to taggedWebp)) {
            val before=ImageMetadataReader.readMetadata(tagged.inputStream())
            assertEquals(6,before.getFirstDirectoryOfType(com.drew.metadata.exif.ExifIFD0Directory::class.java)?.getInteger(com.drew.metadata.exif.ExifIFD0Directory.TAG_ORIENTATION),extension)
            val result=ImagePrivacy.strip(tagged,extension)
            val metadata=ImageMetadataReader.readMetadata(result.inputStream())
            assertNull(metadata.getFirstDirectoryOfType(GpsDirectory::class.java),extension)
            assertEquals(6,metadata.getFirstDirectoryOfType(com.drew.metadata.exif.ExifIFD0Directory::class.java)?.getInteger(com.drew.metadata.exif.ExifIFD0Directory.TAG_ORIENTATION),extension)
        }
    }
    private fun chunks(bytes:ByteArray,webp:Boolean):List<Pair<String,ByteArray>> {
        var p=if(webp)12 else 8
        val result=mutableListOf<Pair<String,ByteArray>>()
        while(p+8<=bytes.size) {
            val length=ByteBuffer.wrap(bytes,if(webp)p+4 else p,4).order(if(webp)ByteOrder.LITTLE_ENDIAN else ByteOrder.BIG_ENDIAN).int
            val type=String(bytes,if(webp)p else p+4,4,Charsets.US_ASCII)
            result+=type to bytes.copyOfRange(p+8,p+8+length)
            p+=8+length+if(webp)length%2 else 4
        }
        return result
    }
    @Test fun animatedPngKeepsBothFramesAndTimingButNotGpsOrText() {
        val png=ByteArrayOutputStream().also { ImageIO.write(BufferedImage(2,2,BufferedImage.TYPE_INT_ARGB),"png",it) }.toByteArray()
        val original=chunks(png,false)
        fun frame(seq:Int)=ByteBuffer.allocate(26).putInt(seq).putInt(2).putInt(2).putInt(0).putInt(0).putShort(1).putShort(10).put(0).put(0).array()
        val source=png.copyOfRange(0,8)+pngChunk("IHDR",original.first { it.first=="IHDR" }.second)+
            pngChunk("eXIf",gpsExif())+pngChunk("tEXt","GPS\u0000private-place".toByteArray())+
            pngChunk("acTL",ByteBuffer.allocate(8).putInt(2).putInt(0).array())+pngChunk("fcTL",frame(0))+
            pngChunk("IDAT",original.first { it.first=="IDAT" }.second)+pngChunk("fcTL",frame(1))+
            pngChunk("fdAT",ByteBuffer.allocate(4).putInt(2).array()+original.first { it.first=="IDAT" }.second)+pngChunk("IEND",byteArrayOf())
        val result=ImageOptimizer.optimize(source,"png")
        val before=chunks(source,false).filter { it.first in setOf("acTL","fcTL","IDAT","fdAT") }
        val after=chunks(result.bytes,false)
        val animation=after.filter { it.first in setOf("acTL","fcTL","IDAT","fdAT") }
        assertEquals(before.map { it.first },animation.map { it.first })
        before.zip(animation).forEach { (a,b)->assertArrayEquals(a.second,b.second) }
        assertFalse(after.any { it.first in setOf("eXIf","tEXt","iTXt","zTXt") })
        assertEquals(2,ImageIO.read(result.bytes.inputStream()).width)
    }
    @Test fun animatedWebpKeepsAnimationChunksAndCorrectRiffLengthWithoutExifAndXmp() {
        val source=javaClass.getResourceAsStream("/images/animated.webp")!!.readBytes()
        fun chunk(type:String,data:ByteArray)=type.toByteArray()+ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(data.size).array()+data+(if(data.size%2==1)byteArrayOf(0)else byteArrayOf())
        val tagged=source+chunk("EXIF","Exif\u0000\u0000".toByteArray()+gpsExif())+chunk("XMP ","private-location".toByteArray())
        ByteBuffer.wrap(tagged,4,4).order(ByteOrder.LITTLE_ENDIAN).putInt(tagged.size-8)
        val result=ImageOptimizer.optimize(tagged,"webp")
        val before=chunks(source,true).filter { it.first in setOf("ANIM","ANMF") }
        val after=chunks(result.bytes,true)
        assertFalse(after.any { it.first in setOf("EXIF","XMP ") })
        val animation=after.filter { it.first in setOf("ANIM","ANMF") }
        assertEquals(before.size,animation.size)
        before.zip(animation).forEach { (a,b)->assertArrayEquals(a.second,b.second) }
        assertEquals(result.bytes.size-8,ByteBuffer.wrap(result.bytes,4,4).order(ByteOrder.LITTLE_ENDIAN).int)
    }
}
