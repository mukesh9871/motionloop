package com.motionloop.app.feature.editor.components

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import com.motionloop.app.domain.audio.AmbientSound
import com.motionloop.app.domain.model.Atmosphere
import com.motionloop.app.domain.model.BackgroundMode
import com.motionloop.app.domain.model.CameraPath
import com.motionloop.app.domain.model.LightLeak
import com.motionloop.app.domain.model.ParticleType
import com.motionloop.app.domain.model.SkyStyle
import com.motionloop.app.domain.model.StickerKind
import com.motionloop.app.domain.model.TextAnimation
import com.motionloop.app.ui.components.TilePreview
import com.motionloop.app.ui.components.tileScene
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/*
 * Drawn previews for the effect tiles. Each one is a tiny picture of what the effect does, in
 * fixed colors (a picture, not themed UI). t runs 0..1 and every motion is periodic in it.
 */

private const val TAU = (2 * PI).toFloat()

/** One unit per dp of the 64 dp tile, so drawings look the same on every screen density. */
private val DrawScope.u: Float get() = size.minDimension / 64f
private val White = Color.White

/** "Nothing chosen": the plain scene, dimmed, with a slash. */
val NonePreview: TilePreview = {
    tileScene()
    drawRect(Color.Black.copy(alpha = 0.35f))
    val r = size.minDimension * 0.22f
    val c = center
    drawCircle(White, radius = r, center = c, style = Stroke(width = 3f * u))
    drawLine(White, Offset(c.x - r * 0.7f, c.y + r * 0.7f), Offset(c.x + r * 0.7f, c.y - r * 0.7f), strokeWidth = 3f * u)
}

/** A plus on the scene, for actions that pick a file (your own photo or audio). */
val PickPreview: TilePreview = {
    tileScene()
    drawRect(Color.Black.copy(alpha = 0.4f))
    val r = size.minDimension * 0.2f
    drawLine(White, Offset(center.x - r, center.y), Offset(center.x + r, center.y), strokeWidth = 4f * u, cap = StrokeCap.Round)
    drawLine(White, Offset(center.x, center.y - r), Offset(center.x, center.y + r), strokeWidth = 4f * u, cap = StrokeCap.Round)
}

// region overlays

fun particlePreview(type: ParticleType): TilePreview = when (type) {
    ParticleType.NONE -> NonePreview
    else -> { t ->
        tileScene()
        val w = size.width
        val h = size.height
        for (k in 0 until 14) {
            val x0 = ((k * 0.618f) % 1f) * w
            val y0 = ((k * 0.377f) % 1f)
            when (type) {
                ParticleType.SPARKLE -> {
                    val tw = (sin((t * 2 + k * 0.37f) * TAU) * 0.5f + 0.5f)
                    star(Offset(x0, y0 * h), (3f + 4f * tw) * u, White.copy(alpha = 0.4f + 0.6f * tw))
                }
                ParticleType.SNOW -> drawCircle(White, radius = (1f + (k % 3) * 0.7f) * u, center = Offset(x0 + 3f * u * sin((t + k) * TAU), ((y0 + t) % 1f) * h))
                ParticleType.RAIN -> {
                    val y = ((y0 + 2 * t) % 1f) * h
                    drawLine(Color(0xFFD8E4FF), Offset(x0, y), Offset(x0 + 2f * u, y + 9f * u), strokeWidth = 1.2f * u)
                }
                ParticleType.BOKEH -> drawCircle(
                    if (k % 2 == 0) Color(0x66FFD08A) else Color(0x6699B8FF),
                    radius = (4f + (k % 4) * 1.5f) * u,
                    center = Offset(x0 + 3f * u * cos((t + y0) * TAU), y0 * h + 3f * u * sin((t + y0) * TAU)),
                )
                ParticleType.EMBERS -> drawCircle(Color(0xFFFF8A3C), radius = 1.4f * u, center = Offset(x0 + 2f * u * sin((2 * t + k) * TAU), h - ((y0 + t) % 1f) * h))
                ParticleType.BUBBLES -> drawCircle(White.copy(alpha = 0.8f), radius = (2.5f + (k % 3) * 0.8f) * u, center = Offset(x0, h - ((y0 + t) % 1f) * h), style = Stroke(1.2f * u))
                ParticleType.HEARTS -> heart(Offset(x0, h - ((y0 + t) % 1f) * h), (3f + (k % 3) * 0.8f) * u, Color(0xFFFF5C8A))
                ParticleType.FIREFLIES -> {
                    val p = sin((t * 2 + y0) * TAU) * 0.5f + 0.5f
                    drawCircle(Color(0xFFDFFF73).copy(alpha = p), radius = 2f * u, center = Offset(x0 + 4f * u * sin((t + y0) * TAU), y0 * h * 0.8f + h * 0.15f))
                }
                ParticleType.SMOKE -> {
                    val y = ((y0 + t) % 1f)
                    drawCircle(Color(0xFFD9D9E0).copy(alpha = (0.35f * sin(y * PI.toFloat())).coerceIn(0f, 1f)), radius = (5f + 7f * y) * u, center = Offset(x0, h - y * h))
                }
                ParticleType.NONE -> Unit
            }
        }
    }
}

