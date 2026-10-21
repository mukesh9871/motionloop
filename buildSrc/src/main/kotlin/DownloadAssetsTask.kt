import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.MapProperty
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction
import java.io.File
import java.net.HttpURLConnection
import java.net.URI
import java.security.MessageDigest

/**
 * Downloads binary assets (ML models, sample photos) into a generated assets folder,
 * so no binaries live in git. Every file is checked against a pinned SHA-256.
 * An empty pin only logs the hash as `ASSET_SHA256 <name> <hash>`, which CI publishes for pinning.
 *
 * Pinned files are kept in a cache under the Gradle user home (persisted by CI), so after the first
 * success a build does not contact the hosts again. Downloads retry on 429 and 5xx with backoff.
 */
abstract class DownloadAssetsTask : DefaultTask() {
    /** File name in the output folder (may contain a sub folder) to source URL. */
    @get:Input
    abstract val urls: MapProperty<String, String>

    @get:Input
    abstract val sha256: MapProperty<String, String>

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    /** Content-addressed cache, keyed by SHA-256. */
    @get:Internal
    val cacheDir: File = File(project.gradle.gradleUserHomeDir, "caches/motionloop-assets")

    @TaskAction
    fun download() {
        val dir = outputDir.get().asFile.apply { mkdirs() }
        val pins = sha256.get()
        for ((name, url) in urls.get()) {
            val target = dir.resolve(name)
            target.parentFile.mkdirs()
            val pin = pins[name].orEmpty()
            if (target.exists() && pin.isNotEmpty() && sha(target.readBytes()) == pin) continue

            val cached = if (pin.isNotEmpty()) File(cacheDir, pin) else null
            if (cached != null && cached.exists() && sha(cached.readBytes()) == pin) {
                cached.copyTo(target, overwrite = true)
                continue
            }

            logger.lifecycle("Downloading $name")
            val bytes = fetchWithRetry(url)
            val actual = sha(bytes)
            logger.lifecycle("ASSET_SHA256 $name $actual")
            if (pin.isNotEmpty() && pin != actual) {
                throw GradleException("Checksum mismatch for $name: expected $pin, got $actual")
            }
            target.writeBytes(bytes)
            if (cached != null) {
                cacheDir.mkdirs()
                target.copyTo(cached, overwrite = true)
            }
        }
    }

    private fun fetchWithRetry(url: String): ByteArray {
        var delayMs = 2_000L
        repeat(MAX_ATTEMPTS) { attempt ->
            val connection = URI(url).toURL().openConnection() as HttpURLConnection
            // Wikimedia asks every client to identify itself with a way to reach the owner.
            connection.setRequestProperty("User-Agent", USER_AGENT)
            connection.instanceFollowRedirects = true
            connection.connectTimeout = 30_000
            connection.readTimeout = 120_000
            try {
                val code = connection.responseCode
                if (code in 200..299) return connection.inputStream.use { it.readBytes() }
                val retryable = code == 429 || code >= 500
                if (!retryable || attempt == MAX_ATTEMPTS - 1) throw GradleException("Download failed ($code): $url")
                // Honour Retry-After (seconds) when the server sends it.
                val wait = connection.getHeaderField("Retry-After")?.toLongOrNull()?.times(1_000) ?: delayMs
                logger.lifecycle("Got $code for $url, retrying in ${wait / 1000} s")
                Thread.sleep(wait.coerceAtMost(60_000))
                delayMs *= 2
            } finally {
                connection.disconnect()
            }
        }
        throw GradleException("Download failed: $url")
    }

    private fun sha(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    private companion object {
        const val MAX_ATTEMPTS = 5
        const val USER_AGENT = "MotionLoopBuild/1.0 (https://github.com/mukesh9871/motionloop; build-time asset download)"
    }
}
