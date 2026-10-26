import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// AGP 9 compiles Kotlin itself (built-in Kotlin), so no kotlin-android plugin here.
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

android {
    namespace = "com.motionloop.app"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.motionloop.app"
        minSdk = 26
        targetSdk = 37
        versionCode = 1
        versionName = "0.1.0"
        // Contact form recipient. Personal address until the app has a support mailbox.
        buildConfigField("String", "SUPPORT_EMAIL", "\"mukesh9871@gmail.com\"")
        // Empty until the pages exist: the app hides the links. Play requires a real policy page before release.
        buildConfigField("String", "PRIVACY_URL", "\"\"")
        buildConfigField("String", "TERMS_URL", "\"\"")

        // MediaPipe and LiteRT ship large native libraries per CPU type. Phones are ARM, and
        // x86_64 covers the emulator. 32-bit x86 has no real devices left, so it is not packaged.
        ndk {
            abiFilters += listOf("arm64-v8a", "armeabi-v7a", "x86_64")
        }
    }

    buildTypes {
        debug {
            // A debug build installs next to the release one.
            applicationIdSuffix = ".debug"
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    // ML models from :core:ml are memory-mapped, so they must stay uncompressed in the APK.
    androidResources {
        noCompress += "tflite"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

// Sample photos (CC0, Wikimedia Commons, see DemoPhotos.kt) are downloaded at build time,
// never committed. Same pinned-download task as the ML models (buildSrc).
val downloadDemos = tasks.register<DownloadAssetsTask>("downloadDemos") {
    urls.put("demos/waterfall.jpg", "https://upload.wikimedia.org/wikipedia/commons/thumb/f/f7/Frothy_waterfall_in_Iceland_%28Unsplash%29.jpg/1920px-Frothy_waterfall_in_Iceland_%28Unsplash%29.jpg")
    sha256.put("demos/waterfall.jpg", "3153efbef02bfd9eab1ce210cab72c1990b4a201d1784b1ecaf2d506e12193c6")
    urls.put("demos/ocean.jpg", "https://upload.wikimedia.org/wikipedia/commons/thumb/4/40/Ocean_waves_rippling_%28Unsplash%29.jpg/1920px-Ocean_waves_rippling_%28Unsplash%29.jpg")
    sha256.put("demos/ocean.jpg", "a8a35eb2a06be1db2d40005697cb12035c8be5283cac6f65803d102dc6de020a")
    urls.put("demos/mountains.jpg", "https://upload.wikimedia.org/wikipedia/commons/thumb/8/82/Cloudy_Skies_Over_Mountains_%28Unsplash%29.jpg/1920px-Cloudy_Skies_Over_Mountains_%28Unsplash%29.jpg")
    sha256.put("demos/mountains.jpg", "55713397f25b6dec0ed9f22b1a9129737cac0b6d7e155062b3b3ef82d39a9a5e")
    urls.put("demos/lake.jpg", "https://upload.wikimedia.org/wikipedia/commons/thumb/d/d1/Clouds_mirrored_in_a_mountain_lake_%28Unsplash%29.jpg/1920px-Clouds_mirrored_in_a_mountain_lake_%28Unsplash%29.jpg")
    sha256.put("demos/lake.jpg", "8621e1ad31ba5b5e0bc7559bad7b27cbbb3dc3ab861e091654e80b826453fe45")
    urls.put("demos/campfire.jpg", "https://upload.wikimedia.org/wikipedia/commons/thumb/9/99/Campfire_Cooking_%28Unsplash%29.jpg/1920px-Campfire_Cooking_%28Unsplash%29.jpg")
    sha256.put("demos/campfire.jpg", "6c5a584d02c78cf2c6901cbbae363ecaaf4aa968b12132433decd0c9550d4977")
    urls.put("demos/falls_portrait.jpg", "https://upload.wikimedia.org/wikipedia/commons/thumb/c/c7/Narrow_waterfall_in_Alaska_%28Unsplash%29.jpg/1920px-Narrow_waterfall_in_Alaska_%28Unsplash%29.jpg")
    sha256.put("demos/falls_portrait.jpg", "71d26ae0e433400c296044db8b9d806c6e09763488f24bbecd21a47ef364802c")
    // The reference photo every effect card and Help picture is rendered from. CC0, Paola Chaaya.
    urls.put("reference/pier.jpg", "https://upload.wikimedia.org/wikipedia/commons/thumb/c/c5/Admiring_the_lake_from_a_pier_%28Unsplash%29.jpg/1280px-Admiring_the_lake_from_a_pier_%28Unsplash%29.jpg")
    sha256.put("reference/pier.jpg", "5c9debeeee1953044a132d1d528c4ae6fdbeb2301d6ea941742d1859e635e62a")
    outputDir.set(layout.buildDirectory.dir("generated/demos"))
}

// Space Grotesk (SIL Open Font License 1.1), as font resources. Static weights: no variable font APIs needed.
val downloadFonts = tasks.register<DownloadAssetsTask>("downloadFonts") {
    val base = "https://raw.githubusercontent.com/floriankarsten/space-grotesk/master/fonts/ttf/static"
    urls.put("font/space_grotesk_regular.ttf", "$base/SpaceGrotesk-Regular.ttf")
    sha256.put("font/space_grotesk_regular.ttf", "5ede28c4425f3fe4830c8f4754b39e9a87a93d0c3baa5e0a9924532aaa8a98bd")
    urls.put("font/space_grotesk_medium.ttf", "$base/SpaceGrotesk-Medium.ttf")
    sha256.put("font/space_grotesk_medium.ttf", "3183b7eb0b4241476360e2cd8e527868616cc16d3ce332c4376505989b772b44")
    urls.put("font/space_grotesk_bold.ttf", "$base/SpaceGrotesk-Bold.ttf")
    sha256.put("font/space_grotesk_bold.ttf", "7209bbb75fc0f5c546a5f5773b0db74ffc6abf04c2148c1105cace5765a96bdb")
    outputDir.set(layout.buildDirectory.dir("generated/fonts"))
}

androidComponents {
    onVariants { variant ->
        variant.sources.assets?.addGeneratedSourceDirectory(downloadDemos, DownloadAssetsTask::outputDir)
        variant.sources.res?.addGeneratedSourceDirectory(downloadFonts, DownloadAssetsTask::outputDir)
    }
}

dependencies {
    implementation(project(":core:ml"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.hilt.lifecycle.viewmodel.compose)
    implementation(libs.androidx.exifinterface)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    debugImplementation(libs.compose.ui.tooling)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)

    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)

    testImplementation(libs.junit)
}
