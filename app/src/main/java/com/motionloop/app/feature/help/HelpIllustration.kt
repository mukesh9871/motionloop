package com.motionloop.app.feature.help

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

/** Which tool a help picture shows. */
enum class Illus { PATH, ANCHOR, FREEZE, UNFREEZE, ERASE, HOLD, ONLY_THIS, SPEED, SKY, DEPTH, CUTOUT, TEXT, SOUND, ADJUST, OVERLAY, EXPORT, STICKER, DISPERSE, WATER }

// A picture, so fixed colors: the same in light and dark theme.
private val SkyTop = Color(0xFF5B7BD5)
private val SkyLow = Color(0xFFF4B58C)
private val Hills = Color(0xFF2F3B66)
private val Water = Color(0xFF2B6A92)
private val Mint = Color(0xFF2EE6C5)
private val Coral = Color(0xFFFF6B7A)
private val Finger = Color(0xFFFFFFFF)

/**
 * Small looping demo of a tool: a stylized photo plus the gesture that uses the tool.
 * Drawn in code, so it costs no asset and follows the help text exactly.
 */
@Composable
fun HelpIllustration(kind: Illus, modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "help")
    val t by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2800, easing = LinearEasing), RepeatMode.Restart),
        label = "t",
    )
    val measurer = rememberTextMeasurer()
    Canvas(modifier) {
        when (kind) {
            Illus.SKY -> scene(skyShift = (sin(t * 2 * PI).toFloat() + 1) / 2)
            Illus.DEPTH -> depthScene(t)
            Illus.CUTOUT -> cutout(t)
            Illus.ADJUST -> scene(warmth = sin(t * 2 * PI).toFloat())
            else -> scene()
        }
        when (kind) {
            Illus.PATH -> pathDemo(t)
            Illus.ANCHOR -> anchors(t)
            Illus.FREEZE -> brush(t, erase = false)
            Illus.UNFREEZE -> brush(t, erase = true)
            Illus.ERASE -> erase(t)
            Illus.HOLD -> person(t, onlyThis = false)
            Illus.ONLY_THIS -> person(t, onlyThis = true)
            Illus.SPEED -> speed(t)
            Illus.SKY -> clouds(t)
            Illus.TEXT -> {
                val layout = measurer.measure("HELLO", TextStyle(color = Color.White, fontSize = 34.sp, fontWeight = FontWeight.Black))
                drawText(layout, topLeft = Offset((size.width - layout.size.width) / 2, size.height * 0.18f + 4f * sin(t * 2 * PI).toFloat()))
                silhouette(Color(0xFF1B1E33))
            }
            Illus.SOUND -> bars(t)
            Illus.OVERLAY -> snow(t)
            Illus.EXPORT -> shapes(t)
            Illus.STICKER -> sticker(t)
            Illus.DISPERSE -> disperse(t)
            Illus.WATER -> water(t)
            else -> Unit
        }
    }
}

private fun DrawScope.scene(skyShift: Float = 0f, warmth: Float = 0f) {
    val w = size.width
    val h = size.height
    val top = lerp(SkyTop, Color(0xFF1C1F4A), skyShift)
    val low = lerp(SkyLow, Color(0xFF8E5BD8), skyShift)
    drawRect(Brush.verticalGradient(listOf(top, low), endY = h * 0.6f), size = Size(w, h * 0.6f))
    val hills = Path().apply {
        moveTo(0f, h * 0.6f)
        lineTo(0f, h * 0.42f); lineTo(w * 0.18f, h * 0.3f); lineTo(w * 0.36f, h * 0.45f)
        lineTo(w * 0.55f, h * 0.26f); lineTo(w * 0.78f, h * 0.44f); lineTo(w, h * 0.34f); lineTo(w, h * 0.6f)
        close()
    }
    drawPath(hills, Hills)
    drawRect(Water, topLeft = Offset(0f, h * 0.6f), size = Size(w, h * 0.4f))
    if (warmth != 0f) drawRect(if (warmth > 0) Color(0xFFFF9A3C) else Color(0xFF3C8DFF), alpha = abs(warmth) * 0.25f)
}

