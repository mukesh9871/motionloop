package com.motionloop.app.data

import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.OutputStream

/**
 * Writes [this] file atomically and durably: into a temp file, flushed and synced to disk, then
 * renamed over the target. A crash or power loss leaves either the old file or the new one, never
 * a truncated one (a rename alone can reach disk before the data it points at).
 */
fun File.writeAtomically(write: (OutputStream) -> Unit) {
    val tmp = File(parentFile, "$name.tmp")
    try {
        FileOutputStream(tmp).use { out ->
            write(out)
            out.flush()
            out.fd.sync()
        }
        if (!tmp.renameTo(this)) throw IOException("Could not replace $name")
    } catch (e: Exception) {
        tmp.delete()
        throw e
    }
}
