package com.motionloop.app.feature.editor.tools

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import com.motionloop.app.domain.model.Anchor
import com.motionloop.app.domain.model.MotionArrow
import com.motionloop.app.domain.model.NPoint
import com.motionloop.app.feature.editor.EditorTool
import com.motionloop.app.ui.theme.MlColors
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * Transparent layer over the GL preview, the same size as the photo.
 * Draws arrows, anchors and the brush cursor, and turns touches into normalized points.
 */
@Composable
fun EditorCanvas(
    tool: EditorTool,
    arrows: List<MotionArrow>,
    anchors: List<Anchor>,
    showGuides: Boolean,
    brushRadius: Float,
    onDown: (NPoint) -> Unit,
    onMove: (NPoint) -> Unit,
    onUp: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var cursor by remember { mutableStateOf<Offset?>(null) }

    Canvas(
        modifier.pointerInput(tool) {
            fun norm(o: Offset) = NPoint(
                (o.x / size.width).coerceIn(0f, 1f),
                (o.y / size.height).coerceIn(0f, 1f),
            )
            awaitEachGesture {
                val down = awaitFirstDown()
                down.consume()
                cursor = down.position
                onDown(norm(down.position))
                while (true) {
                    val event = awaitPointerEvent()
                    val change = event.changes.firstOrNull { it.id == down.id } ?: break
                    if (!change.pressed) break
                    change.consume()
                    cursor = change.position
                    onMove(norm(change.position))
                }
                cursor = null
                onUp()
            }
        },
    ) {
        if (showGuides) {
            anchors.forEach { drawAnchor(it) }
            arrows.forEach { drawArrow(it) }
        }
        val c = cursor
        if (c != null && (tool == EditorTool.FREEZE || tool == EditorTool.UNFREEZE)) {
            drawCircle(Color.White, radius = brushRadius * size.width, center = c, style = Stroke(width = 2f))
        }
    }
}

private fun DrawScope.toPx(p: NPoint) = Offset(p.x * size.width, p.y * size.height)

private fun DrawScope.drawArrow(a: MotionArrow) {
    val s = toPx(a.start)
    val e = toPx(a.end)
    val stroke = 3.5f * density
    drawLine(Color.Black.copy(alpha = 0.35f), s, e, strokeWidth = stroke + 3f, cap = StrokeCap.Round)
    drawLine(MlColors.Arrow, s, e, strokeWidth = stroke, cap = StrokeCap.Round)
    drawCircle(MlColors.Arrow, radius = stroke, center = s)

    val angle = atan2(e.y - s.y, e.x - s.x)
    val head = min(14f * density, (e - s).getDistance() * 0.6f)
    val path = Path().apply {
        moveTo(e.x, e.y)
        lineTo(e.x - head * cos(angle - 0.45f), e.y - head * sin(angle - 0.45f))
        lineTo(e.x - head * cos(angle + 0.45f), e.y - head * sin(angle + 0.45f))
        close()
    }
    drawPath(path, MlColors.Arrow)
}

private fun DrawScope.drawAnchor(a: Anchor) {
    val c = toPx(a.center)
    val r = a.radius * size.width
    drawCircle(Color.Black.copy(alpha = 0.35f), radius = r, center = c, style = Stroke(width = 5f))
    drawCircle(MlColors.Freeze, radius = r, center = c, style = Stroke(width = 2.5f * density))
    drawCircle(MlColors.Freeze, radius = 3f * density, center = c)
}