fun leakPreview(leak: LightLeak): TilePreview = when (leak) {
    LightLeak.NONE -> NonePreview
    else -> { t ->
        tileScene()
        val colors = when (leak) {
            LightLeak.WARM -> listOf(Color(0xCCFF8A2A), Color(0x99FFC04D))
            LightLeak.ROSE -> listOf(Color(0xCCFF4F8B), Color(0x99B36BFF))
            else -> listOf(Color(0xCC33E6A0), Color(0x994D80FF))
        }
        val a = Offset(size.width * (0.1f + 0.1f * cos(t * TAU)), size.height * (0.25f + 0.1f * sin(t * TAU)))
        val b = Offset(size.width * (0.9f + 0.08f * sin(t * TAU)), size.height * 0.55f)
        drawCircle(Brush.radialGradient(listOf(colors[0], Color.Transparent), center = a, radius = size.width * 0.55f), radius = size.width * 0.55f, center = a)
        drawCircle(Brush.radialGradient(listOf(colors[1], Color.Transparent), center = b, radius = size.width * 0.5f), radius = size.width * 0.5f, center = b)
    }
}

fun atmospherePreview(atmosphere: Atmosphere): TilePreview = when (atmosphere) {
    Atmosphere.NONE -> NonePreview
    Atmosphere.FOG -> { t ->
        tileScene()
        for (k in 0 until 3) {
            val y = size.height * (0.45f + k * 0.17f)
            val x = ((t + k * 0.33f) % 1f) * size.width * 1.4f - size.width * 0.2f
            drawOval(White.copy(alpha = 0.45f), topLeft = Offset(x - size.width * 0.45f, y - 6f * u), size = Size(size.width * 0.9f, 12f * u))
        }
    }
    Atmosphere.GRAIN -> { t ->
        tileScene()
        val step = (t * 24).toInt()
        var i = 0
        while (i < 220) {
            val h = ((i * 7919 + step * 104729) % 1000) / 1000f
            val x = ((i * 37 + step * 11) % 64) / 64f * size.width
            val y = ((i * 53 + step * 17) % 64) / 64f * size.height
            drawRect(Color.Gray.copy(alpha = 0.25f + 0.4f * h), topLeft = Offset(x, y), size = Size(u, u))
            i++
        }
    }
}

// endregion

// region sky, 3D, background

