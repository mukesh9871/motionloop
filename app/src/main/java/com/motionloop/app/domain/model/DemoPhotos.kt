package com.motionloop.app.domain.model

/**
 * Sample photos for trying the app. All CC0 (public domain dedication) from Wikimedia Commons,
 * downloaded at build time into assets/demos (see app/build.gradle.kts), never committed.
 * CC0 needs no credit, but the photographers are credited in Settings anyway.
 */
data class DemoPhoto(
    val asset: String,
    val title: String,
    val hint: String,
    val author: String,
    val sourceUrl: String,
) {
    val assetPath: String get() = "demos/$asset"
}

object DemoPhotos {
    val all = listOf(
        DemoPhoto(
            "waterfall.jpg", "Waterfall", "Draw arrows down the falling water.",
            "Alice Donovan Rouse", "https://commons.wikimedia.org/wiki/File:Frothy_waterfall_in_Iceland_(Unsplash).jpg",
        ),
        DemoPhoto(
            "ocean.jpg", "Ocean", "Arrows toward the shore make the waves roll in.",
            "Tim Marshall", "https://commons.wikimedia.org/wiki/File:Ocean_waves_rippling_(Unsplash).jpg",
        ),
        DemoPhoto(
            "mountains.jpg", "Clouds", "Try the Sky panel, or drift the clouds with arrows.",
            "Cole Mader", "https://commons.wikimedia.org/wiki/File:Cloudy_Skies_Over_Mountains_(Unsplash).jpg",
        ),
        DemoPhoto(
            "lake.jpg", "Mirror lake", "Animate the clouds and their reflection together.",
            "Ales Krivec", "https://commons.wikimedia.org/wiki/File:Clouds_mirrored_in_a_mountain_lake_(Unsplash).jpg",
        ),
        DemoPhoto(
            "campfire.jpg", "Campfire", "Short upward arrows on the flames and smoke.",
            "Evan Kirby", "https://commons.wikimedia.org/wiki/File:Campfire_Cooking_(Unsplash).jpg",
        ),
        DemoPhoto(
            "falls_portrait.jpg", "Narrow falls", "A tall photo: good for 9:16 stories.",
            "Ben Black", "https://commons.wikimedia.org/wiki/File:Narrow_waterfall_in_Alaska_(Unsplash).jpg",
        ),
    )
}

/** Outward links. Placeholders until the app is published. */
object AppLinks {
    const val PLAY_STORE = "https://play.google.com/store/apps/details?id=com.motionloop.app"
    const val PRIVACY = "https://motionloop.example/privacy"
    const val TERMS = "https://motionloop.example/terms"
    const val SUPPORT_EMAIL = "support@motionloop.example"
}
