package com.motionloop.app.data.media

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File

object MediaIntents {

    /** Temp file Uri the camera app writes the photo into. */
    fun newCameraUri(context: Context): Uri {
        val dir = File(context.cacheDir, "camera").apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() }
        val file = File(dir, "capture_${System.currentTimeMillis()}.jpg")
        return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    }

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