fun skyPreview(style: SkyStyle): TilePreview = { t ->
    val (top, low) = when (style) {
        SkyStyle.CLEAR -> Color(0xFF3E8EF0) to Color(0xFFA9D4FF)
        SkyStyle.SUNSET -> Color(0xFF6A3FA0) to Color(0xFFFF9A5C)
        SkyStyle.NIGHT -> Color(0xFF0B1030) to Color(0xFF2A2F66)
        SkyStyle.STORM -> Color(0xFF3B4250) to Color(0xFF7B8494)
        SkyStyle.AURORA -> Color(0xFF071A2A) to Color(0xFF1C3E5A)
        SkyStyle.PHOTO -> Color(0xFF5B7BD5) to Color(0xFFF4B58C)
    }
    tileScene(top, low)
    val w = size.width
    val h = size.height
    when (style) {
        SkyStyle.CLEAR, SkyStyle.SUNSET, SkyStyle.STORM -> {
            val cloud = if (style == SkyStyle.STORM) Color(0xFF555C6A) else White.copy(alpha = 0.85f)
            for (k in 0 until 2) {
                val x = ((t + k * 0.5f) % 1f) * (w + 30f * u) - 15f * u
                drawRoundRect(cloud, topLeft = Offset(x, h * (0.12f + 0.12f * k)), size = Size(22f * u, 7f * u), cornerRadius = CornerRadius(3.5f * u))
            }
        }
        SkyStyle.NIGHT -> repeat(10) { k ->
            val tw = sin((t * 2 + k * 0.29f) * TAU) * 0.5f + 0.5f
            drawCircle(White.copy(alpha = 0.3f + 0.7f * tw), radius = u, center = Offset(((k * 0.618f) % 1f) * w, ((k * 0.41f) % 1f) * h * 0.5f))
        }
        SkyStyle.AURORA -> repeat(3) { k ->
            val path = Path().apply {
                moveTo(0f, h * (0.2f + 0.08f * k))
                var x = 0f
                while (x <= w) {
                    lineTo(x, h * (0.2f + 0.08f * k) + 4f * u * sin((x / w * 2 + t + k * 0.3f) * TAU))
                    x += w / 12f
                }
            }
            drawPath(path, Color(0xFF3CF2A5).copy(alpha = 0.6f - 0.15f * k), style = Stroke(width = 3.5f * u))
        }
        SkyStyle.PHOTO -> PickPreview(this, t)
    }
}

fun cameraPreview(path: CameraPath): TilePreview = { t ->
    val a = t * TAU
    val (ux, uy) = when (path) {
        CameraPath.CIRCLE -> cos(a) * 4f to sin(a) * 4f
        CameraPath.SWAY -> sin(a) * 5f to 0f
        CameraPath.TILT -> 0f to sin(a) * 5f
        CameraPath.FLOAT -> sin(a) * 5f to sin(2 * a) * 2.5f
        CameraPath.ZOOM -> 0f to 0f
    }
    val dx = ux * u
    val dy = uy * u
    val zoom = if (path == CameraPath.ZOOM) 1f + 0.12f * (0.5f - 0.5f * cos(a)) else 1f
    // Far layer moves a little, the near rock a lot: the 3D feel in one picture.
    translate(dx * 0.3f, dy * 0.3f) { tileScene() }
    val rock = Offset(size.width * 0.3f + dx * 1.6f, size.height * 0.8f + dy * 1.6f)
    drawOval(Color(0xFF1E5A3A), topLeft = Offset(rock.x - 12f * u * zoom, rock.y - 8f * u * zoom), size = Size(24f * u * zoom, 16f * u * zoom))
}

fun backgroundPreview(mode: BackgroundMode): TilePreview = { _ ->
    when (mode) {
        BackgroundMode.NONE -> tileScene()
        BackgroundMode.TRANSPARENT -> {
            val cell = size.width / 8f
            for (r in 0 until 8) for (c in 0 until 8) {
                drawRect(if ((r + c) % 2 == 0) Color(0xFFDDDDDD) else White, topLeft = Offset(c * cell, r * cell), size = Size(cell, cell))
            }
        }
        BackgroundMode.SOLID -> drawRect(Color(0xFF1B1F3B))
        BackgroundMode.GRADIENT -> drawRect(Brush.verticalGradient(listOf(Color(0xFF6B4EFF), Color(0xFF2EE6C5))))
        BackgroundMode.BLUR -> {
            tileScene()
            drawRect(White.copy(alpha = 0.35f))
        }
        BackgroundMode.PHOTO -> PickPreview(this, 0f)
    }
    if (mode != BackgroundMode.PHOTO) person()
}

