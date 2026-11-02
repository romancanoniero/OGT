package com.onlygoodthings.backend.media

import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Removes descriptive/private metadata without recompressing pixels or flattening animations. */
internal object ImagePrivacy {
    fun strip(bytes: ByteArray, extension: String): ByteArray = when (extension) {
        "jpg" -> jpeg(bytes)
        "png" -> chunks(bytes, false)
        "webp" -> chunks(bytes, true)
        else -> throw IllegalArgumentException("Formato no compatible")
    }

    private fun jpeg(bytes: ByteArray): ByteArray {
        require(bytes.size >= 4 && bytes[0] == 0xff.toByte() && bytes[1] == 0xd8.toByte())
        val out = ByteArrayOutputStream();out.write(bytes,0,2)
        var p = 2
        while (p < bytes.size) {
            val start = p
            require(bytes[p] == 0xff.toByte()) { "Estructura JPEG inválida" }
            while (p < bytes.size && bytes[p] == 0xff.toByte()) p++
            require(p < bytes.size)
            val marker = bytes[p++].toInt() and 255
            if (marker == 0xd9) { out.write(0xff);out.write(marker);return out.toByteArray() }
            require(marker !in 0xd0..0xd8 && marker != 0 && p + 2 <= bytes.size)
            val size = ((bytes[p].toInt() and 255) shl 8) or (bytes[p+1].toInt() and 255)
            require(size >= 2 && p.toLong()+size <= bytes.size)
            val end = p + size
            val icc = marker == 0xe2 && size >= 14 && String(bytes,p+2,12,Charsets.US_ASCII)=="ICC_PROFILE\u0000"
            val drop = marker == 0xfe || (marker in 0xe1..0xef && marker != 0xee && !icc)
            if (!drop) out.write(bytes,start,end-start)
            p = end
            if (marker == 0xda) {
                val scan = p
                while (p+1 < bytes.size) {
                    if (bytes[p] != 0xff.toByte()) { p++;continue }
                    var next = p+1
                    while (next < bytes.size && bytes[next] == 0xff.toByte()) next++
                    require(next < bytes.size)
                    val code = bytes[next].toInt() and 255
                    if (code == 0 || code in 0xd0..0xd7) p=next+1 else break
                }
                out.write(bytes,scan,p-scan)
            }
        }
        throw IllegalArgumentException("JPEG incompleto")
    }

    private fun chunks(bytes: ByteArray, webp: Boolean): ByteArray {
        val header = if (webp) 12 else 8
        require(bytes.size >= header)
        val limit=if(webp)(ByteBuffer.wrap(bytes,4,4).order(ByteOrder.LITTLE_ENDIAN).int.toLong() and 0xffffffffL)+8 else bytes.size.toLong()
        require(limit in header.toLong()..bytes.size.toLong()) { "Contenedor de imagen inválido" }
        val safe = if (webp) setOf("VP8X","VP8 ","VP8L","ALPH","ICCP","ANIM","ANMF")
            else setOf("IHDR","PLTE","IDAT","IEND","tRNS","gAMA","cHRM","sRGB","iCCP","sBIT","pHYs","acTL","fcTL","fdAT")
        val out = ByteArrayOutputStream();out.write(bytes,0,header)
        val orientation=runCatching {
            com.drew.imaging.ImageMetadataReader.readMetadata(bytes.inputStream())
                .getFirstDirectoryOfType(com.drew.metadata.exif.ExifIFD0Directory::class.java)
                ?.getInteger(com.drew.metadata.exif.ExifIFD0Directory.TAG_ORIENTATION)
        }.getOrNull()?.takeIf { it in 2..8 }
        var p=header;var ended=false
        while (p.toLong()+8 <= limit) {
            val sizeOffset=if(webp)p+4 else p
            val length=ByteBuffer.wrap(bytes,sizeOffset,4).order(if(webp)ByteOrder.LITTLE_ENDIAN else ByteOrder.BIG_ENDIAN).int.toLong() and 0xffffffffL
            val total=8+length+if(webp)length%2 else 4
            require(total <= limit-p) { "Estructura de imagen inválida" }
            val type=String(bytes,if(webp)p else p+4,4,Charsets.US_ASCII)
            if(!webp) {
                val crc=java.util.zip.CRC32().apply { update(bytes,p+4,length.toInt()+4) }
                val expected=ByteBuffer.wrap(bytes,p+8+length.toInt(),4).int.toLong() and 0xffffffffL
                require(crc.value==expected) { "La imagen está dañada" }
                require(type in safe || type[0].isLowerCase()) { "La imagen contiene un bloque crítico desconocido" }
            }
            if(type in safe) {
                val chunk=bytes.copyOfRange(p,p+total.toInt())
                if(webp && type=="VP8X") { require(length>=10);chunk[8]=((chunk[8].toInt() and 0x0c.inv()) or if(orientation!=null)0x08 else 0).toByte() }
                out.write(chunk)
                if(!webp && type=="IHDR" && orientation!=null)out.write(orientationChunk(orientation,false))
            }
            p+=total.toInt()
            if(!webp && type=="IEND") { ended=true;break }
        }
        require(if(webp)p.toLong()==limit else ended) { "Imagen incompleta" }
        if(webp && orientation!=null)out.write(orientationChunk(orientation,true))
        val result=out.toByteArray()
        if(webp)ByteBuffer.wrap(result,4,4).order(ByteOrder.LITTLE_ENDIAN).putInt(result.size-8)
        return result
    }

    private fun orientationChunk(orientation: Int,webp: Boolean):ByteArray {
        // Minimal TIFF: the sole tag is orientation. No GPS, author, dates or device data.
        val tiff=ByteBuffer.allocate(26).order(ByteOrder.LITTLE_ENDIAN).apply {
            put('I'.code.toByte());put('I'.code.toByte());putShort(42);putInt(8);putShort(1)
            putShort(0x112);putShort(3);putInt(1);putShort(orientation.toShort());putShort(0);putInt(0)
        }.array()
        val type=(if(webp)"EXIF" else "eXIf").toByteArray(Charsets.US_ASCII)
        val out=ByteArrayOutputStream()
        if(webp){out.write(type);out.write(ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(tiff.size).array());out.write(tiff)}
        else {
            out.write(ByteBuffer.allocate(4).putInt(tiff.size).array());out.write(type);out.write(tiff)
            val crc=java.util.zip.CRC32().apply { update(type);update(tiff) }
            out.write(ByteBuffer.allocate(4).putInt(crc.value.toInt()).array())
        }
        return out.toByteArray()
    }
}
