package com.motionloop.app.domain.pro

import kotlinx.coroutines.flow.StateFlow

/**
 * Features that can be reserved for MotionLoop Pro. The list of what is actually locked lives
 * in [ProPolicy], so changing the free tier is a one-line change.
 */
enum class ProFeature(val title: String, val description: String) {
    EXPORT_4K("4K export", "Export loops in 4K on phones whose encoder supports it."),
    LONG_EXPORT("Long videos", "Export 15 second videos."),
    SKY_PHOTO("Your own skies", "Use any photo as the new sky."),
    PARALLAX("3D camera", "Depth-based 3D camera motion."),
    CUSTOM_LUT("Custom color grades", "Import your own .cube color grades."),
    BACKGROUND_PHOTO("Background photos", "Replace the background with any photo."),
    MUSIC("Your own music", "Add your own audio and sync motion to the beat."),
    LIVE_WALLPAPER("Live wallpaper", "Set any loop as your home or lock screen wallpaper."),
}

object ProPolicy {
    /**
     * Master switch. While false nothing is locked: there is no billing yet, and testers get everything.
     * Turn on together with a real [Entitlements] implementation (Google Play Billing).
     */
    const val GATING_ENABLED = false

    /** Features locked for free users once gating is on. */
    val proOnly: Set<ProFeature> = setOf(
        ProFeature.EXPORT_4K,
        ProFeature.SKY_PHOTO,
        ProFeature.PARALLAX,
        ProFeature.CUSTOM_LUT,
        ProFeature.BACKGROUND_PHOTO,
        ProFeature.MUSIC,
    )

    fun isLocked(feature: ProFeature, isPro: Boolean): Boolean =
        GATING_ENABLED && !isPro && feature in proOnly
}

enum class ProPlan(val title: String, val priceLabel: String, val note: String) {
    MONTHLY("Monthly", "Price set in Play Store", "Cancel anytime"),
    YEARLY("Yearly", "Price set in Play Store", "Best value"),
    LIFETIME("Lifetime", "Price set in Play Store", "Pay once"),
}

/**
 * Who has Pro. Today a local implementation (with a debug "Simulate Pro" switch).
 * A Google Play Billing implementation replaces it later without touching the screens.
 */
interface Entitlements {
    val isPro: StateFlow<Boolean>

    /** True when billing is set up and [purchase] can be called. */
    val purchasesAvailable: Boolean

    suspend fun purchase(plan: ProPlan): Result<Unit>
    suspend fun restore(): Result<Boolean>

    fun isLocked(feature: ProFeature): Boolean = ProPolicy.isLocked(feature, isPro.value)
}