private fun lerp(a: Color, b: Color, t: Float) = Color(
    a.red + (b.red - a.red) * t, a.green + (b.green - a.green) * t, a.blue + (b.blue - a.blue) * t, 1f,
)

private fun DrawScope.finger(at: Offset, pressed: Boolean = true) {
    drawCircle(Finger.copy(alpha = 0.35f), radius = 14.dp.toPx(), center = at)
    drawCircle(Finger, radius = (if (pressed) 6 else 8).dp.toPx(), center = at, style = Stroke(2.dp.toPx()))
}

/** A curved path is drawn, then water flows along it. */
private fun DrawScope.pathDemo(t: Float) {
    val w = size.width
    val h = size.height
    val path = Path().apply {
        moveTo(w * 0.12f, h * 0.72f)
        cubicTo(w * 0.35f, h * 0.62f, w * 0.5f, h * 0.95f, w * 0.88f, h * 0.78f)
    }
    val measure = PathMeasure().apply { setPath(path, false) }
    val draw = (t / 0.5f).coerceIn(0f, 1f)
    val part = Path()
    measure.getSegment(0f, measure.length * draw, part, true)
    drawPath(part, Mint, style = Stroke(4.dp.toPx(), cap = StrokeCap.Round))
    if (t < 0.5f) {
        finger(measure.getPosition(measure.length * draw))
    } else {
        // Streaks travelling along the finished path.
        for (k in 0 until 5) {
            val d = ((t - 0.5f) * 2f + k / 5f) % 1f
            drawCircle(Color.White.copy(alpha = 0.8f), radius = 4f, center = measure.getPosition(measure.length * d))
        }
    }
}

private fun DrawScope.anchors(t: Float) {
    val pts = listOf(Offset(0.25f, 0.8f), Offset(0.52f, 0.7f), Offset(0.78f, 0.84f))
    pts.forEachIndexed { i, p ->
        val appear = ((t * 3f) - i).coerceIn(0f, 1f)
        if (appear > 0f) {
            val c = Offset(p.x * size.width, p.y * size.height)
            drawCircle(Coral, radius = 16f * appear, center = c, style = Stroke(3f))
            drawCircle(Coral, radius = 4f, center = c)
        }
    }
    val active = (t * 3f).toInt().coerceAtMost(2)
    finger(Offset(pts[active].x * size.width, pts[active].y * size.height))
}

/** Painting a frozen area, or wiping it away again with Unfreeze. */
private fun DrawScope.brush(t: Float, erase: Boolean) {
    val w = size.width
    val h = size.height
    val x = w * (0.2f + 0.6f * t)
    val y = h * (0.72f + 0.06f * sin(t * 6 * PI).toFloat())
    if (erase) {
        // Full area first, wiped from the left as the finger passes.
        clipRect(left = x, top = 0f, right = w, bottom = h) {
            drawRoundRect(Coral.copy(alpha = 0.45f), Offset(w * 0.18f, h * 0.62f), Size(w * 0.64f, h * 0.22f), CornerRadius(30f))
        }
    } else {
        clipRect(left = 0f, top = 0f, right = x, bottom = h) {
            drawRoundRect(Coral.copy(alpha = 0.45f), Offset(w * 0.18f, h * 0.62f), Size(w * 0.64f, h * 0.22f), CornerRadius(30f))
        }
    }
    drawCircle(Color.White, radius = h * 0.11f, center = Offset(x, y), style = Stroke(2f))
    finger(Offset(x, y))
}

private fun DrawScope.erase(t: Float) {
    val w = size.width
    val h = size.height
    val alpha = if (t < 0.45f) 1f else (1f - (t - 0.45f) / 0.25f).coerceIn(0f, 1f)
    drawLine(Mint.copy(alpha = alpha), Offset(w * 0.2f, h * 0.75f), Offset(w * 0.7f, h * 0.7f), strokeWidth = 8f, cap = StrokeCap.Round)
    if (t in 0.3f..0.7f) finger(Offset(w * 0.45f, h * 0.725f))
}

