package com.motionloop.app.render.text

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import com.motionloop.app.domain.model.TextAnimation
import com.motionloop.app.domain.model.TextLayer
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min

/**
 * A text layer drawn to bitmaps. [refWidth] is the image width the font size was resolved against,
 * so the GPU can size the quad in normalized image units. Bitmaps are premultiplied ARGB.
 */
class RenderedText(
    val key: TextLayer,
    val main: Bitmap,
    /** Soft halo for the glow pulse, same size as [main]. */
    val glow: Bitmap?,
    /** Typewriter order: red = (character index + 1) / count, same size as [main]. */
    val order: Bitmap?,
    val refWidth: Int,
) {
    fun recycle() {
        main.recycle()
        glow?.recycle()
        order?.recycle()
    }
}

/** Canvas text drawing. CPU work: call off the main thread and never per frame. */
object TextRasterizer {

    fun render(layer: TextLayer, refWidth: Int): RenderedText {
        val key = layer.renderKey()
        val textSize = max(4f, layer.size * refWidth)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.textSize = textSize
            typeface = typefaceFor(layer)
            letterSpacing = layer.letterSpacing
            color = layer.color
        }
        val lines = layer.text.take(TextLayer.MAX_LENGTH).ifEmpty { " " }.split('\n')
        val metrics = paint.fontMetrics
        val lineHeight = metrics.descent - metrics.ascent
        val pad = textSize * 0.6f // room for outline, shadow and glow
        val width = lines.maxOf { paint.measureText(it) }
        val bw = min(MAX_SIDE, ceil(width + 2 * pad).toInt()).coerceAtLeast(1)
        val bh = min(MAX_SIDE, ceil(lineHeight * lines.size + 2 * pad).toInt()).coerceAtLeast(1)

        fun baseline(i: Int) = pad - metrics.ascent + i * lineHeight
        fun left(line: String) = (bw - paint.measureText(line)) / 2f

        val main = Bitmap.createBitmap(bw, bh, Bitmap.Config.ARGB_8888)
        Canvas(main).apply {
            if (layer.outline) {
                val stroke = Paint(paint).apply {
                    style = Paint.Style.STROKE
                    strokeWidth = textSize * 0.08f
                    strokeJoin = Paint.Join.ROUND
                    color = layer.outlineColor
                }
                lines.forEachIndexed { i, line -> drawText(line, left(line), baseline(i), stroke) }
            }
            val fill = Paint(paint)
            if (layer.shadow) fill.setShadowLayer(textSize * 0.06f, textSize * 0.03f, textSize * 0.04f, 0x99000000.toInt())
            lines.forEachIndexed { i, line -> drawText(line, left(line), baseline(i), fill) }
        }

        val glow = if (key.animation == TextAnimation.GLOW_PULSE) {
            Bitmap.createBitmap(bw, bh, Bitmap.Config.ARGB_8888).also { bmp ->
                val halo = Paint(paint).apply { setShadowLayer(textSize * 0.35f, 0f, 0f, layer.color) }
                Canvas(bmp).apply { lines.forEachIndexed { i, line -> drawText(line, left(line), baseline(i), halo) } }
            }
        } else {
            null
        }

        val order = if (key.animation == TextAnimation.TYPEWRITER) {
            Bitmap.createBitmap(bw, bh, Bitmap.Config.ARGB_8888).also { bmp ->
                drawOrder(Canvas(bmp), lines, paint, ::left, ::baseline)
            }
        } else {
            null
        }
        return RenderedText(key, main, glow, order, refWidth)
    }

    /** Each character in reading order gets a gray level, so the shader can reveal them one by one. */
    private fun drawOrder(canvas: Canvas, lines: List<String>, base: Paint, left: (String) -> Float, baseline: (Int) -> Float) {
        val total = lines.sumOf { it.length }.coerceAtLeast(1)
        val p = Paint(base).apply {
            // A little thicker than the glyph, so antialiased edges keep their own level.
            style = Paint.Style.FILL_AND_STROKE
            strokeWidth = base.textSize * 0.08f
        }
        var index = 0
        lines.forEachIndexed { li, line ->
            var x = left(line)
            for (ch in line) {
                val s = ch.toString()
                val level = ((index + 1) * 255f / total).toInt().coerceIn(1, 255)
                p.color = Color.argb(255, level, level, level)
                canvas.drawText(s, x, baseline(li), p)
                x += base.measureText(s)
                index++
            }
        }
    }

    private fun typefaceFor(layer: TextLayer): Typeface {
        val style = when {
            layer.font.bold && layer.font.italic -> Typeface.BOLD_ITALIC
            layer.font.bold -> Typeface.BOLD
            layer.font.italic -> Typeface.ITALIC
            else -> Typeface.NORMAL
        }
        return Typeface.create(layer.font.family, style)
    }

    /** Text bitmaps stay well below the GLES 3.0 minimum texture size. */
    private const val MAX_SIDE = 2048
}
