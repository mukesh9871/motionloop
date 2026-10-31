package com.motionloop.app.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.ui.Alignment
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.dp
import com.motionloop.app.ui.theme.MlColors
import kotlin.math.max

/**
 * Rounded option chip used by every panel. Active: violet with white text. Read by TalkBack as a
 * selectable button with its state, and padded out to a 48 dp touch target. It dips a little
 * while pressed and gives a light tick when it becomes the choice.
 */
@Composable
fun Pill(label: String, active: Boolean, enabled: Boolean = true, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val source = remember { MutableInteractionSource() }
    val haptics = LocalHapticFeedback.current
    val fill by animateColorAsState(if (active) MlColors.Violet else MlColors.SurfaceHigh, MlMotion.colorSpring(), label = "pillFill")
    Box(
        modifier
            .minimumInteractiveComponentSize()
            .pressBounce(source)
            .clip(RoundedCornerShape(50))
            .background(fill)
            .selectable(
                selected = active,
                interactionSource = source,
                indication = LocalIndication.current,
                enabled = enabled,
                role = Role.Button,
            ) {
                if (!active) haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                onClick()
            }
            .padding(horizontal = 14.dp, vertical = 7.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = when {
                !enabled -> MlColors.TextSecondary.copy(alpha = 0.5f)
                active -> Color.White
                else -> MlColors.TextPrimary
            },
        )
    }
}

/** Springs shared by the controls, so every press and highlight moves with the same feel. */
object MlMotion {
    /** Presses and pops: quick, with a small overshoot. */
    fun <T> bouncy() = spring<T>(dampingRatio = 0.55f, stiffness = Spring.StiffnessMedium)

    /** Highlights and fills: settles without overshoot (colors must not overshoot). */
    fun <T> colorSpring() = spring<T>(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow)

    /** Panels and sheets: soft, barely bouncy. */
    fun <T> gentle() = spring<T>(dampingRatio = 0.85f, stiffness = Spring.StiffnessMediumLow)
}

/**
 * A small springy dip while [source] is pressed, so taps feel physical. The scale is read in the
 * layer block: pressing redraws the layer and never recomposes the content.
 */
@Composable
fun Modifier.pressBounce(source: InteractionSource, pressedScale: Float = 0.94f): Modifier {
    val pressed by source.collectIsPressedAsState()
    val scale = animateFloatAsState(if (pressed) pressedScale else 1f, MlMotion.bouncy(), label = "press")
    return graphicsLayer {
        scaleX = scale.value
        scaleY = scale.value
    }
}

/**
 * Vertical scroll with an always-visible bar on the right whenever there is more content,
 * so people can tell that more options sit above or below.
 */
@Composable
fun Modifier.verticalScrollWithBar(state: ScrollState): Modifier {
    val thumb = MlColors.TextSecondary.copy(alpha = 0.7f)
    val track = MlColors.TextSecondary.copy(alpha = 0.15f)
    // Drawn before the scroll modifier: this layer has the fixed viewport size.
    return drawWithContent {
        drawContent()
        drawBar(state, vertical = true, thumb, track)
    }
        .verticalScroll(state)
        .padding(end = 12.dp)
}

/** Horizontal scroll with a thin bar along the bottom when the row overflows. */
@Composable
fun Modifier.horizontalScrollWithBar(state: ScrollState): Modifier {
    val thumb = MlColors.TextSecondary.copy(alpha = 0.7f)
    val track = MlColors.TextSecondary.copy(alpha = 0.15f)
    return drawWithContent {
        drawContent()
        drawBar(state, vertical = false, thumb, track)
    }
        .horizontalScroll(state)
        .padding(bottom = 6.dp)
}

/**
 * Scroll bar for a lazy list. Item heights vary, so the thumb is an estimate from the visible
 * share of items, which is enough to show where you are and that there is more.
 */
@Composable
fun Modifier.lazyScrollBar(state: LazyListState): Modifier {
    val thumb = MlColors.TextSecondary.copy(alpha = 0.7f)
    val track = MlColors.TextSecondary.copy(alpha = 0.15f)
    return drawWithContent {
        drawContent()
        val info = state.layoutInfo
        val total = info.totalItemsCount
        val visible = info.visibleItemsInfo
        if (total == 0 || visible.isEmpty() || (visible.size >= total && !state.canScrollForward && !state.canScrollBackward)) return@drawWithContent
        val first = visible.first()
        val firstFraction = if (first.size > 0) (-first.offset).toFloat() / first.size else 0f
        val position = (first.index + firstFraction.coerceIn(0f, 1f)) / total
        val viewport = size.height
        val thickness = 3.dp.toPx()
        // Never longer than the viewport, so the clamp range below stays valid.
        val length = minOf(maxOf(viewport * visible.size / total, 28.dp.toPx()), viewport)
        val start = (position * viewport).coerceIn(0f, viewport - length)
        val x = size.width - thickness
        drawRoundRect(track, Offset(x, 0f), Size(thickness, viewport), CornerRadius(thickness / 2))
        drawRoundRect(thumb, Offset(x, start), Size(thickness, length), CornerRadius(thickness / 2))
    }
}

/** Horizontal lazy row variant of [lazyScrollBar]: a thin bar along the bottom. */
@Composable
fun Modifier.lazyScrollBarHorizontal(state: LazyListState): Modifier {
    val thumb = MlColors.TextSecondary.copy(alpha = 0.7f)
    val track = MlColors.TextSecondary.copy(alpha = 0.15f)
    return drawWithContent {
        drawContent()
        val info = state.layoutInfo
        val total = info.totalItemsCount
        val visible = info.visibleItemsInfo
        if (total == 0 || visible.isEmpty() || (!state.canScrollForward && !state.canScrollBackward)) return@drawWithContent
        val first = visible.first()
        val firstFraction = if (first.size > 0) (-first.offset).toFloat() / first.size else 0f
        val position = (first.index + firstFraction.coerceIn(0f, 1f)) / total
        val viewport = size.width
        val thickness = 3.dp.toPx()
        val length = minOf(maxOf(viewport * visible.size / total, 28.dp.toPx()), viewport)
        val start = (position * viewport).coerceIn(0f, viewport - length)
        val y = size.height - thickness
        drawRoundRect(track, Offset(0f, y), Size(viewport, thickness), CornerRadius(thickness / 2))
        drawRoundRect(thumb, Offset(start, y), Size(length, thickness), CornerRadius(thickness / 2))
    }.padding(bottom = 6.dp)
}

private fun DrawScope.drawBar(state: ScrollState, vertical: Boolean, thumb: Color, track: Color) {
    val max = state.maxValue
    // Int.MAX_VALUE until the first measure.
    if (max <= 0 || max == Int.MAX_VALUE) return
    val viewport = if (vertical) size.height else size.width
    val thickness = 3.dp.toPx()
    val length = max(viewport * viewport / (viewport + max), 28.dp.toPx())
    val start = (viewport - length) * state.value / max
    val radius = CornerRadius(thickness / 2)
    if (vertical) {
        val x = size.width - thickness
        drawRoundRect(track, Offset(x, 0f), Size(thickness, viewport), radius)
        drawRoundRect(thumb, Offset(x, start), Size(thickness, length), radius)
    } else {
        val y = size.height - thickness
        drawRoundRect(track, Offset(0f, y), Size(viewport, thickness), radius)
        drawRoundRect(thumb, Offset(start, y), Size(length, thickness), radius)
    }
}
