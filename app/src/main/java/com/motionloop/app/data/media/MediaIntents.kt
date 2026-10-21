package com.motionloop.app.data.media

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File

object MediaIntents {

    /** Temp file Uri the camera app writes the photo into. Old captures are cleared by [pruneCaptures]. */
    fun newCameraUri(context: Context): Uri {
        // Normally made by pruneCaptures when Home opens. One stat here covers a cache cleared meanwhile.
        val dir = File(context.cacheDir, "camera")
        if (!dir.isDirectory) dir.mkdirs()
        val file = File(dir, "capture_${System.currentTimeMillis()}.jpg")
        return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    }

    /**
     * Deletes stale captures. Disk work: call off the main thread. Recent files stay: the camera
     * app may have killed this process while writing one that Home is about to import.
     */
    fun pruneCaptures(context: Context) {
        val dir = File(context.cacheDir, "camera")
        val cutoff = System.currentTimeMillis() - CAPTURE_MAX_AGE_MS
        dir.listFiles()?.forEach { if (it.lastModified() < cutoff) it.delete() }
        dir.mkdirs()
    }

    private const val CAPTURE_MAX_AGE_MS = 60L * 60 * 1000

    fun shareText(context: Context, text: String) {
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
        }
        context.startActivity(Intent.createChooser(send, null))
    }

    /** Opens the mail app with the message filled in. Returns false when there is none. */
    fun email(context: Context, address: String, subject: String, body: String = ""): Boolean = try {
        // Both the mailto query and the extras: some mail apps read only one of them.
        val uri = Uri.parse("mailto:$address?subject=${Uri.encode(subject)}&body=${Uri.encode(body)}")
        context.startActivity(
            Intent(Intent.ACTION_SENDTO, uri).apply {
                putExtra(Intent.EXTRA_EMAIL, arrayOf(address))
                putExtra(Intent.EXTRA_SUBJECT, subject)
                putExtra(Intent.EXTRA_TEXT, body)
            },
        )
        true
    } catch (e: android.content.ActivityNotFoundException) {
        false
    }

    fun share(context: Context, uri: Uri, mimeType: String) {
        val send = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(send, null))
    }
}
