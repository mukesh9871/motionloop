package com.motionloop.app.domain.model

import kotlinx.serialization.Serializable

/** Order matters: `parallax.frag` switches on the ordinal. */
@Serializable
enum class CameraPath { CIRCLE, SWAY, ZOOM }

/**
 * 3D parallax from a depth map ([MaskKind.DEPTH] revision, 255 = near).
 * The virtual camera moves on a closed path once per loop cycle, so it loops seamlessly.
 */
@Serializable
data class ParallaxSettings(
    val enabled: Boolean = false,
    val path: CameraPath = CameraPath.CIRCLE,
    /** 0..1 strength of the camera move. */
    val amount: Float = 0.5f,
    /** 0..1 depth that stays still (0 = far, 1 = near). Things nearer or farther move opposite ways. */
    val focus: Float = 0.5f,
    /** Revision of the stored depth map, 0 = not computed yet. */
    val depthRev: Int = 0,
) {
    val isActive: Boolean get() = enabled && depthRev != 0 && amount > 0f

    companion object {
        /** Largest camera offset in image widths at amount 1. Also the auto zoom margin. */
        const val MAX_OFFSET = 0.05f
    }
}
