package com.motionloop.ml

import android.content.Context
import android.graphics.Bitmap
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel

/**
 * Monocular depth with MiDaS v2.1 small (LiteRT). Output is relative inverse depth,
 * normalized to 0..255 with 255 = nearest. Blocks on inference: call from a background dispatcher.
 */
class DepthEstimator(private val context: Context) {

    private val lock = Mutex()
    private var interpreter: Interpreter? = null

    suspend fun estimate(image: Bitmap): AlphaMask = lock.withLock {
        val tflite = interpreter()
        val inShape = tflite.getInputTensor(0).shape() // [1, H, W, 3]
        val size = inShape[1]
        val input = ByteBuffer.allocateDirect(size * size * 3 * 4).order(ByteOrder.nativeOrder())
        val scaled = Bitmap.createScaledBitmap(image, size, size, true)
        val pixels = IntArray(size * size)
        scaled.getPixels(pixels, 0, size, 0, 0, size, size)
        if (scaled !== image) scaled.recycle()
        for (c in pixels) {
            input.putFloat(((c shr 16 and 255) / 255f - MEAN[0]) / STD[0])
            input.putFloat(((c shr 8 and 255) / 255f - MEAN[1]) / STD[1])
            input.putFloat(((c and 255) / 255f - MEAN[2]) / STD[2])
        }
        input.rewind()

        val outShape = tflite.getOutputTensor(0).shape() // [1, H, W] or [1, H, W, 1]
        val outH = outShape[1]
        val outW = outShape[2]
        val output = ByteBuffer.allocateDirect(outH * outW * 4).order(ByteOrder.nativeOrder())
        tflite.run(input, output)
        output.rewind()
        val values = FloatArray(outH * outW)
        output.asFloatBuffer().get(values)
        normalize(values)
        AlphaMask.fromFloats(outW, outH, values)
    }

    fun close() {
        interpreter?.close()
        interpreter = null
    }

    private fun interpreter(): Interpreter = interpreter ?: run {
        val fd = context.assets.openFd(MODEL)
        val buffer = FileInputStream(fd.fileDescriptor).use { stream ->
            stream.channel.map(FileChannel.MapMode.READ_ONLY, fd.startOffset, fd.declaredLength)
        }
        fd.close()
        Interpreter(buffer, Interpreter.Options().setNumThreads(4))
    }.also { interpreter = it }

    companion object {
        private const val MODEL = "midas_small.tflite"
        private val MEAN = floatArrayOf(0.485f, 0.456f, 0.406f)
        private val STD = floatArrayOf(0.229f, 0.224f, 0.225f)

        /** Min-max to 0..1 in place. Internal for tests. */
        fun normalize(values: FloatArray) {
            var lo = Float.MAX_VALUE
            var hi = -Float.MAX_VALUE
            for (v in values) {
                if (v < lo) lo = v
                if (v > hi) hi = v
            }
            val range = hi - lo
            for (i in values.indices) values[i] = if (range > 1e-6f) (values[i] - lo) / range else 0.5f
        }
    }
}