private fun DrawScope.silhouette(color: Color) {
    val w = size.width
    val h = size.height
    val cx = w * 0.5f
    drawCircle(color, radius = h * 0.09f, center = Offset(cx, h * 0.42f))
    drawRoundRect(color, Offset(cx - h * 0.12f, h * 0.52f), Size(h * 0.24f, h * 0.48f), CornerRadius(h * 0.08f))
}

/** Tap a person: it is held still, or it becomes the only moving part. */
private fun DrawScope.person(t: Float, onlyThis: Boolean) {
    val selected = t > 0.35f
    if (onlyThis && selected) drawRect(Color.Black.copy(alpha = 0.45f))
    silhouette(Color(0xFF1B1E33))
    if (selected) {
        val c = if (onlyThis) Mint else Coral
        val cx = size.width * 0.5f
        val h = size.height
        drawCircle(c, radius = h * 0.09f, center = Offset(cx, h * 0.42f), style = Stroke(3f))
        drawRoundRect(c, Offset(cx - h * 0.12f, h * 0.52f), Size(h * 0.24f, h * 0.48f), CornerRadius(h * 0.08f), style = Stroke(3f))
    }
    if (t in 0.2f..0.5f) finger(Offset(size.width * 0.5f, size.height * 0.7f))
}

private fun DrawScope.speed(t: Float) {
    val w = size.width
    val h = size.height
    // Waves on the water speed up as the slider moves right.
    val v = 0.2f + 0.8f * t
    for (row in 0 until 3) {
        val y = h * (0.68f + row * 0.09f)
        val phase = (t * 8f * v + row * 0.3f) % 1f
        for (k in 0 until 4) {
            val x = ((k / 4f + phase) % 1f) * w
            drawLine(Color.White.copy(alpha = 0.6f), Offset(x, y), Offset(x + w * 0.08f, y), strokeWidth = 3f, cap = StrokeCap.Round)
        }
    }
    val trackY = h * 0.12f
    drawLine(Color.White.copy(alpha = 0.5f), Offset(w * 0.15f, trackY), Offset(w * 0.85f, trackY), strokeWidth = 6f, cap = StrokeCap.Round)
    val knob = Offset(w * (0.15f + 0.7f * t), trackY)
    drawCircle(Mint, radius = 10f, center = knob)
    finger(knob)
}

private fun DrawScope.clouds(t: Float) {
    val w = size.width
    val h = size.height
    for (k in 0 until 3) {
        val x = ((t + k / 3f) % 1f) * (w + 80f) - 40f
        val y = h * (0.12f + 0.08f * k)
        drawRoundRect(Color.White.copy(alpha = 0.8f), Offset(x, y), Size(60f, 18f), CornerRadius(9f))
    }
}

/** Layers slide by different amounts: near things move more than far things. */
private fun DrawScope.depthScene(t: Float) {
    val s = sin(t * 2 * PI).toFloat()
    translate(left = s * 6f) { scene() }
    translate(left = s * 18f) {
        drawRoundRect(Color(0xFF1E5A3A), Offset(size.width * 0.08f, size.height * 0.5f), Size(size.width * 0.12f, size.height * 0.4f), CornerRadius(20f))
    }
}

private fun DrawScope.cutout(t: Float) {
    // Checkerboard where the background was removed, the person kept.
    val cell = size.height / 8f
    var y = 0f
    var row = 0
    while (y < size.height) {
        var x = 0f
        var col = 0
        while (x < size.width) {
            drawRect(if ((row + col) % 2 == 0) Color(0xFFDDDDDD) else Color(0xFFFFFFFF), Offset(x, y), Size(cell, cell))
            x += cell
            col++
        }
        y += cell
        row++
    }
    val reveal = (t * 1.5f).coerceAtMost(1f)
    clipRect(right = size.width * (1f - reveal)) { scene() }
    silhouette(Color(0xFF1B1E33))
}

