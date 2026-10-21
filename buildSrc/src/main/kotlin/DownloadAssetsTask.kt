import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.MapProperty
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction
import java.net.HttpURLConnection
import java.net.URI
import java.security.MessageDigest

/**
 * Downloads binary assets (ML models, demo photos) into a generated assets folder,
 * so no binaries live in git. Every file is checked against a pinned SHA-256.
 * An empty pin only logs the hash as `ASSET_SHA256 <name> <hash>`, which CI publishes for pinning.
 */
abstract class DownloadAssetsTask : DefaultTask() {
    /** File name in the output folder (may contain a sub folder) to source URL. */
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
            target.parentFile.mkdirs()
            val pin = pins[name].orEmpty()
            if (target.exists() && pin.isNotEmpty() && sha(target.readBytes()) == pin) continue
            logger.lifecycle("Downloading $name")
            val bytes = fetch(url)
            val actual = sha(bytes)
            logger.lifecycle("ASSET_SHA256 $name $actual")
            if (pin.isNotEmpty() && pin != actual) {
                throw GradleException("Checksum mismatch for $name: expected $pin, got $actual")
            }
            target.writeBytes(bytes)
        }
    }

    private fun fetch(url: String): ByteArray {
        val connection = URI(url).toURL().openConnection() as HttpURLConnection
        // Wikimedia asks every client to identify itself.
        connection.setRequestProperty("User-Agent", "MotionLoopBuild/1.0 (Android app build; asset download)")
        connection.instanceFollowRedirects = true
        connection.connectTimeout = 30_000
        connection.readTimeout = 120_000
        try {
            val code = connection.responseCode
            if (code !in 200..299) throw GradleException("Download failed ($code): $url")
            return connection.inputStream.use { it.readBytes() }
        } finally {
            connection.disconnect()
        }
    }

    private fun sha(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
}
