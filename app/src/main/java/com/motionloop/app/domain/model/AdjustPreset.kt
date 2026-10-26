package com.motionloop.app.domain.model

/** One-tap starting looks for the Adjust panel. The sliders stay for fine tuning afterwards. */
enum class AdjustPreset(
    val label: String,
    val brightness: Float = 0f,
    val contrast: Float = 0f,
    val saturation: Float = 0f,
    val warmth: Float = 0f,
) {
    NATURAL("Natural"),
    BRIGHT("Bright", brightness = 0.25f, contrast = 0.05f),
    DARK("Moody", brightness = -0.2f, contrast = 0.2f, saturation = -0.15f),
    VIVID("Vivid", contrast = 0.15f, saturation = 0.45f),
    MUTED("Muted", contrast = -0.1f, saturation = -0.45f),
    WARM("Warm", warmth = 0.45f, saturation = 0.1f),
    COOL("Cool", warmth = -0.45f),
    PUNCHY("Punchy", brightness = 0.05f, contrast = 0.4f, saturation = 0.2f),
    SOFT("Soft", brightness = 0.1f, contrast = -0.3f, saturation = -0.1f),
    ;

    /** [base] with this look's four values, keeping its grade. */
    fun applyTo(base: Adjustments): Adjustments =
        base.copy(brightness = brightness, contrast = contrast, saturation = saturation, warmth = warmth)

    /** True when [a] has exactly this look's values (the tile shows as chosen). */
    fun matches(a: Adjustments): Boolean =
        a.brightness == brightness && a.contrast == contrast && a.saturation == saturation && a.warmth == warmth
}
