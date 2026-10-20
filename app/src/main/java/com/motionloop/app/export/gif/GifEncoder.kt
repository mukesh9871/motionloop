package com.motionloop.app.export.gif

import java.io.OutputStream

/** Minimal GIF89a writer: one global 256-color palette, infinite loop, full-frame updates. */
class GifEncoder(
    private val out: OutputStream,
    private val width: Int,
    private val height: Int,
    palette: IntArray,
) {
    init {
        require(palette.size == 256) { "Palette must have 256 entries" }
        out.write("GIF89a".toByteArray(Charsets.US_ASCII))
        writeShort(width)
        writeShort(height)
        out.write(0xF7) // global color table, 8 bits color resolution, 256 entries
        out.write(0) // background color index
        out.write(0) // pixel aspect ratio
        for (c in palette) {
            out.write(c shr 16 and 255)
            out.write(c shr 8 and 255)
            out.write(c and 255)
        }
        // NETSCAPE2.0 application extension: loop forever.
        out.write(byteArrayOf(0x21, 0xFF.toByte(), 0x0B))
        out.write("NETSCAPE2.0".toByteArray(Charsets.US_ASCII))
        out.write(byteArrayOf(0x03, 0x01, 0x00, 0x00, 0x00))
    }

    fun writeFrame(indices: ByteArray, delayCs: Int) {
        require(indices.size == width * height)
        // Graphic control extension: disposal "do not dispose", no transparency.
        out.write(byteArrayOf(0x21, 0xF9.toByte(), 0x04, 0x04))
        writeShort(delayCs)
        out.write(0)
        out.write(0)
        // Image descriptor, full frame, no local color table.
        out.write(0x2C)
        writeShort(0)
        writeShort(0)
        writeShort(width)
        writeShort(height)
        out.write(0)
        LzwWriter(out).encode(indices)
    }

    fun finish() {
        out.write(0x3B)
        out.flush()
    }

    private fun writeShort(v: Int) {
        out.write(v and 255)
        out.write(v shr 8 and 255)
    }
}

/**
 * GIF LZW for 8-bit indices. Code width grows from 9 to 12 bits, table is reset when full.
 * Bit packing and width growth follow the classic GIF encoder behaviour that every decoder expects.
 */
internal class LzwWriter(private val out: OutputStream) {
    private val block = ByteArray(255)
    private var blockLen = 0
    private var bitBuffer = 0
    private var bitCount = 0

    private var codeSize = INIT_BITS
    private var nextCode = FIRST_CODE
    private var resetPending = false

    // Open addressing: key = prefix shl 8 or byte, value = code.
    private val hashKeys = IntArray(HASH_SIZE)
    private val hashCodes = IntArray(HASH_SIZE)

    fun encode(data: ByteArray) {
        out.write(MIN_CODE_SIZE)
        clearTable()
        output(CLEAR)
        if (data.isNotEmpty()) {
            var prefix = data[0].toInt() and 255
            for (i in 1 until data.size) {
                val c = data[i].toInt() and 255
                val key = (prefix shl 8) or c
                val found = lookup(key)
                if (found >= 0) {
                    prefix = found
                    continue
                }
                output(prefix)
                if (nextCode < MAX_CODES) {
                    insert(key, nextCode++)
                } else {
                    clearTable()
                    resetPending = true
                    output(CLEAR)
                }
                prefix = c
            }
            output(prefix)
        }
        output(EOI)
        if (bitCount > 0) addByte(bitBuffer and 255)
        flushBlock()
        out.write(0) // block terminator
    }

    private fun output(code: Int) {
        bitBuffer = bitBuffer or (code shl bitCount)
        bitCount += codeSize
        while (bitCount >= 8) {
            addByte(bitBuffer and 255)
            bitBuffer = bitBuffer ushr 8
            bitCount -= 8
        }
        if (resetPending) {
            codeSize = INIT_BITS
            resetPending = false
        } else if (nextCode > (1 shl codeSize) - 1 && codeSize < MAX_BITS) {
            codeSize++
        }
    }

    private fun clearTable() {
        hashKeys.fill(-1)
        nextCode = FIRST_CODE
    }

    private fun lookup(key: Int): Int {
        var h = hash(key)
        while (true) {
            val k = hashKeys[h]
            if (k == -1) return -1
            if (k == key) return hashCodes[h]
            h = (h + 1) and (HASH_SIZE - 1)
        }
    }

    private fun insert(key: Int, code: Int) {
        var h = hash(key)
        while (hashKeys[h] != -1) h = (h + 1) and (HASH_SIZE - 1)
        hashKeys[h] = key
        hashCodes[h] = code
    }

    private fun hash(key: Int) = (key * -0x61c88647 ushr 18) and (HASH_SIZE - 1)

    private fun addByte(b: Int) {
        block[blockLen++] = b.toByte()
        if (blockLen == 255) flushBlock()
    }

    private fun flushBlock() {
        if (blockLen == 0) return
        out.write(blockLen)
        out.write(block, 0, blockLen)
        blockLen = 0
    }

    private companion object {
        const val MIN_CODE_SIZE = 8
        const val CLEAR = 256
        const val EOI = 257
        const val FIRST_CODE = 258
        const val INIT_BITS = 9
        const val MAX_BITS = 12
        const val MAX_CODES = 4096
        const val HASH_SIZE = 16384 // power of two, load factor stays under 0.25
    }
}
