package com.motionloop.app.domain.model

import kotlinx.serialization.Serializable
import kotlin.random.Random

@Serializable
enum class ParticleType { NONE, SPARKLE, SNOW, RAIN, BOKEH, EMBERS }

@Serializable
enum class LightLeak { NONE, WARM, ROSE, AURORA }

/** How the overlay layer combines with the photo. Applied in the color shader. */
@Serializable
enum class BlendMode { NORMAL, ADD, SCREEN, OVERLAY, MULTIPLY, SOFT_LIGHT }

/**
 * Particles and light leaks drawn on top of the animated photo.
 * All motion is periodic in the loop phase, so overlays loop as seamlessly as the warp.
 */
@Serializable
data class OverlaySettings(
    val particles: ParticleType = ParticleType.NONE,
    /** 0..1, share of [MAX_PARTICLES] drawn. */
    val density: Float = 0.5f,
    /** 0..1, particle size scale. */
    val size: Float = 0.5f,
    /** Whole wraps per loop cycle for the slowest layer. Integer keeps the loop seamless. */
    val speed: Int = 1,
    val lightLeak: LightLeak = LightLeak.NONE,
    val leakIntensity: Float = 0.7f,
    val blend: BlendMode = BlendMode.SCREEN,
    /** 0..1 strength of the whole overlay layer. */
    val opacity: Float = 1f,
) {
    val isActive: Boolean
        get() = opacity > 0f && (particles != ParticleType.NONE || (lightLeak != LightLeak.NONE && leakIntensity > 0f))

    val particleCount: Int
        get() = if (particles == ParticleType.NONE) 0 else (density.coerceIn(0f, 1f) * MAX_PARTICLES).toInt().coerceAtLeast(1)

    companion object {
        const val MAX_PARTICLES = 400
        const val MIN_SPEED = 1
        const val MAX_SPEED = 3
    }
}

/** Per-particle random constants, fixed for the app so every frame and every export agree. */
object ParticleSeeds {
    /** [OverlaySettings.MAX_PARTICLES] x vec4: x0, y0, phase offset, random. */
    val data: FloatArray by lazy {
        val rnd = Random(0x4D4C) // fixed seed: same layout in preview and export
        FloatArray(OverlaySettings.MAX_PARTICLES * 4) { rnd.nextFloat() }
    }
}
