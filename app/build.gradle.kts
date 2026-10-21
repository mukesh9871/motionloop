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
    }

    buildTypes {
        release {
            isMinifyEnabled = true
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
    sha256.put("demos/waterfall.jpg", "")
    urls.put("demos/ocean.jpg", "https://upload.wikimedia.org/wikipedia/commons/thumb/4/40/Ocean_waves_rippling_%28Unsplash%29.jpg/1920px-Ocean_waves_rippling_%28Unsplash%29.jpg")
    sha256.put("demos/ocean.jpg", "")
    urls.put("demos/mountains.jpg", "https://upload.wikimedia.org/wikipedia/commons/thumb/8/82/Cloudy_Skies_Over_Mountains_%28Unsplash%29.jpg/1920px-Cloudy_Skies_Over_Mountains_%28Unsplash%29.jpg")
    sha256.put("demos/mountains.jpg", "")
    urls.put("demos/lake.jpg", "https://upload.wikimedia.org/wikipedia/commons/thumb/d/d1/Clouds_mirrored_in_a_mountain_lake_%28Unsplash%29.jpg/1920px-Clouds_mirrored_in_a_mountain_lake_%28Unsplash%29.jpg")
    sha256.put("demos/lake.jpg", "")
    urls.put("demos/campfire.jpg", "https://upload.wikimedia.org/wikipedia/commons/thumb/9/99/Campfire_Cooking_%28Unsplash%29.jpg/1920px-Campfire_Cooking_%28Unsplash%29.jpg")
    sha256.put("demos/campfire.jpg", "")
    urls.put("demos/falls_portrait.jpg", "https://upload.wikimedia.org/wikipedia/commons/thumb/c/c7/Narrow_waterfall_in_Alaska_%28Unsplash%29.jpg/1920px-Narrow_waterfall_in_Alaska_%28Unsplash%29.jpg")
    sha256.put("demos/falls_portrait.jpg", "")
    outputDir.set(layout.buildDirectory.dir("generated/demos"))
}

androidComponents {
    onVariants { variant ->
        variant.sources.assets?.addGeneratedSourceDirectory(downloadDemos, DownloadAssetsTask::outputDir)
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
