package com.motionloop.app.domain.model

import com.motionloop.ml.AlphaMask
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.DeflaterOutputStream
import java.util.zip.InflaterInputStream

/**
 * Small binary format for masks: "MLMK", version, width, height, then deflated bytes.
 * Masks are mostly flat areas, so they compress to a few KB.
 */
object MaskCodec {
    private const val MAGIC = 0x4D4C4D4B // "MLMK"
    private const val VERSION = 1
    private const val MAX_SIDE = 8192

    fun write(mask: AlphaMask, out: OutputStream) {
        val data = DataOutputStream(out)
        data.writeInt(MAGIC)
        data.writeInt(VERSION)
        data.writeInt(mask.width)
        data.writeInt(mask.height)
        data.flush()
        val deflater = DeflaterOutputStream(out)
        deflater.write(mask.data)
        deflater.finish()
        out.flush()
    }

    fun read(input: InputStream): AlphaMask {
        val data = DataInputStream(input)
        if (data.readInt() != MAGIC) throw IOException("Not a mask file")
        if (data.readInt() != VERSION) throw IOException("Unsupported mask version")
        val w = data.readInt()
        val h = data.readInt()
        if (w !in 1..MAX_SIDE || h !in 1..MAX_SIDE) throw IOException("Bad mask size $w x $h")
        val bytes = ByteArray(w * h)
        val inflater = InflaterInputStream(input)
        var off = 0
        while (off < bytes.size) {
            val n = inflater.read(bytes, off, bytes.size - off)
            if (n < 0) throw IOException("Mask file is truncated")
            off += n
        }
        return AlphaMask(w, h, bytes)
    }
}
