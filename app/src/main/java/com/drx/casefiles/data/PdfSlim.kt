package com.drx.casefiles.data

import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.Locale
import java.util.zip.InflaterInputStream

/**
 * Android's PdfDocument stores every picture as raw pixels (lossless Flate), so even small images make big files.
 * This pass opens the finished PDF and re-encodes each opaque RGB image as JPEG, then rebuilds the xref table.
 * It is deliberately cautious: anything unexpected (object streams, indirect lengths, odd image types, a result
 * that is not smaller, or a file PdfRenderer cannot open) leaves the original file untouched.
 * The same algorithm was checked against a Skia-shaped PDF with qpdf and pypdf.
 */
internal object PdfSlim {
    private val OBJ_START = Regex("(?m)^(\\d+) 0 obj")
    private val OBJ_HDR = Regex("(\\d+) 0 obj")
    private val LENGTH = Regex("/Length\\s+(\\d+)(?!\\d|\\s+\\d+\\s+R)")
    private val SIZE = Regex("/Size\\s+(\\d+)")
    private val IS_IMAGE = Regex("/Subtype\\s*/Image")
    private val IS_RGB = Regex("/ColorSpace\\s*/DeviceRGB")
    private val IS_FLATE = Regex("/Filter\\s*(\\[\\s*)?/FlateDecode")
    private val IS_8BIT = Regex("/BitsPerComponent\\s+8")
    private val NO_GO = Regex("/SMask|/Mask|/DecodeParms|/Predictor|/Decode\\s|/ImageMask")
    private val WIDTH = Regex("/Width\\s+(\\d+)")
    private val HEIGHT = Regex("/Height\\s+(\\d+)")
    private val LENGTH_ANY = Regex("/Length\\s+\\d+")

    /** Replaces [file] by a smaller, valid version of itself. Returns true when that happened. */
    fun slim(file: File, jpegQuality: Int, pages: Int): Boolean {
        val tmp = File(file.parentFile, file.name + ".slim")
        return try {
            val ok = rewrite(file.readBytes(), jpegQuality, tmp) &&
                tmp.length() > 0 && tmp.length() < file.length() &&
                opens(tmp, pages) && tmp.renameTo(file)
            if (!ok) tmp.delete()
            ok
        } catch (e: Throwable) {
            tmp.delete()
            false
        }
    }

    private fun skipWs(t: String, from: Int): Int {
        var p = from
        while (p < t.length && (t[p] == ' ' || t[p] == '\n' || t[p] == '\r' || t[p] == '\t' || t[p] == '\u000c' || t[p] == '\u0000')) p++
        return p
    }

    private fun rewrite(bytes: ByteArray, quality: Int, dest: File): Boolean {
        // ISO-8859-1 maps one byte to one char, so string indexes are byte offsets.
        val t = String(bytes, Charsets.ISO_8859_1)
        if (t.contains("/ObjStm") || t.contains("/Type /XRef")) return false
        val first = OBJ_START.find(t) ?: return false
        val out = ByteArrayOutputStream(bytes.size)
        out.write(bytes, 0, first.range.first)
        val offsets = HashMap<Int, Int>()
        var pos = first.range.first
        var changed = 0
        var p = pos
        while (true) {
            p = skipWs(t, pos)
            if (t.startsWith("xref", p)) break
            val hm = OBJ_HDR.find(t, p)
            if (hm == null || hm.range.first != p) return false
            val num = hm.groupValues[1].toInt()
            val body = hm.range.last + 1
            val endObj0 = t.indexOf("endobj", body)
            if (endObj0 < 0) return false
            val sIdx = t.indexOf("stream", body)
            val isStream = sIdx in 0 until endObj0
            var dict = ""
            var dataStart = 0
            var length = 0
            val objEnd: Int
            if (isStream) {
                dict = t.substring(body, sIdx)
                val lm = LENGTH.find(dict) ?: return false
                length = lm.groupValues[1].toInt()
                dataStart = sIdx + 6
                if (dataStart < t.length && t[dataStart] == '\r') dataStart++
                if (dataStart < t.length && t[dataStart] == '\n') dataStart++
                val dataEnd = dataStart + length
                if (dataEnd > t.length || !t.startsWith("endstream", skipWs(t, dataEnd))) return false
                val eo = t.indexOf("endobj", dataEnd)
                if (eo < 0) return false
                objEnd = eo + 6
            } else {
                objEnd = endObj0 + 6
            }
            offsets[num] = out.size()
            val replaced = if (isStream) toJpeg(dict, bytes, dataStart, length, num, quality) else null
            if (replaced != null) {
                out.write(replaced)
                changed++
            } else {
                out.write(bytes, p, objEnd - p)
            }
            out.write('\n'.code)
            pos = objEnd
        }
        if (changed == 0) return false
        val ti = t.indexOf("trailer", p)
        if (ti < 0) return false
        val sx = t.indexOf("startxref", ti)
        if (sx < 0) return false
        val trailer = t.substring(ti + 7, sx).trim()
        val size = SIZE.find(trailer)?.groupValues?.get(1)?.toInt() ?: return false
        if (offsets.size != size - 1) return false
        for (i in 1 until size) if (!offsets.containsKey(i)) return false
        val xrefPos = out.size()
        val xref = StringBuilder("xref\n0 $size\n0000000000 65535 f \n")
        for (i in 1 until size) xref.append(String.format(Locale.US, "%010d 00000 n \n", offsets[i]))
        xref.append("trailer\n").append(trailer).append("\nstartxref\n").append(xrefPos).append("\n%%EOF\n")
        out.write(xref.toString().toByteArray(Charsets.ISO_8859_1))
        dest.writeBytes(out.toByteArray())
        return true
    }