// endregion

// region stickers, text, sound

fun stickerPreview(kind: StickerKind): TilePreview = { t ->
    drawRect(Color(0xFF243048))
    val c = center
    val s = size.minDimension
    when (kind) {
        StickerKind.BUTTERFLY -> {
            val flap = 0.25f + 0.75f * abs(cos(t * 3 * TAU))
            for (side in listOf(-1f, 1f)) {
                drawOval(Color(0xFFFF8A1F), topLeft = Offset(c.x + side * s * 0.16f * flap - s * 0.14f * flap, c.y - s * 0.24f), size = Size(s * 0.28f * flap, s * 0.26f))
                drawOval(Color(0xFFE85D0C), topLeft = Offset(c.x + side * s * 0.11f * flap - s * 0.09f * flap, c.y), size = Size(s * 0.18f * flap, s * 0.2f))
            }
            drawRoundRect(Color(0xFF1A1A1A), topLeft = Offset(c.x - 2f * u, c.y - s * 0.22f), size = Size(4f * u, s * 0.42f), cornerRadius = CornerRadius(2f * u))
        }
        StickerKind.FLAME -> flame(t)
        StickerKind.BIRDS -> repeat(3) { k ->
            val x = ((t + k / 3f) % 1f) * (s * 1.3f) - s * 0.15f
            val y = s * (0.3f + 0.18f * k)
            val f = 5f * u * sin((t * 6 + k * 0.3f) * TAU)
            drawLine(White, Offset(x - 7f * u, y + f), Offset(x, y), strokeWidth = 2f * u, cap = StrokeCap.Round)
            drawLine(White, Offset(x, y), Offset(x + 7f * u, y + f), strokeWidth = 2f * u, cap = StrokeCap.Round)
        }
        StickerKind.GLINT -> rotate(t * 360f) {
            star(c, s * (0.3f + 0.05f * sin(t * 2 * TAU)), Color(0xFFFFF4D6))
        }
    }
}

fun textAnimationPreview(animation: TextAnimation): TilePreview = { t ->
    drawRect(Color(0xFF243048))
    val y = size.height * 0.5f
    val x0 = size.width * 0.2f
    val bar = size.width * 0.6f
    // Text shown as three bars, so the preview needs no font measuring every frame.
    fun bars(dx: Float, alpha: Float, share: Float, glow: Float) {
        if (glow > 0f) drawRoundRect(Color(0xFF8C7CFF).copy(alpha = 0.5f * glow), topLeft = Offset(x0 + dx - 4f * u, y - 12f * u), size = Size(bar + 8f * u, 24f * u), cornerRadius = CornerRadius(8f * u))
        for (k in 0 until 3) {
            val start = k / 3f
            if (share <= start) break
            val part = ((share - start) * 3f).coerceAtMost(1f)
            // Skipped while too short to draw: a negative width would draw a reversed sliver.
            val width = bar / 3f * part - 3f * u
            if (width > 0f) {
                drawRoundRect(White.copy(alpha = alpha), topLeft = Offset(x0 + dx + k * bar / 3f, y - 5f * u), size = Size(width, 10f * u), cornerRadius = CornerRadius(3f * u))
            }
        }
    }
    when (animation) {
        TextAnimation.NONE -> bars(0f, 1f, 1f, 0f)
        TextAnimation.SLIDE_IN -> bars(-size.width * 0.3f * (1f - (t / 0.4f).coerceAtMost(1f)), 1f, 1f, 0f)
        TextAnimation.FADE -> bars(0f, sin(t * PI.toFloat()).coerceIn(0f, 1f), 1f, 0f)
        TextAnimation.TYPEWRITER -> bars(0f, 1f, (t / 0.6f).coerceAtMost(1f), 0f)
        TextAnimation.GLOW_PULSE -> bars(0f, 1f, 1f, 0.5f - 0.5f * cos(t * TAU))
    }
}

