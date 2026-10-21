import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "com.motionloop.ml"
    compileSdk = 37

    defaultConfig {
        minSdk = 26
        consumerProguardFiles("consumer-rules.pro")
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    // Models are memory-mapped from the APK, which needs them stored uncompressed.
    androidResources {
        noCompress += "tflite"
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

// DownloadAssetsTask lives in buildSrc, shared with the demo photo download. Empty pins only log
// the hash (ASSET_SHA256 lines, published by CI to ci-status/models.txt) until they are filled in.
val downloadModels = tasks.register<DownloadAssetsTask>("downloadModels") {
    urls.put(
        "magic_touch.tflite",
        "https://storage.googleapis.com/mediapipe-models/interactive_segmenter/magic_touch/float32/1/magic_touch.tflite",
    )
    urls.put("midas_small.tflite", "https://github.com/isl-org/MiDaS/releases/download/v2_1/model_opt.tflite")
    sha256.put("magic_touch.tflite", "")
    sha256.put("midas_small.tflite", "")
    outputDir.set(layout.buildDirectory.dir("generated/models"))
}

androidComponents {
    onVariants { variant ->
        variant.sources.assets?.addGeneratedSourceDirectory(downloadModels, DownloadAssetsTask::outputDir)
    }
}

dependencies {
    implementation(libs.mediapipe.tasks.vision)
    implementation(libs.litert)
    implementation(libs.mlkit.subject.segmentation)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.coroutines.play.services)

    testImplementation(libs.junit)
}
