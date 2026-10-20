package com.motionloop.app.domain.model

import android.net.Uri

enum class ExportFormat { MP4, GIF }

/** Target short edge in pixels. The long edge follows the photo aspect ratio. */
enum class ExportResolution(val shortEdge: Int, val label: String) {
    HD(720, "720p"),
    FULL_HD(1080, "1080p"),
    UHD(2160, "4K"),
}

data class ExportSettings(
    val format: ExportFormat = ExportFormat.MP4,
    val lengthSeconds: Int = 5,
    val resolution: ExportResolution = ExportResolution.FULL_HD,
) {
    companion object {
        const val VIDEO_FPS = 24
        val VIDEO_LENGTHS = listOf(3, 5, 10, 15)
        val GIF_LENGTHS = listOf(3, 5)
        const val GIF_LONG_EDGE = 480

        /** GIF delays are in 1/100 s. 8 cs is 12.5 fps. */
        const val GIF_DELAY_CS = 8
    }
}

sealed interface ExportProgress {
    data class Running(val fraction: Float) : ExportProgress
    data class Done(val uri: Uri, val mimeType: String) : ExportProgress
}