fun soundPreview(sound: AmbientSound): TilePreview = { t ->
    tileScene()
    drawRect(Color.Black.copy(alpha = 0.25f))
    val w = size.width
    val h = size.height
    when (sound) {
        AmbientSound.RAIN -> {
            for (k in 0 until 12) {
                val x = ((k * 0.618f) % 1f) * w
                val y = (((k * 0.37f) % 1f + 2 * t) % 1f) * h
                drawLine(Color(0xFFD8E4FF), Offset(x, y), Offset(x + 2f * u, y + 9f * u), strokeWidth = 1.2f * u)
            }
        }
        AmbientSound.WAVES -> {
            for (row in 0 until 3) {
                val path = Path().apply {
                    var x = 0f
                    moveTo(0f, h * (0.62f + row * 0.1f))
                    while (x <= w) {
                        lineTo(x, h * (0.62f + row * 0.1f) + 3f * u * sin((x / w * 2 + t + row * 0.2f) * TAU))
                        x += w / 12f
                    }
                }
                drawPath(path, White.copy(alpha = 0.7f), style = Stroke(2f * u))
            }
        }
        AmbientSound.WIND -> {
            for (k in 0 until 3) {
                val x = ((t + k / 3f) % 1f) * w * 1.3f - w * 0.3f
                drawLine(White.copy(alpha = 0.8f), Offset(x, h * (0.25f + k * 0.15f)), Offset(x + w * 0.35f, h * (0.25f + k * 0.15f)), strokeWidth = 2f * u, cap = StrokeCap.Round)
            }
        }
        // The flame alone, over the scene like the other sounds.
        AmbientSound.FIRE -> flame(t)
    }
}

// endregion

private fun DrawScope.flame(t: Float) {
    val c = center
    val s = size.minDimension
    val hgt = s * (0.42f + 0.04f * sin(t * 3 * TAU))
    val flame = Path().apply {
        moveTo(c.x, c.y - hgt * 0.7f)
        quadraticTo(c.x + s * 0.2f, c.y, c.x, c.y + s * 0.2f)
        quadraticTo(c.x - s * 0.2f, c.y, c.x, c.y - hgt * 0.7f)
        close()
    }
    drawPath(flame, Brush.verticalGradient(listOf(Color(0xFFFFF2B0), Color(0xFFFF7A1A), Color(0xFFD9300F)), startY = c.y - hgt * 0.7f, endY = c.y + s * 0.2f))
}

private fun DrawScope.star(c: Offset, r: Float, color: Color) {
    drawLine(color, Offset(c.x - r, c.y), Offset(c.x + r, c.y), strokeWidth = 2f * u, cap = StrokeCap.Round)
    drawLine(color, Offset(c.x, c.y - r), Offset(c.x, c.y + r), strokeWidth = 2f * u, cap = StrokeCap.Round)
    drawCircle(color, radius = r * 0.25f, center = c)
}

private fun DrawScope.heart(c: Offset, r: Float, color: Color) {
    drawCircle(color, radius = r * 0.55f, center = Offset(c.x - r * 0.45f, c.y - r * 0.2f))
    drawCircle(color, radius = r * 0.55f, center = Offset(c.x + r * 0.45f, c.y - r * 0.2f))
    val tip = Path().apply {
        moveTo(c.x - r, c.y)
        lineTo(c.x + r, c.y)
        lineTo(c.x, c.y + r)
        close()
    }
    drawPath(tip, color)
}

private fun DrawScope.person() {
    val c = center
    val h = size.height
    drawCircle(Color(0xFF151827), radius = h * 0.11f, center = Offset(c.x, h * 0.42f))
    drawRoundRect(Color(0xFF151827), topLeft = Offset(c.x - h * 0.15f, h * 0.55f), size = Size(h * 0.3f, h * 0.45f), cornerRadius = CornerRadius(h * 0.1f))
}