    /** A complete replacement object when [dict] is a plain opaque RGB image that JPEG makes smaller, else null. */
    private fun toJpeg(dict: String, bytes: ByteArray, dataStart: Int, length: Int, num: Int, quality: Int): ByteArray? {
        if (!IS_IMAGE.containsMatchIn(dict) || !IS_RGB.containsMatchIn(dict) || !IS_FLATE.containsMatchIn(dict) || !IS_8BIT.containsMatchIn(dict)) return null
        if (NO_GO.containsMatchIn(dict)) return null
        val w = WIDTH.find(dict)?.groupValues?.get(1)?.toInt() ?: return null
        val h = HEIGHT.find(dict)?.groupValues?.get(1)?.toInt() ?: return null
        if (w <= 0 || h <= 0) return null
        val raw = InflaterInputStream(ByteArrayInputStream(bytes, dataStart, length)).use { it.readBytes() }
        if (raw.size != w * h * 3) return null
        val px = IntArray(w * h)
        var k = 0
        for (i in px.indices) {
            px[i] = (0xFF shl 24) or ((raw[k].toInt() and 0xFF) shl 16) or ((raw[k + 1].toInt() and 0xFF) shl 8) or (raw[k + 2].toInt() and 0xFF)
            k += 3
        }
        val bmp = Bitmap.createBitmap(px, w, h, Bitmap.Config.ARGB_8888)
        val jpg = ByteArrayOutputStream()
        val okEnc = bmp.compress(Bitmap.CompressFormat.JPEG, quality, jpg)
        bmp.recycle()
        if (!okEnc || jpg.size() >= length) return null
        val newDict = LENGTH_ANY.replaceFirst(dict.trim().replace("/FlateDecode", "/DCTDecode"), "/Length ${jpg.size()}")
        val head = "$num 0 obj\n$newDict\nstream\n".toByteArray(Charsets.ISO_8859_1)
        val tail = "\nendstream\nendobj".toByteArray(Charsets.ISO_8859_1)
        return head + jpg.toByteArray() + tail
    }

    /** The result must open and render (first and last page) with the system PDF renderer, and have the same page count. */
    private fun opens(f: File, pages: Int): Boolean {
        val pfd = ParcelFileDescriptor.open(f, ParcelFileDescriptor.MODE_READ_ONLY)
        try {
            val r = PdfRenderer(pfd)
            try {
                if (r.pageCount != pages) return false
                for (i in listOf(0, pages - 1).distinct()) {
                    val pg = r.openPage(i)
                    try {
                        val bmp = Bitmap.createBitmap(120, 170, Bitmap.Config.ARGB_8888)
                        pg.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        bmp.recycle()
                    } finally {
                        pg.close()
                    }
                }
                return true
            } finally {
                r.close()
            }
        } finally {
            pfd.close()
        }
    }
}
