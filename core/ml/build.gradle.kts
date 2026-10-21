import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.MapProperty
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.net.URI
import java.security.MessageDigest

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

/**
 * Downloads the on-device models into a generated assets folder, so no binaries live in git.
 * Every file is checked against a pinned SHA-256. An empty pin only logs the hash (MODEL_SHA256 lines),
 * which CI publishes so the pin can be filled in.
 */
abstract class DownloadModelsTask : DefaultTask() {
    @get:Input
    abstract val urls: MapProperty<String, String>

    @get:Input
    abstract val sha256: MapProperty<String, String>

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    @TaskAction
    fun download() {
        val dir = outputDir.get().asFile.apply { mkdirs() }
        val pins = sha256.get()
        for ((name, url) in urls.get()) {
            val target = dir.resolve(name)
            val pin = pins[name].orEmpty()
            if (target.exists() && pin.isNotEmpty() && sha(target.readBytes()) == pin) continue
            logger.lifecycle("Downloading model $name")
            val bytes = URI(url).toURL().openStream().use { it.readBytes() }
            val actual = sha(bytes)
            logger.lifecycle("MODEL_SHA256 $name $actual")
            if (pin.isNotEmpty() && pin != actual) {
                throw GradleException("Checksum mismatch for $name: expected $pin, got $actual")
            }
            target.writeBytes(bytes)
        }
    }

    private fun sha(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
}

val downloadModels = tasks.register<DownloadModelsTask>("downloadModels") {
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
        variant.sources.assets?.addGeneratedSourceDirectory(downloadModels, DownloadModelsTask::outputDir)
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
