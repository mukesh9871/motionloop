package com.motionloop.app.data

import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.OutputStream
import java.nio.file.Files

/**
 * Writes [this] file atomically and durably: into a temp file, flushed and synced to disk, then
 * renamed over the target. A crash or power loss leaves either the old file or the new one, never
 * a truncated one (a rename alone can reach disk before the data it points at).
 * With [backup], the current file keeps a second name there (a hard link, no copy) before it is
 * replaced, so the target itself never goes missing.
 */
fun File.writeAtomically(backup: File? = null, write: (OutputStream) -> Unit) {
    val tmp = File(parentFile, "$name.tmp")
    try {
        FileOutputStream(tmp).use { out ->
            write(out)
            out.flush()
            out.fd.sync()
        }
        if (backup != null && exists()) {
            backup.delete()
            try {
                Files.createLink(backup.toPath(), toPath())
            } catch (e: Exception) {
                // A file system without hard links: fall back to a copy.
                copyTo(backup, overwrite = true)
            }
        }
        if (!tmp.renameTo(this)) throw IOException("Could not replace $name")
    } catch (e: Exception) {
        tmp.delete()
        throw e
    }
}
