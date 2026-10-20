package com.motionloop.app.ui.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * MotionLoop's own line icons on a 24 x 24 grid.
 * Drawn here so the app ships no third-party icon set.
 */
object MlIcons {

    private fun icon(name: String, fill: Boolean = false, block: PathBuilder.() -> Unit): ImageVector =
        ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f).apply {
            path(
                fill = if (fill) SolidColor(Color.Black) else null,
                stroke = if (fill) null else SolidColor(Color.Black),
                strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
                pathBuilder = block,
            )
        }.build()

    private fun PathBuilder.circle(cx: Float, cy: Float, r: Float) {
        moveTo(cx - r, cy)
        arcTo(r, r, 0f, isMoreThanHalf = true, isPositiveArc = true, x1 = cx + r, y1 = cy)
        arcTo(r, r, 0f, isMoreThanHalf = true, isPositiveArc = true, x1 = cx - r, y1 = cy)
        close()
    }

    val Motion by lazy {
        icon("Motion") {
            moveTo(4f, 18f); curveTo(8f, 10f, 12f, 16f, 18f, 7f)
            moveTo(13f, 6f); lineTo(18f, 7f); lineTo(19f, 12f)
        }
    }

    val Anchor by lazy {
        icon("Anchor") {
            circle(12f, 12f, 7f)
            circle(12f, 12f, 2f)
            moveTo(12f, 2f); lineTo(12f, 5f)
            moveTo(12f, 19f); lineTo(12f, 22f)
            moveTo(2f, 12f); lineTo(5f, 12f)
            moveTo(19f, 12f); lineTo(22f, 12f)
        }
    }

    val Freeze by lazy {
        icon("Freeze") {
            moveTo(12f, 3f); lineTo(12f, 21f)
            moveTo(4.2f, 7.5f); lineTo(19.8f, 16.5f)
            moveTo(4.2f, 16.5f); lineTo(19.8f, 7.5f)
            moveTo(9.5f, 4.5f); lineTo(12f, 7f); lineTo(14.5f, 4.5f)
            moveTo(9.5f, 19.5f); lineTo(12f, 17f); lineTo(14.5f, 19.5f)
        }
    }

    val Unfreeze by lazy {
        icon("Unfreeze") {
            moveTo(15f, 4f); lineTo(20f, 9f); lineTo(10f, 19f); lineTo(5f, 19f); lineTo(5f, 14f); close()
            moveTo(12f, 7f); lineTo(17f, 12f)
            moveTo(13f, 21f); lineTo(21f, 21f)
        }
    }

    val Delete by lazy {
        icon("Delete") {
            moveTo(4f, 7f); lineTo(20f, 7f)
            moveTo(9f, 7f); lineTo(9f, 4f); lineTo(15f, 4f); lineTo(15f, 7f)
            moveTo(6f, 7f); lineTo(7f, 20f); lineTo(17f, 20f); lineTo(18f, 7f)
            moveTo(10f, 11f); lineTo(10f, 16f)
            moveTo(14f, 11f); lineTo(14f, 16f)
        }
    }

    val Play by lazy {
        icon("Play", fill = true) {
            moveTo(7f, 4.5f); lineTo(19.5f, 12f); lineTo(7f, 19.5f); close()
        }
    }

    val Pause by lazy {
        icon("Pause", fill = true) {
            moveTo(6f, 4.5f); lineTo(10f, 4.5f); lineTo(10f, 19.5f); lineTo(6f, 19.5f); close()
            moveTo(14f, 4.5f); lineTo(18f, 4.5f); lineTo(18f, 19.5f); lineTo(14f, 19.5f); close()
        }
    }

    val Undo by lazy {
        icon("Undo") {
            moveTo(8f, 5f); lineTo(4f, 9f); lineTo(8f, 13f)
            moveTo(4f, 9f); lineTo(14f, 9f)
            curveTo(18f, 9f, 20f, 12f, 20f, 14.5f)
            curveTo(20f, 17f, 18f, 19f, 14f, 19f)
            lineTo(9f, 19f)
        }
    }

    val Redo by lazy {
        icon("Redo") {
            moveTo(16f, 5f); lineTo(20f, 9f); lineTo(16f, 13f)
            moveTo(20f, 9f); lineTo(10f, 9f)
            curveTo(6f, 9f, 4f, 12f, 4f, 14.5f)
            curveTo(4f, 17f, 6f, 19f, 10f, 19f)
            lineTo(15f, 19f)
        }
    }

    val Export by lazy {
        icon("Export") {
            moveTo(12f, 15f); lineTo(12f, 3f)
            moveTo(7.5f, 7.5f); lineTo(12f, 3f); lineTo(16.5f, 7.5f)
            moveTo(5f, 12f); lineTo(5f, 20f); lineTo(19f, 20f); lineTo(19f, 12f)
        }
    }

    val Back by lazy {
        icon("Back") {
            moveTo(15f, 5f); lineTo(8f, 12f); lineTo(15f, 19f)
        }
    }

    val Close by lazy {
        icon("Close") {
            moveTo(6f, 6f); lineTo(18f, 18f)
            moveTo(18f, 6f); lineTo(6f, 18f)
        }
    }

    val Camera by lazy {
        icon("Camera") {
            moveTo(3f, 8f); lineTo(7f, 8f); lineTo(9f, 5f); lineTo(15f, 5f); lineTo(17f, 8f); lineTo(21f, 8f)
            lineTo(21f, 19f); lineTo(3f, 19f); close()
            circle(12f, 13f, 3.5f)
        }
    }

    val Gallery by lazy {
        icon("Gallery") {
            moveTo(3f, 5f); lineTo(21f, 5f); lineTo(21f, 19f); lineTo(3f, 19f); close()
            moveTo(3f, 16f); lineTo(9f, 11f); lineTo(14f, 15f); lineTo(16.5f, 13f); lineTo(21f, 16.5f)
            circle(16f, 9f, 1.5f)
        }
    }

    val Eye by lazy {
        icon("Eye") {
            moveTo(2f, 12f)
            curveTo(5f, 6f, 19f, 6f, 22f, 12f)
            curveTo(19f, 18f, 5f, 18f, 2f, 12f)
            close()
            circle(12f, 12f, 3f)
        }
    }

    val Loop by lazy {
        icon("Loop") {
            moveTo(4f, 12f); curveTo(4f, 7f, 9f, 5f, 12f, 8f); curveTo(15f, 11f, 15f, 13f, 12f, 16f)
            curveTo(9f, 19f, 4f, 17f, 4f, 12f)
            moveTo(12f, 8f); curveTo(15f, 5f, 20f, 7f, 20f, 12f); curveTo(20f, 17f, 15f, 19f, 12f, 16f)
        }
    }

    val Boomerang by lazy {
        icon("Boomerang") {
            moveTo(4f, 9f); lineTo(20f, 9f)
            moveTo(16f, 5f); lineTo(20f, 9f); lineTo(16f, 13f)
            moveTo(20f, 15f); lineTo(4f, 15f)
            moveTo(8f, 11f); lineTo(4f, 15f); lineTo(8f, 19f)
        }
    }
}
