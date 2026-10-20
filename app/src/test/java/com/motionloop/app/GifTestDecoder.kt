package com.motionloop.app

/**
 * Independent GIF89a reader for tests (no javax.imageio on the Android unit test classpath).
 * Decodes the global palette and each frame's index data, following the GIF spec LZW rules.
 */
class GifTestDecoder(private val bytes: ByteArray) {
    var width = 0
        private set
    var height = 0
        private set
    val palette = IntArray(256)
    val frames = mutableListOf<ByteArray>()
    val delays = mutableListOf<Int>()
    var loops = false
        private set

    private var pos = 0

    init {
        check(String(bytes, 0, 6, Charsets.US_ASCII) == "GIF89a") { "Bad signature" }
        pos = 6
        width = u16()
        height = u16()
        val packed = u8()
        u8() // background
        u8() // aspect
        check(packed and 0x80 != 0) { "No global color table" }
        val tableSize = 2 shl (packed and 7)
        for (i in 0 until tableSize) palette[i] = (u8() shl 16) or (u8() shl 8) or u8()

        var pendingDelay = 0
        loop@ while (true) {
            when (u8()) {
                0x21 -> {
                    val label = u8()
                    val blocks = subBlocks()
                    if (label == 0xF9) pendingDelay = (blocks[1].toInt() and 255) or ((blocks[2].toInt() and 255) shl 8)
                    if (label == 0xFF && String(blocks, 0, 11, Charsets.US_ASCII) == "NETSCAPE2.0") loops = true
                }
                0x2C -> {
                    u16(); u16()
                    val w = u16()
                    val h = u16()
                    check(u8() and 0x80 == 0) { "Unexpected local color table" }
                    val minCode = u8()
                    frames += lzwDecode(subBlocks(), minCode, w * h)
                    delays += pendingDelay
                }
                0x3B -> break@loop
                else -> error("Unknown block at $pos")
            }
        }
    }

    private fun u8() = bytes[pos++].toInt() and 255
    private fun u16() = u8() or (u8() shl 8)

    /** Concatenated data of a sub-block chain (each block: length byte, data, ends with 0). */
    private fun subBlocks(): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        while (true) {
            val len = u8()
            if (len == 0) return out.toByteArray()
            out.write(bytes, pos, len)
            pos += len
        }
    }

    private fun lzwDecode(data: ByteArray, minCodeSize: Int, pixelCount: Int): ByteArray {
        val clear = 1 shl minCodeSize
        val eoi = clear + 1
        val prefix = IntArray(4096)
        val suffix = IntArray(4096)
        val firstChar = IntArray(4096)
        for (i in 0 until clear) {
            suffix[i] = i
            firstChar[i] = i
        }
        val out = ByteArray(pixelCount)
        var outPos = 0
        val stack = IntArray(4097)

        var codeSize = minCodeSize + 1
        var next = eoi + 1
        var prev = -1
        var bitPos = 0
        val totalBits = data.size * 8

        fun readCode(): Int {
            var code = 0
            for (i in 0 until codeSize) {
                check(bitPos < totalBits) { "Ran out of LZW data" }
                val bit = (data[bitPos shr 3].toInt() shr (bitPos and 7)) and 1
                code = code or (bit shl i)
                bitPos++
            }
            return code
        }

        fun emit(code: Int) {
            var sp = 0
            var c = code
            while (c >= clear) {
                stack[sp++] = suffix[c]
                c = prefix[c]
            }
            stack[sp++] = c
            while (sp > 0) {
                check(outPos < pixelCount) { "Too many pixels" }
                out[outPos++] = stack[--sp].toByte()
            }
        }

        while (true) {
            val code = readCode()
            if (code == clear) {
                codeSize = minCodeSize + 1
                next = eoi + 1
                prev = -1
                continue
            }
            if (code == eoi) break
            if (prev == -1) {
                emit(code)
                prev = code
                continue
            }
            check(code <= next) { "Invalid code $code (next $next)" }
            val first = if (code < next) firstChar[code] else firstChar[prev]
            if (next < 4096) {
                prefix[next] = prev
                suffix[next] = first
                firstChar[next] = firstChar[prev]
                next++
            }
            emit(code)
            if (next >= (1 shl codeSize) && codeSize < 12) codeSize++
            prev = code
        }
        check(outPos == pixelCount) { "Decoded $outPos of $pixelCount pixels" }
        return out
    }
}