private fun DrawScope.bars(t: Float) {
    val n = 9
    val bw = size.width / (n * 2f)
    for (i in 0 until n) {
        val v = 0.3f + 0.7f * abs(sin((t * 4f + i * 0.37f) * PI).toFloat())
        val bh = size.height * 0.5f * v
        drawRoundRect(
            if (i % 2 == 0) Mint else Color(0xFF7C5CFF),
            Offset(bw * (1 + i * 2), size.height * 0.9f - bh),
            Size(bw, bh),
            CornerRadius(bw / 2),
        )
    }
}

private fun DrawScope.snow(t: Float) {
    for (k in 0 until 40) {
        val x = ((k * 0.618f) % 1f) * size.width + 6f * sin((t * 2 + k) * PI).toFloat()
        val y = (((k * 0.37f) % 1f + t) % 1f) * size.height
        drawCircle(Color.White.copy(alpha = 0.9f), radius = 2f + (k % 3), center = Offset(x, y))
    }
}

/** The export shapes, one after the other. */
private fun DrawScope.shapes(t: Float) {
    drawRect(Color.Black.copy(alpha = 0.35f))
    val ratios = listOf(16f / 9f, 1f, 4f / 5f, 9f / 16f)
    val r = ratios[(t * ratios.size).toInt().coerceAtMost(ratios.lastIndex)]
    val maxH = size.height * 0.8f
    val maxW = size.width * 0.8f
    val (fw, fh) = if (maxW / maxH > r) (maxH * r) to maxH else maxW to (maxW / r)
    drawRoundRect(Mint, Offset((size.width - fw) / 2, (size.height - fh) / 2), Size(fw, fh), CornerRadius(12f), style = Stroke(4f))
}

/** A glint sticker dragged into place. */
private fun DrawScope.sticker(t: Float) {
    val c = Offset(size.width * (0.3f + 0.4f * minOf(t * 1.6f, 1f)), size.height * 0.3f)
    val r = size.height * 0.12f * (0.8f + 0.2f * sin(t * 4 * PI).toFloat())
    drawLine(Color.White, Offset(c.x - r, c.y), Offset(c.x + r, c.y), strokeWidth = 3f, cap = StrokeCap.Round)
    drawLine(Color.White, Offset(c.x, c.y - r), Offset(c.x, c.y + r), strokeWidth = 3f, cap = StrokeCap.Round)
    drawCircle(Color.White, radius = r * 0.25f, center = c)
    if (t < 0.65f) finger(Offset(c.x, c.y + r * 1.4f))
}

/** Squares break off a person and drift away, then return. */
private fun DrawScope.disperse(t: Float) {
    silhouette(Color(0xFF1B1E33))
    val h = size.height
    val cx = size.width * 0.5f
    for (k in 0 until 24) {
        val life = (t + k / 24f) % 1f
        val ox = cx + h * 0.12f + (k % 4) * h * 0.03f
        val oy = h * (0.5f + (k / 4) * 0.07f)
        val x = ox + life * size.width * 0.35f
        val y = oy - life * h * 0.25f
        val s = h * 0.03f * (1f - 0.6f * life)
        drawRect(Color(0xFF1B1E33).copy(alpha = 1f - life), Offset(x, y), Size(s, s))
    }
}

/** Ripple lines on the water after a tap. */
private fun DrawScope.water(t: Float) {
    val w = size.width
    val h = size.height
    val on = t > 0.25f
    if (on) {
        for (row in 0 until 5) {
            val y = h * (0.65f + row * 0.07f)
            val path = Path().apply {
                moveTo(0f, y)
                var x = 0f
                while (x <= w) {
                    lineTo(x, y + 3f * sin((x / w * 12f + t * 2f * 2f + row) * PI).toFloat())
                    x += w / 40f
                }
            }
            drawPath(path, Color.White.copy(alpha = 0.5f), style = Stroke(2f))
        }
    }
    if (t in 0.1f..0.35f) finger(Offset(w * 0.5f, h * 0.78f))
}
